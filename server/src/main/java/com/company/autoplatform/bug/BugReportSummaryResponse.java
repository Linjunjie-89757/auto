package com.company.autoplatform.bug;

public record BugReportSummaryResponse(
        Long id,
        String reportName,
        String result,
        String failureSummary,
        Long taskId,
        String taskName,
        String workspaceCode,
        String workspaceName
) {
}
