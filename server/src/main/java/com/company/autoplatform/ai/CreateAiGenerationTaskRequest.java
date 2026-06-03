package com.company.autoplatform.ai;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record CreateAiGenerationTaskRequest(
        String workspaceCode,
        @NotBlank(message = "Requirement title is required") String requirementTitle,
        @NotBlank(message = "Requirement content is required") String requirementContent,
        @NotBlank(message = "Output mode is required") String outputMode,
        Long directoryId,
        String directoryName,
        List<Long> assetIds,
        Integer ignoredAssetCount
) {
}
