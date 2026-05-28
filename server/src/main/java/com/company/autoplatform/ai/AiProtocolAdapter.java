package com.company.autoplatform.ai;

import java.util.List;

interface AiProtocolAdapter {

    String protocolType();

    void testConnection(AiProviderRequestProfile profile, String apiKey);

    AiModelFetchResult fetchModels(AiProviderRequestProfile profile, String apiKey);

    AiModelCapabilities probeCapabilities(AiProviderRequestProfile profile, String apiKey);

    String requestStructuredContent(AiProviderRequestProfile profile, String apiKey, String prompt, List<AiProviderClient.ImageInput> images);
}
