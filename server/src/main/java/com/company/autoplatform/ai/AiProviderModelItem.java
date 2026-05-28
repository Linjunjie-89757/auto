package com.company.autoplatform.ai;

import java.time.LocalDateTime;

public record AiProviderModelItem(
        Long id,
        Long connectionId,
        String modelName,
        String displayName,
        AiModelCapabilities detectedCapabilities,
        boolean selectable,
        String rawMetadataJson,
        LocalDateTime lastProbedAt
) {
}
