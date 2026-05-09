package com.company.autoplatform.bug;

public record BugSummaryResponse(
        Long id,
        String bugNo,
        String title,
        BugPriority priority,
        BugSeverity severity,
        BugStatus status,
        String assigneeName,
        String reporterName,
        String workspaceCode,
        String workspaceName
) {
}
