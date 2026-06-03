package com.company.autoplatform.ai;

public record AiGenerationTaskEventResponse(
        Long id,
        String taskId,
        Integer seq,
        String eventType,
        String phase,
        String level,
        String message,
        Integer itemIndex,
        String itemTitle,
        String provider,
        String model,
        String payloadJson,
        String createdAt
) {
}
