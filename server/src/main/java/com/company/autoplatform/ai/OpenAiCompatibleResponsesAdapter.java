package com.company.autoplatform.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

@Component
class OpenAiCompatibleResponsesAdapter extends AbstractOpenAiCompatibleAdapter {

    OpenAiCompatibleResponsesAdapter(@Value("${app.ai.request-timeout-seconds:60}") long timeoutSeconds) {
        super(timeoutSeconds);
    }

    @Override
    public String protocolType() {
        return AiProviderClient.PROTOCOL_OPENAI_COMPATIBLE_RESPONSES;
    }

    @Override
    public boolean supportsStructuredStreaming() {
        return true;
    }

    @Override
    public AiCapabilityValue probeStreamCapability(
            AiProviderRequestProfile profile,
            String apiKey,
            AiCapabilityValue currentValue
    ) {
        return AiModelCapabilities.value(true, AiModelCapabilities.SOURCE_INFERRED, "Responses 协议通常支持流式输出，首版未主动探测");
    }

    @Override
    public String requestStructuredContent(AiProviderRequestProfile profile, String apiKey, String prompt, List<AiProviderClient.ImageInput> images) {
        return requestStructuredContentWithResponses(profile, apiKey, prompt, images);
    }

    @Override
    public String streamStructuredContent(
            AiProviderRequestProfile profile,
            String apiKey,
            String prompt,
            List<AiProviderClient.ImageInput> images,
            Consumer<String> deltaConsumer
    ) {
        return streamStructuredContentWithResponses(profile, apiKey, prompt, images, deltaConsumer);
    }
}
