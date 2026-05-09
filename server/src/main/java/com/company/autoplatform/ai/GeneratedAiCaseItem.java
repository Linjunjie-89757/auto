package com.company.autoplatform.ai;

import java.util.List;

public record GeneratedAiCaseItem(
        String title,
        String caseType,
        String priority,
        String precondition,
        String steps,
        String expectedResult,
        String riskNotes,
        List<String> warnings
) {
}
