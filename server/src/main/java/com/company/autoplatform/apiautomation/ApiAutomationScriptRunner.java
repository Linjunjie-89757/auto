package com.company.autoplatform.apiautomation;

import com.company.autoplatform.common.BadRequestException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class ApiAutomationScriptRunner {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Duration SCRIPT_TIMEOUT = Duration.ofSeconds(10);
    private static final String NODE_BRIDGE = """
            const fs = require('fs');
            const input = JSON.parse(fs.readFileSync(0, 'utf8'));
            const logs = [];
            const vars = input.vars && typeof input.vars === 'object' ? input.vars : {};
            const request = input.request && typeof input.request === 'object' ? input.request : {};
            const response = input.response && typeof input.response === 'object' ? input.response : {};
            let failedMessage = null;
            const helpers = {
              setVar(key, value) {
                vars[String(key)] = value == null ? '' : String(value);
              },
              getVar(key) {
                return vars[String(key)];
              },
              removeVar(key) {
                delete vars[String(key)];
              },
              log(...args) {
                logs.push(args.map((item) => {
                  if (typeof item === 'string') {
                    return item;
                  }
                  try {
                    return JSON.stringify(item);
                  } catch (error) {
                    return String(item);
                  }
                }).join(' '));
              },
              fail(message) {
                failedMessage = message == null ? 'Script failed' : String(message);
                throw new Error('__API_PROCESSOR_FAIL__');
              },
            };

            const AsyncFunction = Object.getPrototypeOf(async function () {}).constructor;

            (async () => {
              try {
                const script = String(input.script || '');
                const body = [
                  'var setVar = helpers.setVar;',
                  'var getVar = helpers.getVar;',
                  'var removeVar = helpers.removeVar;',
                  'var log = helpers.log;',
                  'var fail = helpers.fail;',
                  'var require = undefined;',
                  'var process = undefined;',
                  'var global = undefined;',
                  'var module = undefined;',
                  'var exports = undefined;',
                  script,
                ].join('\\n');
                const runner = new AsyncFunction(
                  'helpers',
                  'vars',
                  'request',
                  'response',
                  body
                );
                await runner(helpers, vars, request, response);
                process.stdout.write(JSON.stringify({
                  success: true,
                  message: logs.length ? logs[logs.length - 1] : 'Script executed',
                  logs,
                  vars,
                  request,
                }));
              } catch (error) {
                process.stdout.write(JSON.stringify({
                  success: false,
                  message: failedMessage || (error && error.message ? error.message : 'Script failed'),
                  logs,
                  vars,
                  request,
                }));
              }
            })();
            """;

    public ScriptExecutionResult execute(
            String script,
            Map<String, String> variables,
            Map<String, Object> request,
            Map<String, Object> response
    ) {
        ScriptInput payload = new ScriptInput(script, variables, request, response);
        Process process;
        try {
            process = new ProcessBuilder("node", "-e", NODE_BRIDGE).start();
        } catch (IOException exception) {
            throw new BadRequestException("Node.js is required to run JavaScript processors");
        }

        try (OutputStream outputStream = process.getOutputStream()) {
            outputStream.write(OBJECT_MAPPER.writeValueAsBytes(payload));
        } catch (IOException exception) {
            process.destroyForcibly();
            throw new BadRequestException("Failed to start JavaScript processor");
        }

        boolean completed;
        try {
            completed = process.waitFor(SCRIPT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new BadRequestException("JavaScript processor was interrupted");
        }

        if (!completed) {
            process.destroyForcibly();
            throw new BadRequestException("JavaScript processor timed out after " + SCRIPT_TIMEOUT.toSeconds() + " seconds");
        }

        String stdout = readFully(process.getInputStream());
        String stderr = readFully(process.getErrorStream());
        if (process.exitValue() != 0 && stdout.isBlank()) {
            throw new BadRequestException(stderr.isBlank() ? "JavaScript processor failed" : stderr);
        }

        try {
            ScriptOutput output = OBJECT_MAPPER.readValue(stdout, ScriptOutput.class);
            return new ScriptExecutionResult(
                    output.success,
                    output.message == null || output.message.isBlank() ? (output.success ? "Script executed" : "Script failed") : output.message,
                    output.logs == null ? List.of() : output.logs,
                    output.vars == null ? Map.of() : output.vars,
                    output.request == null ? Map.of() : output.request
            );
        } catch (JsonProcessingException exception) {
            throw new BadRequestException(stderr.isBlank() ? "JavaScript processor returned an invalid result" : stderr);
        }
    }

    private String readFully(InputStream stream) {
        try {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return "";
        }
    }

    private record ScriptInput(
            String script,
            Map<String, String> vars,
            Map<String, Object> request,
            Map<String, Object> response
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class ScriptOutput {
        public boolean success;
        public String message;
        public List<String> logs;
        public Map<String, String> vars;
        public Map<String, Object> request;
    }

    public record ScriptExecutionResult(
            boolean success,
            String message,
            List<String> logs,
            Map<String, String> variables,
            Map<String, Object> request
    ) {
    }
}
