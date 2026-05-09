package com.company.autoplatform.ai;

public record AiCaseConfigItem(
        Long id,
        String workspaceCode,
        String workspaceName,
        String roleType,
        String provider,
        String model,
        String baseUrl,
        String apiKeyMasked,
        boolean apiKeyConfigured,
        String promptTemplate,
        String reviewChecklist,
        Double temperature,
        Integer maxCases,
        boolean supportsImageInput,
        Integer status
) {
}
