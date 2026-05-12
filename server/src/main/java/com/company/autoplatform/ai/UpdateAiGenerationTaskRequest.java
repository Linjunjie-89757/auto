package com.company.autoplatform.ai;

import java.util.List;

public record UpdateAiGenerationTaskRequest(
        Long directoryId,
        String directoryName,
        List<GeneratedAiCaseItem> generatedCases,
        List<Integer> adoptedCaseIndexes,
        List<Integer> deletedCaseIndexes,
        Integer savedCaseCount
) {
}
