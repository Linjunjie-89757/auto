package com.company.autoplatform.ai;

import java.util.List;

public record AiGeneratedCasesResult(
        List<GeneratedAiCaseItem> generatedCases,
        String coverageSummary,
        List<String> remainingCoverageGaps,
        List<String> warnings,
        List<AiInvalidCaseItem> invalidCases,
        String rawContent
) {
}
