package com.company.autoplatform.bug;

public record BugTaskSummaryResponse(
        Long id,
        String taskName,
        String engineType,
        String status,
        String workspaceCode,
        String workspaceName
) {
}
