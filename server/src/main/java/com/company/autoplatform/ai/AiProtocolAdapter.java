package com.company.autoplatform.ai;

import java.util.List;
import java.util.function.Consumer;

interface AiProtocolAdapter {

    String protocolType();

    void testConnection(AiProviderRequestProfile profile, String apiKey);

    AiModelFetchResult fetchModels(AiProviderRequestProfile profile, String apiKey);

    AiModelCapabilities probeCapabilities(AiProviderRequestProfile profile, String apiKey);

    String requestStructuredContent(AiProviderRequestProfile profile, String apiKey, String prompt, List<AiProviderClient.ImageInput> images);

    default boolean supportsStructuredStreaming() {
        return false;
    }

    default String streamStructuredContent(
            AiProviderRequestProfile profile,
            String apiKey,
            String prompt,
            List<AiProviderClient.ImageInput> images,
            Consumer<String> deltaConsumer
    ) {
        String content = requestStructuredContent(profile, apiKey, prompt, images);
        if (deltaConsumer != null && content != null && !content.isBlank()) {
            deltaConsumer.accept(content);
        }
        return content;
    }
}
