package com.company.autoplatform.ai;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class AiGenerationTaskResultMergeSupport {

    AiExistingCaseItem toExistingCaseItem(GeneratedAiCaseItem item) {
        return new AiExistingCaseItem(
                item.title(),
                item.caseType(),
                item.priority(),
                item.precondition(),
                item.steps(),
                item.expectedResult(),
                item.testAngle(),
                item.generationReason(),
                item.requirementEvidence()
        );
    }

    GeneratedAiCaseItem applyReviewUpdate(GeneratedAiCaseItem original, AiCaseService.ReviewCaseStreamUpdate update) {
        GeneratedAiCaseItem base = update.optimizedCase() != null && "OPTIMIZED".equals(update.status())
                ? update.optimizedCase()
                : original;
        return new GeneratedAiCaseItem(
                base.title(),
                base.caseType(),
                base.priority(),
                base.precondition(),
                base.steps(),
                base.expectedResult(),
                base.riskNotes(),
                firstNonBlank(base.testAngle(), original.testAngle()),
                firstNonBlank(base.generationReason(), original.generationReason()),
                firstNonBlank(base.requirementEvidence(), original.requirementEvidence()),
                "OPTIMIZED".equals(update.status()) && update.optimizedCase() != null ? "REVIEW_OPTIMIZED" : firstNonBlank(original.aiSource(), "INITIAL"),
                firstNonBlank(update.reviewComment(), update.summary(), base.reviewComment(), original.reviewComment()),
                firstNonBlank(update.optimizationReason(), base.optimizationReason(), original.optimizationReason()),
                firstNonBlank(base.supplementReason(), original.supplementReason()),
                firstNonBlank(update.coverageGap(), base.coverageGap(), original.coverageGap()),
                "OPTIMIZED".equals(update.status()) && update.optimizedCase() != null ? original : original.originalCaseSnapshot(),
                base.warnings() == null ? original.warnings() : base.warnings(),
                update.status(),
                update.summary(),
                original.manualEdited(),
                original.manualEditedByName(),
                original.manualEditedAt()
        );
    }

    GeneratedAiCaseItem withStreamSupplementMetadata(AiCaseService.ReviewCaseStreamUpdate update) {
        GeneratedAiCaseItem item = update.supplementCase();
        return new GeneratedAiCaseItem(
                item.title(),
                item.caseType(),
                item.priority(),
                item.precondition(),
                item.steps(),
                item.expectedResult(),
                item.riskNotes(),
                item.testAngle(),
                item.generationReason(),
                item.requirementEvidence(),
                "REVIEW_SUPPLEMENTED",
                firstNonBlank(item.reviewComment(), update.reviewComment()),
                item.optimizationReason(),
                firstNonBlank(item.supplementReason(), update.supplementReason(), update.summary()),
                firstNonBlank(item.coverageGap(), update.coverageGap()),
                null,
                item.warnings(),
                "SUPPLEMENTED",
                firstNonBlank(item.aiReviewSummary(), update.summary(), update.supplementReason(), update.coverageGap()),
                item.manualEdited(),
                item.manualEditedByName(),
                item.manualEditedAt()
        );
    }

    List<GeneratedAiCaseItem> mergeCompleteReviewResult(List<GeneratedAiCaseItem> generatedCases, AiReviewResult review) {
        List<GeneratedAiCaseItem> finalCases = new ArrayList<>();
        for (GeneratedAiCaseItem item : generatedCases) {
            finalCases.add(withSource(item, "INITIAL"));
        }
        if (review == null) {
            return finalCases;
        }
        for (AiReviewCaseDecision decision : review.caseDecisions() == null ? List.<AiReviewCaseDecision>of() : review.caseDecisions()) {
            if (decision.caseIndex() == null || decision.caseIndex() < 0 || decision.caseIndex() >= finalCases.size()) {
                continue;
            }
            GeneratedAiCaseItem current = finalCases.get(decision.caseIndex());
            GeneratedAiCaseItem next = applyReviewDecision(current, decision);
            finalCases.set(decision.caseIndex(), next);
        }
        for (GeneratedAiCaseItem item : review.supplementCases() == null ? List.<GeneratedAiCaseItem>of() : review.supplementCases()) {
            if (finalCases.size() >= AiCaseService.FINAL_MAX_CASES) {
                break;
            }
            finalCases.add(withSupplementMetadata(item));
        }
        return finalCases;
    }

    private GeneratedAiCaseItem applyReviewDecision(GeneratedAiCaseItem original, AiReviewCaseDecision decision) {
        String status = decision.status() == null ? "CONFIRM_REQUIRED" : decision.status();
        GeneratedAiCaseItem base = "OPTIMIZED".equals(status) && decision.optimizedCase() != null
                ? decision.optimizedCase()
                : original;
        return new GeneratedAiCaseItem(
                base.title(),
                base.caseType(),
                base.priority(),
                base.precondition(),
                base.steps(),
                base.expectedResult(),
                base.riskNotes(),
                firstNonBlank(base.testAngle(), original.testAngle()),
                firstNonBlank(base.generationReason(), original.generationReason()),
                firstNonBlank(base.requirementEvidence(), original.requirementEvidence()),
                "OPTIMIZED".equals(status) && decision.optimizedCase() != null ? "REVIEW_OPTIMIZED" : firstNonBlank(original.aiSource(), "INITIAL"),
                firstNonBlank(decision.reviewComment(), decision.summary(), base.reviewComment(), original.reviewComment()),
                firstNonBlank(decision.optimizationReason(), base.optimizationReason(), original.optimizationReason()),
                firstNonBlank(base.supplementReason(), original.supplementReason()),
                firstNonBlank(decision.coverageGap(), base.coverageGap(), original.coverageGap()),
                "OPTIMIZED".equals(status) && decision.optimizedCase() != null ? original : original.originalCaseSnapshot(),
                base.warnings() == null ? original.warnings() : base.warnings(),
                status,
                decision.summary(),
                original.manualEdited(),
                original.manualEditedByName(),
                original.manualEditedAt()
        );
    }

    private GeneratedAiCaseItem withSource(GeneratedAiCaseItem item, String source) {
        return new GeneratedAiCaseItem(
                item.title(), item.caseType(), item.priority(), item.precondition(), item.steps(), item.expectedResult(),
                item.riskNotes(), item.testAngle(), item.generationReason(), item.requirementEvidence(),
                firstNonBlank(item.aiSource(), source), item.reviewComment(), item.optimizationReason(), item.supplementReason(),
                item.coverageGap(), item.originalCaseSnapshot(), item.warnings(), item.aiReviewStatus(), item.aiReviewSummary(),
                item.manualEdited(), item.manualEditedByName(), item.manualEditedAt()
        );
    }

    private GeneratedAiCaseItem withSupplementMetadata(GeneratedAiCaseItem item) {
        return new GeneratedAiCaseItem(
                item.title(), item.caseType(), item.priority(), item.precondition(), item.steps(), item.expectedResult(),
                item.riskNotes(), item.testAngle(), item.generationReason(), item.requirementEvidence(),
                "REVIEW_SUPPLEMENTED", item.reviewComment(), item.optimizationReason(), item.supplementReason(),
                item.coverageGap(), null, item.warnings(), "SUPPLEMENTED", firstNonBlank(item.aiReviewSummary(), item.supplementReason(), item.coverageGap()),
                item.manualEdited(), item.manualEditedByName(), item.manualEditedAt()
        );
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }
}
