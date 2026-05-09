package com.company.autoplatform.execution;

import java.time.LocalDateTime;
import java.util.List;

public record ReportDetailResponse(
        Long id,
        Long taskId,
        String taskName,
        String reportName,
        String result,
        String logSource,
        String workspaceCode,
        String workspaceName,
        String failureSummary,
        String logText,
        List<ReportAttachmentResponse> attachments,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
