package com.company.autoplatform.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiProviderClientTests {

    private final OpenAiCompatibleChatAdapter chatAdapter = new OpenAiCompatibleChatAdapter(60);
    private final OpenAiCompatibleResponsesAdapter responsesAdapter = new OpenAiCompatibleResponsesAdapter(60);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void openAiChatAppendsChatCompletionsForServiceRootBaseUrl() {
        String endpoint = (String) ReflectionTestUtils.invokeMethod(chatAdapter, "resolveChatEndpoint", "https://api.openai.com/v1");

        assertThat(endpoint).isEqualTo("https://api.openai.com/v1/chat/completions");
    }

    @Test
    void openAiResponsesAppendsResponsesForServiceRootBaseUrl() {
        String endpoint = (String) ReflectionTestUtils.invokeMethod(responsesAdapter, "resolveResponsesEndpoint", "https://proxy.example/v1");

        assertThat(endpoint).isEqualTo("https://proxy.example/v1/responses");
    }

    @Test
    void explicitChatCompletionsEndpointFallsBackToServiceRoot() {
        String endpoint = (String) ReflectionTestUtils.invokeMethod(chatAdapter, "resolveChatEndpoint", "https://proxy.example/v1/chat/completions");

        assertThat(endpoint).isEqualTo("https://proxy.example/v1/chat/completions");
    }

    @Test
    void buildsResponsesRequestBodyWithInputField() throws Exception {
        AiProviderRequestProfile profile = new AiProviderRequestProfile(
                AiProviderClient.PROTOCOL_OPENAI_COMPATIBLE_RESPONSES,
                "OPENAI_COMPATIBLE_RESPONSES",
                "gpt-5",
                "https://proxy.example/v1",
                0.3,
                20
        );

        String body = (String) ReflectionTestUtils.invokeMethod(
                responsesAdapter,
                "buildResponsesRequestBody",
                profile,
                "hello",
                List.of()
        );
        JsonNode root = objectMapper.readTree(body);

        assertThat(root.path("instructions").asText()).contains("structured JSON");
        assertThat(root.has("messages")).isFalse();
        assertThat(root.path("input").isArray()).isTrue();
        assertThat(root.path("input").path(0).path("role").asText()).isEqualTo("user");
        assertThat(root.path("input").path(0).path("content").path(0).path("type").asText()).isEqualTo("input_text");
        assertThat(root.path("input").path(0).path("content").path(0).path("text").asText()).isEqualTo("hello");
    }

    @Test
    void buildsResponsesRequestBodyWithInputImageItems() throws Exception {
        AiProviderRequestProfile profile = new AiProviderRequestProfile(
                AiProviderClient.PROTOCOL_OPENAI_COMPATIBLE_RESPONSES,
                "OPENAI_COMPATIBLE_RESPONSES",
                "gpt-5",
                "https://proxy.example/v1",
                0.3,
                20
        );

        String body = (String) ReflectionTestUtils.invokeMethod(
                responsesAdapter,
                "buildResponsesRequestBody",
                profile,
                "hello",
                List.of(new AiProviderClient.ImageInput("a.png", "image/png", new byte[]{1, 2, 3}))
        );
        JsonNode root = objectMapper.readTree(body);

        assertThat(root.path("input").path(0).path("content").size()).isEqualTo(2);
        assertThat(root.path("input").path(0).path("content").path(1).path("type").asText()).isEqualTo("input_image");
        assertThat(root.path("input").path(0).path("content").path(1).path("image_url").asText())
                .startsWith("data:image/png;base64,");
    }

    @Test
    void extractsResponsesOutputText() {
        String content = (String) ReflectionTestUtils.invokeMethod(
                responsesAdapter,
                "extractResponsesContent",
                "{\"output_text\":\"{\\\"ok\\\":true}\"}"
        );

        assertThat(content).isEqualTo("{\"ok\":true}");
    }
}
