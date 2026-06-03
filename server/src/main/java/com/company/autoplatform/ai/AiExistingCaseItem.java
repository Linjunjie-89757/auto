package com.company.autoplatform.ai;

public record AiExistingCaseItem(
        String title,
        String caseType,
        String priority,
        String precondition,
        String steps,
        String expectedResult,
        String testAngle,
        String generationReason,
        String requirementEvidence
) {
}
