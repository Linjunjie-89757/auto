package com.company.autoplatform.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

@Component
class OpenAiCompatibleChatAdapter extends AbstractOpenAiCompatibleAdapter {

    OpenAiCompatibleChatAdapter(@Value("${app.ai.request-timeout-seconds:60}") long timeoutSeconds) {
        super(timeoutSeconds);
    }

    @Override
    public String protocolType() {
        return AiProviderClient.PROTOCOL_OPENAI_COMPATIBLE_CHAT;
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
        try {
            String content = requestStructuredContentWithChat(profile, apiKey, """
                    Return only JSON.
                    Reply exactly with:
                    {"stream":true}
                    """, List.of(), true);
            boolean ok = content.contains("\"stream\":true");
            return AiModelCapabilities.value(ok, AiModelCapabilities.SOURCE_PROBED, ok ? "流式输出探测成功" : "流式输出探测失败");
        } catch (RuntimeException exception) {
            return AiModelCapabilities.value(false, AiModelCapabilities.SOURCE_PROBED, exception.getMessage());
        }
    }

    @Override
    public String requestStructuredContent(AiProviderRequestProfile profile, String apiKey, String prompt, List<AiProviderClient.ImageInput> images) {
        return requestStructuredContentWithChat(profile, apiKey, prompt, images, false);
    }

    @Override
    public String streamStructuredContent(
            AiProviderRequestProfile profile,
            String apiKey,
            String prompt,
            List<AiProviderClient.ImageInput> images,
            Consumer<String> deltaConsumer
    ) {
        return streamStructuredContentWithChat(profile, apiKey, prompt, images, deltaConsumer);
    }
}
