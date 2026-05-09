package com.company.autoplatform.bug;

import java.time.LocalDateTime;

public record BugCommentResponse(
        Long id,
        String content,
        Long commenterId,
        String commenterName,
        LocalDateTime createdAt
) {
}
