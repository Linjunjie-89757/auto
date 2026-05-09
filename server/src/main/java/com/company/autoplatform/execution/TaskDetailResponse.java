package com.company.autoplatform.execution;

import java.time.LocalDateTime;
import java.util.List;

public record TaskDetailResponse(
        Long id,
        String taskName,
        String engineType,
        String status,
        String summary,
        String workspaceCode,
        String workspaceName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<ReportSummaryResponse> reports
) {
}
