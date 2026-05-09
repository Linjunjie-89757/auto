package com.company.autoplatform.execution;

public record ReportSummaryResponse(
        Long id,
        Long taskId,
        String reportName,
        String result,
        String logSource,
        String workspaceCode,
        String workspaceName,
        String failureSummary
) {
}
