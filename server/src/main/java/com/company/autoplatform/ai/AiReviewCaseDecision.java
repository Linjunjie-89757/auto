package com.company.autoplatform.ai;

public record AiReviewCaseDecision(
        Integer caseIndex,
        String status,
        String summary,
        String coverageComment,
        String evidenceComment,
        String reviewComment,
        String optimizationReason,
        String coverageGap,
        GeneratedAiCaseItem optimizedCase
) {
}
