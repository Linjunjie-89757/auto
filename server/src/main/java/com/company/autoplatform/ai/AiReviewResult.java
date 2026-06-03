package com.company.autoplatform.ai;

import java.util.List;

public record AiReviewResult(
        String result,
        String summary,
        List<String> issues,
        List<String> suggestions,
        List<AiReviewCaseDecision> caseDecisions,
        List<GeneratedAiCaseItem> supplementCases,
        List<String> unresolvedCoverageGaps,
        String rawContent,
        boolean structured
) {
}
