package com.company.autoplatform.bug;

import java.time.LocalDateTime;

public record BugFlowResponse(
        Long id,
        BugStatus fromStatus,
        BugStatus toStatus,
        Long operatorId,
        String operatorName,
        String actionComment,
        LocalDateTime createdAt
) {
}
