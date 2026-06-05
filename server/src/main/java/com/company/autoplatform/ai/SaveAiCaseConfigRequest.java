package com.company.autoplatform.ai;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SaveAiCaseConfigRequest(
        String workspaceCode,
        @NotBlank(message = "AI role type is required") String roleType,
        Long providerConnectionId,
        String protocolType,
        String provider,
        @NotBlank(message = "AI model is required") String model,
        String baseUrl,
        String apiKey,
        @NotBlank(message = "Prompt template is required") String promptTemplate,
        String reviewChecklist,
        @NotNull(message = "Temperature is required")
        @DecimalMin(value = "0.0", message = "Temperature must be >= 0")
        @DecimalMax(value = "1.0", message = "Temperature must be <= 1")
        Double temperature,
        @DecimalMin(value = "0.1", message = "Top-p must be >= 0.1")
        @DecimalMax(value = "1.0", message = "Top-p must be <= 1")
        Double topP,
        Integer maxCases,
        AiCapabilityOverride capabilityOverride,
        Boolean supportsImageInput,
        Integer status
) {
}
