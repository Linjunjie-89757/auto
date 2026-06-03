package com.company.autoplatform.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ReviewAiGeneratedCasesRequest(
        @NotBlank(message = "Requirement title is required") String requirementTitle,
        @NotBlank(message = "Requirement content is required") String requirementContent,
        String sceneFocus,
        List<String> remainingCoverageGaps,
        @NotEmpty(message = "Generated cases are required") List<AiExistingCaseItem> generatedCases
) {
}
