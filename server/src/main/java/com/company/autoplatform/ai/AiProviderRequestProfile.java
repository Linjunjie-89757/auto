package com.company.autoplatform.ai;

public record AiProviderRequestProfile(
        String protocolType,
        String provider,
        String model,
        String baseUrl,
        Double temperature,
        Double topP,
        Integer maxCases,
        Integer requestTimeoutSeconds
) {
}
