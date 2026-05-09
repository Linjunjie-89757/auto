package com.company.autoplatform.ai;

public record ImportRequirementDocumentResponse(
        String fileName,
        String title,
        String content,
        Integer charCount,
        java.util.List<AiRequirementAssetResponse> assets
) {
}
