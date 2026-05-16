package com.company.autoplatform.bug;

public record BugCaseSummaryResponse(
        Long id,
        String caseNo,
        String title,
        String workspaceCode,
        String workspaceName,
        Long directoryId,
        String directoryName,
        String modulePath,
        String executionStatus,
        String executionComment,
        String executedAt
) {
}
