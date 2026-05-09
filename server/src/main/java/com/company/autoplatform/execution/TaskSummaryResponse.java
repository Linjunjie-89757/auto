package com.company.autoplatform.execution;

public record TaskSummaryResponse(
        Long id,
        String taskName,
        String engineType,
        String status,
        String summary,
        String workspaceCode,
        String workspaceName
) {
}
