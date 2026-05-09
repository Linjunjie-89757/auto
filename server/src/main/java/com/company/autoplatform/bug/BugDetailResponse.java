package com.company.autoplatform.bug;

import java.util.List;

public record BugDetailResponse(
        Long id,
        String bugNo,
        String title,
        String description,
        BugPriority priority,
        BugSeverity severity,
        BugStatus status,
        BugSourceType sourceType,
        Long assigneeId,
        String assigneeName,
        Long reporterId,
        String reporterName,
        Long relatedCaseId,
        Long relatedReportId,
        Long relatedTaskId,
        List<String> tags,
        String workspaceCode,
        String workspaceName,
        List<BugFlowResponse> flows,
        List<BugCommentResponse> comments
) {
}
