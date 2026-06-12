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

import static com.company.autoplatform.apiautomation.ApiAutomationModels.ApiRequestConfigInput;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

    private List<SseEvent> streamGenerate(ApiAiCaseGenerationService.ApiAiCaseGenerationRequest request) throws Exception {
        StringWriter writer = new StringWriter();
        service.streamGenerate(WORKSPACE_CODE, request, writer);
        return parseEvents(writer.toString());
    }

    private Long createProvider(String suffix) {
        AiProviderConnectionEntity entity = new AiProviderConnectionEntity();
        entity.setWorkspaceId(0L);
        entity.setOwnerUserId(11L);
        entity.setConnectionName("api-ai-generation-" + suffix + "-" + System.nanoTime());
        entity.setProtocolType(AiProviderClient.PROTOCOL_OPENAI_COMPATIBLE_CHAT);
        entity.setBaseUrl("https://ai.example.test/v1");
        entity.setRequestTimeoutSeconds(30);
        entity.setSelectedModelName("gpt-test");
        entity.setApiKeyCipherText(aiSecretCodec.encrypt("secret-" + suffix));
        entity.setStatus(1);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        providerConnectionMapper.insert(entity);
        return entity.getId();
    }

    private ApiAiCaseGenerationService.ApiAiCaseGenerationRequest request(Long providerId, String caseCount) {
        return new ApiAiCaseGenerationService.ApiAiCaseGenerationRequest(
                WORKSPACE_CODE,
                100L,
                "Demo API",
                "Demo API",
                "GET",
                "/ok",
                "demo description",
                providerId,
                "gpt-test",
                caseCount,
                true,
                "extra prompt",
                List.of(new ApiAiCaseGenerationService.ApiAiCaseGenerationOption(
                        "case-positive",
                        "positive",
                        "positive",
                        "Smoke",
                        "Positive"
                )),
                new ApiRequestConfigInput("GET", "/ok", 3000, List.of(), List.of(), List.of(), null, null),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
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

    private record SseEvent(String name, JsonNode data) {
    }
}
