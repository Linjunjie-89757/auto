package com.company.autoplatform.ai;

import jakarta.validation.constraints.NotBlank;

public record PreviewAiProviderModelsRequest(
        @NotBlank(message = "AI protocol type is required") String protocolType,
        @NotBlank(message = "AI API URL is required") String baseUrl,
        Integer requestTimeoutSeconds,
        @NotBlank(message = "AI API key is required") String apiKey
) {
}
