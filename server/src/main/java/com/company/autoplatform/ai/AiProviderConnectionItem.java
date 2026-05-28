package com.company.autoplatform.ai;

import java.time.LocalDateTime;

public record AiProviderConnectionItem(
        Long id,
        String workspaceCode,
        String workspaceName,
        String connectionName,
        String protocolType,
        String baseUrl,
        String apiKeyMasked,
        boolean apiKeyConfigured,
        Integer status,
        Integer modelCount,
        LocalDateTime lastVerifiedAt,
        LocalDateTime lastFetchModelsAt
) {
}
