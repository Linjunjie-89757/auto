package com.company.autoplatform.ai;

import java.util.List;

public record AiGeneratedCasesResult(
        List<GeneratedAiCaseItem> generatedCases,
        List<String> warnings,
        List<AiInvalidCaseItem> invalidCases,
        String rawContent
) {
}
