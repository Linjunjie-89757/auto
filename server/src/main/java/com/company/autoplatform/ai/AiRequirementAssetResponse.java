package com.company.autoplatform.ai;

import java.time.LocalDateTime;

public record AiRequirementAssetResponse(
        Long id,
        String sourceType,
        String fileName,
        String contentType,
        Long fileSize,
        String extractedText,
        String downloadUrl,
        LocalDateTime createdAt
) {
}
