package com.company.autoplatform.apiautomation;

import com.company.autoplatform.IntegrationTestSupport;
import com.company.autoplatform.settings.DbConnectionItem;
import com.company.autoplatform.settings.DbConnectionRequest;
import com.company.autoplatform.settings.SettingsService;
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

class ApiProcessorCoreTests extends IntegrationTestSupport {

    @Autowired
    private ApiAutomationService apiAutomationService;

    @Autowired
    private SettingsService settingsService;

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/json", exchange -> writeResponse(exchange, 200, "application/json", Map.of(
                "X-Legacy", "legacy-header",
                "X-Trace", "trace-123"
        ), """
                {"user":{"name":"Alice"},"items":[{"id":10},{"id":20}],"token":"alpha","legacy":"json-ok"}
                """));
        server.createContext("/xml", exchange -> writeResponse(exchange, 200, "application/xml", Map.of(), """
                <root><user><name>Alice XML</name></user></root>
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
    void sqlProcessorWritesVariablesAndResultVariableBeforeJsonAndRegexExtractorsRun() {
        DbConnectionItem dbConnection = createH2Connection("processor-sql-" + System.nanoTime());

        ApiRunResponse run = apiAutomationService.debugRunDefinitionDraft(WORKSPACE_CODE, new ApiDebugDefinitionRequest(
                WORKSPACE_CODE,
                null,
                "processor sql json regex",
                request("GET", baseUrl + "/json", List.of(new ApiKeyValueInput("X-SQL-Token", "{{token_1}}", null, true, null, null, null, null, null))),
                List.of(new ApiAssertionInput("STATUS_CODE", null, "EQUALS", "200")),
                List.of(),
                List.of(new ApiProcessorInput(
                        "pre-sql",
                        "SQL",
                        "SQL preprocessor",
                        true,
                        null,
                        null,
                        "SELECT 'alpha' AS token, 7 AS id UNION ALL SELECT 'beta' AS token, 8 AS id",
                        null,
                        dbConnection.id(),
                        null,
                        5000,
                        "token,id",
                        List.of(new ApiKeyValueInput("firstToken", "token", null, true, null, null, null, null, null)),
                        "sqlRows",
                        List.of()
                )),
                List.of(new ApiProcessorInput(
                        "post-extract",
                        "EXTRACT",
                        "JSON and regex extractors",
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(
                                extractor("jsonName", "JSON_PATH", "BODY", "$.user.name", "SPECIFIC", 1, null),
                                extractor("allItemIds", "JSON_PATH", "BODY", "$.items[*].id", "ALL", 1, null),
                                extractor("regexToken", "REGEX", "BODY", "\\\"token\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"", "SPECIFIC", 1, "GROUP")
                        )
                )),
                null,
                null
        ));

        assertThat(run.result()).isEqualTo("SUCCESS");
        ApiRunStepResultResponse step = run.stepResults().getFirst();
        assertThat(step.success()).isTrue();
        assertThat(step.assertionResults()).allMatch(ApiAssertionResult::success);

        ApiProcessorResult sqlResult = processorResult(step, "PRE", "SQL");
        assertThat(sqlResult.success()).isTrue();
        assertThat(sqlResult.outputVariables()).containsEntry("token_1", "alpha");
        assertThat(sqlResult.outputVariables()).containsEntry("id_2", "8");
        assertThat(sqlResult.outputVariables()).containsEntry("firstToken", "alpha");
        assertThat(sqlResult.outputVariables().get("sqlRows"))
                .contains("\"TOKEN\":\"alpha\"")
                .contains("\"ID\":8");

        ApiProcessorResult extractResult = processorResult(step, "POST", "EXTRACT");
        assertThat(extractResult.success()).isTrue();
        assertThat(extractResult.outputVariables()).containsEntry("jsonName", "Alice");
        assertThat(extractResult.outputVariables()).containsEntry("regexToken", "alpha");
        assertThat(extractResult.outputVariables().get("allItemIds")).isEqualTo("[\"10\",\"20\"]");
        assertExtraction(step, "jsonName", "Alice");
        assertExtraction(step, "regexToken", "alpha");
    }

    @Test
    void xpathExtractorReadsXmlResponseBody() {
        ApiRunResponse run = apiAutomationService.debugRunDefinitionDraft(WORKSPACE_CODE, new ApiDebugDefinitionRequest(
                WORKSPACE_CODE,
                null,
                "processor xpath",
                request("GET", baseUrl + "/xml", List.of()),
                List.of(new ApiAssertionInput("STATUS_CODE", null, "EQUALS", "200")),
                List.of(),
                List.of(),
                List.of(new ApiProcessorInput(
                        "post-xpath",
                        "EXTRACT",
                        "XPath extractor",
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(extractor("xmlName", "X_PATH", "BODY", "/root/user/name", "SPECIFIC", 1, null))
                )),
                null,
                null
        ));

        ApiRunStepResultResponse step = run.stepResults().getFirst();
        assertThat(run.result()).isEqualTo("SUCCESS");
        assertThat(processorResult(step, "POST", "EXTRACT").outputVariables()).containsEntry("xmlName", "Alice XML");
        assertExtraction(step, "xmlName", "Alice XML");
    }

    @Test
    void legacyBodyHeaderAndStatusExtractorsStillExecute() {
        ApiRunResponse run = apiAutomationService.debugRunDefinitionDraft(WORKSPACE_CODE, new ApiDebugDefinitionRequest(
                WORKSPACE_CODE,
                null,
                "legacy extractors",
                request("GET", baseUrl + "/json", List.of()),
                List.of(new ApiAssertionInput("STATUS_CODE", null, "EQUALS", "200")),
                List.of(
                        new ApiExtractorInput("legacyJson", "BODY_JSONPATH", "$.legacy"),
                        new ApiExtractorInput("legacyHeader", "HEADER", "X-Legacy"),
                        new ApiExtractorInput("legacyStatus", "STATUS_CODE", "")
                ),
                List.of(),
                List.of(),
                null,
                null
        ));

        ApiRunStepResultResponse step = run.stepResults().getFirst();
        assertThat(run.result()).isEqualTo("SUCCESS");
        assertThat(processorResult(step, "POST", "EXTRACT").outputVariables())
                .containsEntry("legacyJson", "json-ok")
                .containsEntry("legacyHeader", "legacy-header")
                .containsEntry("legacyStatus", "200");
        assertExtraction(step, "legacyJson", "json-ok");
        assertExtraction(step, "legacyHeader", "legacy-header");
        assertExtraction(step, "legacyStatus", "200");
    }

    private DbConnectionItem createH2Connection(String name) {
        return settingsService.createDbConnection(WORKSPACE_CODE, new DbConnectionRequest(
                null,
                name,
                "H2",
                "org.h2.Driver",
                "jdbc:h2:mem:" + name + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa",
                "",
                2,
                5000,
                "processor test",
                1
        ));
    }

    private ApiRequestConfigInput request(String method, String path, List<ApiKeyValueInput> headers) {
        return new ApiRequestConfigInput(
                method,
                path,
                5000,
                List.of(),
                headers,
                List.of(),
                new ApiRequestBodyInput("NONE", null, List.of(), null, null, null),
                new ApiAuthConfigInput("NONE", null, null)
        );
    }

    private ApiProcessorExtractItemInput extractor(
            String variableName,
            String type,
            String scope,
            String expression,
            String matchingRule,
            Integer matchingRuleNum,
            String expressionMatchingRule
    ) {
        return new ApiProcessorExtractItemInput(
                variableName,
                null,
                expression,
                true,
                variableName,
                null,
                "TEMPORARY",
                type,
                scope,
                expressionMatchingRule,
                matchingRule,
                matchingRuleNum,
                "JSON"
        );
    }

    private ApiProcessorResult processorResult(ApiRunStepResultResponse step, String stage, String type) {
        return step.processorResults().stream()
                .filter(result -> stage.equals(result.stage()) && type.equals(result.processorType()))
                .findFirst()
                .orElseThrow();
    }

    private void assertExtraction(ApiRunStepResultResponse step, String name, String value) {
        assertThat(step.extractionResults())
                .anySatisfy(result -> {
                    assertThat(result.name()).isEqualTo(name);
                    assertThat(result.success()).isTrue();
                    assertThat(result.value()).isEqualTo(value);
                });
    }

    private void writeResponse(HttpExchange exchange, int status, String contentType, Map<String, String> headers, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        headers.forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
