package com.company.autoplatform.apiautomation;

import com.company.autoplatform.IntegrationTestSupport;
import com.company.autoplatform.ai.AiProviderClient;
import com.company.autoplatform.ai.AiProviderConnectionEntity;
import com.company.autoplatform.ai.AiProviderConnectionMapper;
import com.company.autoplatform.ai.AiProviderRequestProfile;
import com.company.autoplatform.ai.AiSecretCodec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.company.autoplatform.apiautomation.ApiAutomationModels.ApiAuthConfigInput;
import static com.company.autoplatform.apiautomation.ApiAutomationModels.ApiAuthCredentialInput;
import static com.company.autoplatform.apiautomation.ApiAutomationModels.ApiRequestBodyInput;
import static com.company.autoplatform.apiautomation.ApiAutomationModels.ApiRequestConfigInput;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class ApiAiCaseGenerationServiceTests extends IntegrationTestSupport {

    @Autowired
    private ApiAiCaseGenerationService service;

    @Autowired
    private AiProviderConnectionMapper providerConnectionMapper;

    @Autowired
    private AiSecretCodec aiSecretCodec;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AiProviderClient aiProviderClient;

    @Test
    void streamGenerateEmitsOutlineDetailAndCompletedEventsForSuccessfulCase() throws Exception {
        Long providerId = createProvider("success");
        when(aiProviderClient.streamStructuredContent(any(AiProviderRequestProfile.class), eq("secret-success"), any(), any()))
                .thenReturn("""
                        {"id":"case-positive","outline":{"name":"Positive smoke","description":"outline desc","tags":["ai"],"group":"Positive","groupKey":"positive","type":"Smoke","typeKey":"smoke","expected":"outline expected"}}
                        """);
        when(aiProviderClient.requestStructuredContent(any(AiProviderRequestProfile.class), eq("secret-success"), any()))
                .thenReturn("""
                        {"case":{"name":"Positive smoke detail","description":"detail desc","tags":["ai","smoke"],"group":"Positive","groupKey":"positive","type":"Smoke","typeKey":"smoke","expected":"detail expected","requestConfig":{"method":"GET","path":"/ok","timeoutMs":3000,"queryParams":[],"headers":[],"cookies":[],"body":null,"authConfig":null},"assertions":[],"preProcessors":[],"postProcessors":[]}}
                        """);

        List<SseEvent> events = streamGenerate(request(providerId, "1"));

        assertThat(events).extracting(SseEvent::name)
                .containsExactly("started", "item_outline", "item_completed", "completed");
        assertThat(events.get(0).data.path("total").asInt()).isEqualTo(1);
        assertThat(events.get(1).data.path("itemId").asText()).isEqualTo("case-positive");
        assertThat(events.get(1).data.path("outline").path("name").asText())
                .startsWith("Smoke")
                .endsWith("Positive smoke");
        assertThat(events.get(2).data.path("item").path("name").asText())
                .startsWith("Smoke")
                .endsWith("Positive smoke detail");
        assertThat(events.get(2).data.path("item").path("requestConfig").path("method").asText()).isEqualTo("GET");
        assertThat(events.get(2).data.path("item").path("requestConfig").path("path").asText()).isEqualTo("/ok");
        assertThat(events.get(3).data.path("message").isNull()).isTrue();
    }

    @Test
    void streamGenerateEmitsFailedEventWhenProviderConnectionIdIsMissing() throws Exception {
        List<SseEvent> events = streamGenerate(request(null, "1"));

        assertThat(events).extracting(SseEvent::name)
                .containsExactly("failed");
        assertThat(events.get(0).data.path("message").asText()).contains("AI");
    }

    @Test
    void streamGenerateEmitsFailedEventWhenProviderDoesNotExist() throws Exception {
        List<SseEvent> events = streamGenerate(request(-99999L, "1"));

        assertThat(events).extracting(SseEvent::name)
                .containsExactly("failed");
        assertThat(events.get(0).data.path("message").asText()).contains("AI");
    }

    @Test
    void streamGenerateEmitsFailedEventWhenProviderIsDisabled() throws Exception {
        Long providerId = createProvider("disabled", 0);

        List<SseEvent> events = streamGenerate(request(providerId, "1"));

        assertThat(events).extracting(SseEvent::name)
                .containsExactly("failed");
        assertThat(events.get(0).data.path("message").asText()).contains("AI");
    }

    @Test
    void streamGenerateEmitsItemFailedAndCompletedEventsWhenOutlineGenerationFails() throws Exception {
        Long providerId = createProvider("outline-failed");
        when(aiProviderClient.streamStructuredContent(any(AiProviderRequestProfile.class), eq("secret-outline-failed"), any(), any()))
                .thenThrow(new RuntimeException("outline boom"));

        List<SseEvent> events = streamGenerate(request(providerId, "1"));

        assertThat(events).extracting(SseEvent::name)
                .containsExactly("started", "item_failed", "completed");
        assertThat(events.get(1).data.path("itemId").asText()).isEqualTo("case-positive");
        assertThat(events.get(1).data.path("message").asText()).contains("outline boom");
        assertThat(events.get(2).data.path("message").asText()).isNotBlank();
    }

    @Test
    void streamGenerateParsesMultipleOutlineNdjsonLinesInOrder() throws Exception {
        Long providerId = createProvider("outline-ndjson");
        when(aiProviderClient.streamStructuredContent(any(AiProviderRequestProfile.class), eq("secret-outline-ndjson"), any(), any()))
                .thenReturn("""
                        {"id":"case-positive","outline":{"name":"Positive smoke","description":"outline desc","type":"Smoke","expected":"positive expected"}}
                        {"id":"case-negative","outline":{"name":"Negative smoke","description":"outline desc","type":"Negative","expected":"negative expected"}}
                        """);
        when(aiProviderClient.requestStructuredContent(any(AiProviderRequestProfile.class), eq("secret-outline-ndjson"), any()))
                .thenReturn("""
                        {"case":{"name":"Detail smoke","description":"detail desc","expected":"detail expected","requestConfig":{"method":"GET","path":"/ok","timeoutMs":3000,"queryParams":[],"headers":[],"cookies":[],"body":null,"authConfig":null},"assertions":[],"preProcessors":[],"postProcessors":[]}}
                        """);

        List<SseEvent> events = streamGenerate(request(providerId, "2", List.of(
                option("case-positive", "positive", "positive", "Smoke", "Positive"),
                option("case-negative", "negative", "negative", "Negative", "Negative")
        )));

        assertThat(events).extracting(SseEvent::name)
                .containsExactly("started", "item_outline", "item_outline", "item_completed", "item_completed", "completed");
        assertThat(events.get(1).data.path("itemId").asText()).isEqualTo("case-positive");
        assertThat(events.get(2).data.path("itemId").asText()).isEqualTo("case-negative");
        assertThat(events.get(3).data.path("itemId").asText()).isEqualTo("case-positive");
        assertThat(events.get(4).data.path("itemId").asText()).isEqualTo("case-negative");
        assertThat(events.get(5).data.path("message").isNull()).isTrue();
    }

    @Test
    void streamGenerateParsesOutlineFallbackFromReturnedContentWhenNoLineCallbackIsEmitted() throws Exception {
        Long providerId = createProvider("outline-fallback");
        when(aiProviderClient.streamStructuredContent(any(AiProviderRequestProfile.class), eq("secret-outline-fallback"), any(), any()))
                .thenReturn("""
                        {"id":"case-positive","outline":{"name":"Fallback outline","description":"outline desc","type":"Smoke","expected":"fallback expected"}}
                        """);
        when(aiProviderClient.requestStructuredContent(any(AiProviderRequestProfile.class), eq("secret-outline-fallback"), any()))
                .thenReturn("""
                        {"case":{"name":"Fallback detail","description":"detail desc","expected":"detail expected","requestConfig":{"method":"GET","path":"/ok","timeoutMs":3000,"queryParams":[],"headers":[],"cookies":[],"body":null,"authConfig":null},"assertions":[],"preProcessors":[],"postProcessors":[]}}
                        """);

        List<SseEvent> events = streamGenerate(request(providerId, "1"));

        assertThat(events).extracting(SseEvent::name)
                .containsExactly("started", "item_outline", "item_completed", "completed");
        assertThat(events.get(1).data.path("outline").path("name").asText())
                .endsWith("Fallback outline");
    }

    @Test
    void streamGenerateParsesSingleCaseJsonWithoutCaseWrapper() throws Exception {
        Long providerId = createProvider("single-fallback");
        when(aiProviderClient.streamStructuredContent(any(AiProviderRequestProfile.class), eq("secret-single-fallback"), any(), any()))
                .thenReturn("""
                        {"id":"case-positive","outline":{"name":"Single outline","description":"outline desc","type":"Smoke","expected":"single expected"}}
                        """);
        when(aiProviderClient.requestStructuredContent(any(AiProviderRequestProfile.class), eq("secret-single-fallback"), anyString()))
                .thenReturn("""
                        {"name":"Single fallback detail","description":"detail desc","expected":"detail expected","requestConfig":{"method":"POST","path":"/single","timeoutMs":3000,"queryParams":[],"headers":[],"cookies":[],"body":null,"authConfig":null},"assertions":[],"preProcessors":[],"postProcessors":[]}
                        """);

        List<SseEvent> events = streamGenerate(request(providerId, "1"));

        assertThat(events).extracting(SseEvent::name)
                .containsExactly("started", "item_outline", "item_completed", "completed");
        assertThat(events.get(2).data.path("item").path("name").asText())
                .endsWith("Single fallback detail");
        assertThat(events.get(2).data.path("item").path("requestConfig").path("method").asText()).isEqualTo("POST");
        assertThat(events.get(2).data.path("item").path("requestConfig").path("path").asText()).isEqualTo("/single");
    }

    @Test
    void streamGenerateFallsBackToSourceRequestConfigWhenDetailOmitsRequestConfig() throws Exception {
        Long providerId = createProvider("detail-request-fallback");
        when(aiProviderClient.streamStructuredContent(any(AiProviderRequestProfile.class), eq("secret-detail-request-fallback"), any(), any()))
                .thenReturn("""
                        {"id":"case-positive","outline":{"name":"Fallback outline","description":"outline desc","type":"Smoke","expected":"fallback expected"}}
                        """);
        when(aiProviderClient.requestStructuredContent(any(AiProviderRequestProfile.class), eq("secret-detail-request-fallback"), anyString()))
                .thenReturn("""
                        {"case":{"name":"Fallback detail","description":"detail desc","tags":["custom"],"expected":"detail expected","assertions":[],"preProcessors":[],"postProcessors":[]}}
                        """);

        ApiRequestBodyInput body = new ApiRequestBodyInput("RAW_JSON", "{\"source\":true}", List.of(),
                "application/json", null, null);
        ApiAuthConfigInput authConfig = new ApiAuthConfigInput("BASIC",
                new ApiAuthCredentialInput("api-user", "api-pass"),
                new ApiAuthCredentialInput("", ""));
        ApiRequestConfigInput sourceConfig = new ApiRequestConfigInput("POST", "/fallback", 7000,
                List.of(), List.of(), List.of(), body, authConfig);

        List<SseEvent> events = streamGenerate(request(providerId, "1", List.of(
                option("case-positive", "positive", "positive", "Smoke", "Positive")
        ), sourceConfig));

        JsonNode item = events.get(2).data.path("item");
        JsonNode requestConfig = item.path("requestConfig");
        assertThat(requestConfig.path("method").asText()).isEqualTo("POST");
        assertThat(requestConfig.path("path").asText()).isEqualTo("/fallback");
        assertThat(requestConfig.path("timeoutMs").asInt()).isEqualTo(7000);
        assertThat(requestConfig.path("body").path("type").asText()).isEqualTo("RAW_JSON");
        assertThat(requestConfig.path("body").path("rawText").asText()).isEqualTo("{\"source\":true}");
        assertThat(requestConfig.path("authConfig").path("authType").asText()).isEqualTo("BASIC");
        assertThat(requestConfig.path("authConfig").path("basicAuth").path("userName").asText()).isEqualTo("api-user");
    }

    @Test
    void streamGenerateAddsDefaultAssertionsAndNormalizesTags() throws Exception {
        Long providerId = createProvider("detail-normalize");
        when(aiProviderClient.streamStructuredContent(any(AiProviderRequestProfile.class), eq("secret-detail-normalize"), any(), any()))
                .thenReturn("""
                        {"id":"case-positive","outline":{"name":"Normalize outline","description":"outline desc","type":"Smoke","expected":"normalize expected"}}
                        """);
        when(aiProviderClient.requestStructuredContent(any(AiProviderRequestProfile.class), eq("secret-detail-normalize"), anyString()))
                .thenReturn("""
                        {"case":{"name":"Normalize detail","description":"detail desc","tags":["custom"],"expected":"detail expected","requestConfig":{"method":"GET","path":"/ok","timeoutMs":3000,"queryParams":[],"headers":[],"cookies":[],"body":null,"authConfig":null},"assertions":[],"preProcessors":[],"postProcessors":[]}}
                        """);

        List<SseEvent> events = streamGenerate(request(providerId, "1"));

        JsonNode item = events.get(2).data.path("item");
        assertThat(jsonArrayValues(item.path("tags"))).contains("custom", "Positive", "Smoke");
        JsonNode assertion = item.path("assertions").get(0);
        assertThat(assertion.path("type").asText()).isEqualTo("STATUS_CODE");
        assertThat(assertion.path("subject").asText()).isEqualTo("STATUS_CODE");
        assertThat(assertion.path("operator").asText()).isEqualTo("LT");
        assertThat(assertion.path("expectedValue").asText()).isEqualTo("400");
    }

    @Test
    void streamGenerateNormalizesOutlineDefaultsFromSlot() throws Exception {
        Long providerId = createProvider("outline-normalize");
        when(aiProviderClient.streamStructuredContent(any(AiProviderRequestProfile.class), eq("secret-outline-normalize"), any(), any()))
                .thenReturn("""
                        {"id":"case-positive","outline":{"name":"Outline defaults","description":"outline desc","tags":["outline-tag"]}}
                        """);
        when(aiProviderClient.requestStructuredContent(any(AiProviderRequestProfile.class), eq("secret-outline-normalize"), anyString()))
                .thenReturn("""
                        {"case":{"name":"Detail defaults","description":"detail desc","expected":"detail expected","requestConfig":{"method":"GET","path":"/ok","timeoutMs":3000,"queryParams":[],"headers":[],"cookies":[],"body":null,"authConfig":null},"assertions":[],"preProcessors":[],"postProcessors":[]}}
                        """);

        List<SseEvent> events = streamGenerate(request(providerId, "1"));

        JsonNode outline = events.get(1).data.path("outline");
        assertThat(outline.path("name").asText()).startsWith("Smoke");
        assertThat(outline.path("group").asText()).isEqualTo("Positive");
        assertThat(outline.path("groupKey").asText()).isEqualTo("positive");
        assertThat(outline.path("type").asText()).isEqualTo("Smoke");
        assertThat(outline.path("typeKey").asText()).isEqualTo("positive");
        assertThat(outline.path("expected").asText()).isNotBlank();
        assertThat(jsonArrayValues(outline.path("tags"))).contains("outline-tag", "Positive", "Smoke");
    }

    @Test
    void streamGenerateEmitsItemFailedAndPartialCompletedWhenOneDetailGenerationFails() throws Exception {
        Long providerId = createProvider("detail-partial-failure");
        when(aiProviderClient.streamStructuredContent(any(AiProviderRequestProfile.class), eq("secret-detail-partial-failure"), any(), any()))
                .thenReturn("""
                        {"id":"case-positive","outline":{"name":"Positive outline","description":"outline desc","type":"Smoke","expected":"positive expected"}}
                        {"id":"case-negative","outline":{"name":"Negative outline","description":"outline desc","type":"Negative","expected":"negative expected"}}
                        """);
        when(aiProviderClient.requestStructuredContent(any(AiProviderRequestProfile.class), eq("secret-detail-partial-failure"), anyString()))
                .thenReturn("""
                        {"case":{"name":"Positive detail","description":"detail desc","expected":"detail expected","requestConfig":{"method":"GET","path":"/ok","timeoutMs":3000,"queryParams":[],"headers":[],"cookies":[],"body":null,"authConfig":null},"assertions":[],"preProcessors":[],"postProcessors":[]}}
                        """)
                .thenThrow(new RuntimeException("detail boom"));

        List<SseEvent> events = streamGenerate(request(providerId, "2", List.of(
                option("case-positive", "positive", "positive", "Smoke", "Positive"),
                option("case-negative", "negative", "negative", "Negative", "Negative")
        )));

        assertThat(events).extracting(SseEvent::name)
                .containsExactly("started", "item_outline", "item_outline", "item_completed", "item_failed", "completed");
        assertThat(events.get(4).data.path("itemId").asText()).isEqualTo("case-negative");
        assertThat(events.get(4).data.path("message").asText()).contains("detail boom");
        assertThat(events.get(5).data.path("message").asText()).contains("部分");
    }

    private List<SseEvent> streamGenerate(ApiAiCaseGenerationService.ApiAiCaseGenerationRequest request) throws Exception {
        StringWriter writer = new StringWriter();
        service.streamGenerate(WORKSPACE_CODE, request, writer);
        return parseEvents(writer.toString());
    }

    private Long createProvider(String suffix) {
        return createProvider(suffix, 1);
    }

    private Long createProvider(String suffix, int status) {
        AiProviderConnectionEntity entity = new AiProviderConnectionEntity();
        entity.setWorkspaceId(0L);
        entity.setOwnerUserId(11L);
        entity.setConnectionName("api-ai-generation-" + suffix + "-" + System.nanoTime());
        entity.setProtocolType(AiProviderClient.PROTOCOL_OPENAI_COMPATIBLE_CHAT);
        entity.setBaseUrl("https://ai.example.test/v1");
        entity.setRequestTimeoutSeconds(30);
        entity.setSelectedModelName("gpt-test");
        entity.setApiKeyCipherText(aiSecretCodec.encrypt("secret-" + suffix));
        entity.setStatus(status);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        providerConnectionMapper.insert(entity);
        return entity.getId();
    }

    private ApiAiCaseGenerationService.ApiAiCaseGenerationRequest request(Long providerId, String caseCount) {
        return request(providerId, caseCount, List.of(
                option("case-positive", "positive", "positive", "Smoke", "Positive")
        ));
    }

    private ApiAiCaseGenerationService.ApiAiCaseGenerationRequest request(
            Long providerId,
            String caseCount,
            List<ApiAiCaseGenerationService.ApiAiCaseGenerationOption> options
    ) {
        return request(providerId, caseCount, options,
                new ApiRequestConfigInput("GET", "/ok", 3000, List.of(), List.of(), List.of(), null, null));
    }

    private ApiAiCaseGenerationService.ApiAiCaseGenerationRequest request(
            Long providerId,
            String caseCount,
            List<ApiAiCaseGenerationService.ApiAiCaseGenerationOption> options,
            ApiRequestConfigInput requestConfig
    ) {
        return new ApiAiCaseGenerationService.ApiAiCaseGenerationRequest(
                WORKSPACE_CODE,
                100L,
                "Demo API",
                "Demo API",
                requestConfig.method(),
                requestConfig.path(),
                "demo description",
                providerId,
                "gpt-test",
                caseCount,
                true,
                "extra prompt",
                options,
                requestConfig,
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private ApiAiCaseGenerationService.ApiAiCaseGenerationOption option(
            String id,
            String key,
            String group,
            String label,
            String groupLabel
    ) {
        return new ApiAiCaseGenerationService.ApiAiCaseGenerationOption(id, key, group, label, groupLabel);
    }

    private List<SseEvent> parseEvents(String content) throws Exception {
        List<SseEvent> events = new ArrayList<>();
        for (String block : content.split("\\R\\R")) {
            if (block.isBlank()) {
                continue;
            }
            String name = null;
            String data = null;
            for (String line : block.split("\\R")) {
                if (line.startsWith("event: ")) {
                    name = line.substring("event: ".length());
                } else if (line.startsWith("data: ")) {
                    data = line.substring("data: ".length());
                }
            }
            assertThat(name).isNotBlank();
            assertThat(data).isNotBlank();
            events.add(new SseEvent(name, objectMapper.readTree(data)));
        }
        return events;
    }

    private List<String> jsonArrayValues(JsonNode node) {
        List<String> values = new ArrayList<>();
        node.forEach(item -> values.add(item.asText()));
        return values;
    }

    private record SseEvent(String name, JsonNode data) {
    }
}
