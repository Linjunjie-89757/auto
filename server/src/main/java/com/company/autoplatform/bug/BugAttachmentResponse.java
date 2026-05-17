package com.company.autoplatform.bug;

import java.time.LocalDateTime;

public record BugAttachmentResponse(
        Long id,
        String fileName,
        String contentType,
        Long fileSize,
        String downloadUrl,
        String uploadedByName,
        LocalDateTime createdAt
) {
}
