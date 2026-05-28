package com.company.autoplatform.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
class AiProviderClientTests {

    private final AiProviderClient client = new AiProviderClient(60);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void openAiChatAppendsChatCompletionsForServiceRootBaseUrl() {
        String endpoint = invokeResolveEndpoint("https://api.openai.com/v1", "OPENAI_CHAT_COMPLETIONS");

        assertThat(endpoint).isEqualTo("https://api.openai.com/v1/chat/completions");
    }

    @Test
    void openAiResponsesAppendsResponsesForServiceRootBaseUrl() {
        String endpoint = invokeResolveEndpoint("https://proxy.example/v1", "OPENAI_RESPONSES");

        assertThat(endpoint).isEqualTo("https://proxy.example/v1/responses");
    }

    @Test
    void explicitChatCompletionsEndpointIsPreserved() {
        String endpoint = invokeResolveEndpoint("https://proxy.example/v1/chat/completions", "OPENAI_CHAT_COMPLETIONS");

        assertThat(endpoint).isEqualTo("https://proxy.example/v1/chat/completions");
    }

    @Test
    void buildsResponsesRequestBodyWithInputField() throws Exception {
        AiCaseConfigEntity config = new AiCaseConfigEntity();
        config.setModel("gpt-5");
        config.setTemperature(0.3);

        String body = invokeBuildRequestBody(config, "OPENAI_RESPONSES", "hello", List.of(), false);
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
        AiCaseConfigEntity config = new AiCaseConfigEntity();
        config.setModel("gpt-5");
        config.setTemperature(0.3);

        String body = invokeBuildRequestBody(
                config,
                "OPENAI_RESPONSES",
                "hello",
                List.of(new AiProviderClient.ImageInput("a.png", "image/png", new byte[]{1, 2, 3})),
                false
        );
        JsonNode root = objectMapper.readTree(body);

        assertThat(root.path("input").path(0).path("content").size()).isEqualTo(2);
        assertThat(root.path("input").path(0).path("content").path(1).path("type").asText()).isEqualTo("input_image");
        assertThat(root.path("input").path(0).path("content").path(1).path("image_url").asText())
                .startsWith("data:image/png;base64,");
    }

    @Test
    void extractsResponsesOutputText() {
        String content = invokeExtractContent("OPENAI_RESPONSES", "{\"output_text\":\"{\\\"ok\\\":true}\"}");

        assertThat(content).isEqualTo("{\"ok\":true}");
    }

    private String invokeResolveEndpoint(String baseUrl, String protocolType) {
        return (String) ReflectionTestUtils.invokeMethod(client, "resolveEndpointByProtocol", baseUrl, protocolType);
    }

    private String invokeBuildRequestBody(
            AiCaseConfigEntity config,
            String protocolType,
            String prompt,
            List<AiProviderClient.ImageInput> images,
            boolean stream
    ) {
        return (String) ReflectionTestUtils.invokeMethod(
                client,
                "buildRequestBodyByProtocol",
                config,
                protocolType,
                prompt,
                images,
                stream
        );
    }

    private String invokeExtractContent(String protocolType, String responseBody) {
        return (String) ReflectionTestUtils.invokeMethod(client, "extractContentByProtocol", protocolType, responseBody);
    }
}
