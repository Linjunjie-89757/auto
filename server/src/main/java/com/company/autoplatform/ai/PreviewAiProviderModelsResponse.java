package com.company.autoplatform.ai;

import java.time.LocalDateTime;
import java.util.List;

public record PreviewAiProviderModelsResponse(
        List<AiProviderModelItem> models,
        LocalDateTime fetchedAt,
        String message
) {
}
