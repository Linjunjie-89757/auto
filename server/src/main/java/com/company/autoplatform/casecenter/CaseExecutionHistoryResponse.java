package com.company.autoplatform.casecenter;

public record CaseExecutionHistoryResponse(
        Long id,
        Long caseId,
        String executionStatus,
        String executionComment,
        String executionNote,
        Long executorId,
        String executorName,
        String executedAt
) {
}
