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
        String aiSource,
        String reviewComment,
        String optimizationReason,
        String supplementReason,
        String coverageGap,
        GeneratedAiCaseItem originalCaseSnapshot,
        List<String> warnings,
        String aiReviewStatus,
        String aiReviewSummary,
        Boolean manualEdited,
        String manualEditedByName,
        String manualEditedAt
) {
}
