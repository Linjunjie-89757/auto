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
        String testAngle,
        String generationReason,
        String requirementEvidence,
        List<String> warnings,
        String aiReviewStatus,
        String aiReviewSummary,
        Boolean manualEdited,
        String manualEditedByName,
        String manualEditedAt
) {
}
