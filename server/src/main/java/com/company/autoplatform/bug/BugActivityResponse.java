package com.company.autoplatform.bug;

import java.time.LocalDateTime;

public record BugActivityResponse(
        String id,
        BugActivityType type,
        Long operatorId,
        String operatorName,
        LocalDateTime occurredAt,
        String title,
        String content,
        BugStatus fromStatus,
        BugStatus toStatus,
        Long attachmentId,
        String attachmentName,
        Long commentId
) {
}
