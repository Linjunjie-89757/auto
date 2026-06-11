package com.company.autoplatform.apiautomation;

import com.company.autoplatform.IntegrationTestSupport;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static com.company.autoplatform.apiautomation.ApiAutomationModels.*;
import static org.assertj.core.api.Assertions.assertThat;

class ApiExecutionEntryIntegrationTests extends IntegrationTestSupport {

    @Autowired
    private ApiAutomationService apiAutomationService;

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok", exchange -> writeResponse(exchange, 200, "application/json", """
                {"ok":true,"source":"entry-smoke"}
                """));
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void debugRunDefinitionDraftReturnsSuccessfulRunWithStepResult() {
        ApiRunResponse run = apiAutomationService.debugRunDefinitionDraft(WORKSPACE_CODE, new ApiDebugDefinitionRequest(
                WORKSPACE_CODE,
                null,
                "draft definition entry smoke " + System.nanoTime(),
                requestConfig(),
                List.of(statusCodeAssertion()),
                List.of(),
                List.of(),
                List.of(),
                null,
                null
        ));

        assertSuccessfulRun(run);
    }

    @Test
    void debugRunSavedDefinitionReturnsSuccessfulRunAndUpdatesLastRunResult() {
        ApiDefinitionDetail definition = apiAutomationService.createDefinition(WORKSPACE_CODE, new SaveApiDefinitionRequest(
                WORKSPACE_CODE,
                "saved definition entry smoke " + System.nanoTime(),
                null,
                "entry smoke",
                List.of("entry-smoke"),
                requestConfig(),
                List.of(statusCodeAssertion()),
                List.of(),
                List.of(),
                List.of()
        ));

        ApiRunResponse run = apiAutomationService.debugRunDefinition(
                definition.id(),
                WORKSPACE_CODE,
                new ApiRunRequest(WORKSPACE_CODE, null, null)
        );

        assertSuccessfulRun(run);
        ApiDefinitionDetail refreshed = apiAutomationService.getDefinition(definition.id(), WORKSPACE_CODE);
        assertThat(refreshed.lastRunResult()).isEqualTo("SUCCESS");
        assertThat(refreshed.lastRunAt()).isNotNull();
    }

    private void assertSuccessfulRun(ApiRunResponse run) {
        assertThat(run.taskId()).isNotNull();
        assertThat(run.reportId()).isNotNull();
        assertThat(run.result()).isEqualTo("SUCCESS");
        assertThat(run.stepResults()).hasSize(1);
        ApiRunStepResultResponse step = run.stepResults().getFirst();
        assertThat(step.success()).isTrue();
        assertThat(step.response()).isNotNull();
        assertThat(step.response().statusCode()).isEqualTo(200);
        assertThat(step.assertionResults()).allMatch(ApiAssertionResult::success);
    }

    private ApiRequestConfigInput requestConfig() {
        return new ApiRequestConfigInput(
                "GET",
                baseUrl + "/ok",
                5000,
                List.of(),
                List.of(),
                List.of(),
                new ApiRequestBodyInput("NONE", null, List.of(), null, null, null),
                new ApiAuthConfigInput("NONE", null, null)
        );
    }

    private ApiAssertionInput statusCodeAssertion() {
        return new ApiAssertionInput("STATUS_CODE", null, "EQUALS", "200");
    }

    private void writeResponse(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().put("Content-Type", List.of(contentType));
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
