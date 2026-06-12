package com.company.autoplatform.ai;

import jakarta.validation.constraints.NotBlank;

public record SaveAiProviderConnectionRequest(
        String workspaceCode,
        String providerType,
        @NotBlank(message = "AI 杩炴帴鍚嶇О涓嶈兘涓虹┖") String connectionName,
        @NotBlank(message = "AI 鍗忚绫诲瀷涓嶈兘涓虹┖") String protocolType,
        @NotBlank(message = "AI API URL 涓嶈兘涓虹┖") String baseUrl,
        Integer requestTimeoutSeconds,
        String modelName,
        String apiKey,
        Integer status
) {
}
