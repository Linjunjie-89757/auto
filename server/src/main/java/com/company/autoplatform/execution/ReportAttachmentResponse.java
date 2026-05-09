package com.company.autoplatform.execution;

import java.time.LocalDateTime;

public record ReportAttachmentResponse(
        Long id,
        String fileName,
        String contentType,
        Long fileSize,
        String downloadUrl,
        LocalDateTime createdAt
) {
}
