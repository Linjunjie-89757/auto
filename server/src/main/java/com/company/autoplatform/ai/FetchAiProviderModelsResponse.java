package com.company.autoplatform.ai;

import java.time.LocalDateTime;
import java.util.List;

public record FetchAiProviderModelsResponse(
        Long connectionId,
        String connectionName,
        List<AiProviderModelItem> models,
        LocalDateTime fetchedAt,
        String message
) {
}
