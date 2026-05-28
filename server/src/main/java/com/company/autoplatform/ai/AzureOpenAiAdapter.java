package com.company.autoplatform.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.http.HttpRequest;
import java.util.List;

@Component
class AzureOpenAiAdapter extends OpenAiCompatibleChatAdapter {

    AzureOpenAiAdapter(@Value("${app.ai.request-timeout-seconds:60}") long timeoutSeconds) {
        super(timeoutSeconds);
    }

    @Override
    public String protocolType() {
        return AiProviderClient.PROTOCOL_AZURE_OPENAI;
    }

    @Override
    protected void applyAuthHeader(HttpRequest.Builder builder, String apiKey) {
        builder.header("api-key", apiKey);
    }

    @Override
    public AiModelFetchResult fetchModels(AiProviderRequestProfile profile, String apiKey) {
        return new AiModelFetchResult(List.of(), "Azure OpenAI 首版不稳定拉模型列表，可手工输入模型或部署名");
    }
}
