package com.company.autoplatform.bug;

import java.time.LocalDateTime;

public record BugSummaryResponse(
        Long id,
        String bugNo,
        String title,
        BugPriority priority,
        BugSeverity severity,
        BugStatus status,
        String assigneeName,
        String reporterName,
        LocalDateTime createdAt,
        Long relatedCaseId,
        String workspaceCode,
        String workspaceName
) {
}
