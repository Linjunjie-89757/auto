package com.company.autoplatform.casecenter;

import java.time.LocalDateTime;

public record CaseExecutionAttachmentResponse(
        Long id,
        String fileName,
        String contentType,
        Long fileSize,
        String downloadUrl,
        LocalDateTime createdAt
) {
}
