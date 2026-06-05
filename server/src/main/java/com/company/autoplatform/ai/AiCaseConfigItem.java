package com.company.autoplatform.ai;

public record AiCaseConfigItem(
        Long id,
        String workspaceCode,
        String workspaceName,
        String roleType,
        Long providerConnectionId,
        String providerConnectionName,
        String protocolType,
        String provider,
        String model,
        String baseUrl,
        String apiKeyMasked,
        boolean apiKeyConfigured,
        String promptTemplate,
        String reviewChecklist,
        Double temperature,
        Double topP,
        Integer maxCases,
        AiModelCapabilities detectedCapabilities,
        AiModelCapabilities effectiveCapabilities,
        AiCapabilityOverride capabilityOverride,
        boolean supportsImageInput,
        Integer status
) {
}
