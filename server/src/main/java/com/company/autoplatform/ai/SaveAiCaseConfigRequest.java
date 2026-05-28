package com.company.autoplatform.ai;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SaveAiCaseConfigRequest(
        String workspaceCode,
        @NotBlank(message = "AI role type is required") String roleType,
        String protocolType,
        String provider,
        @NotBlank(message = "AI model is required") String model,
        @NotBlank(message = "AI base URL is required") String baseUrl,
        String apiKey,
        @NotBlank(message = "Prompt template is required") String promptTemplate,
        String reviewChecklist,
        @NotNull(message = "Temperature is required")
        @DecimalMin(value = "0.0", message = "Temperature must be >= 0")
        @DecimalMax(value = "1.0", message = "Temperature must be <= 1")
        Double temperature,
        @NotNull(message = "Max cases is required")
        @Min(value = 1, message = "Max cases must be >= 1")
        @Max(value = 100, message = "Max cases must be <= 100")
        Integer maxCases,
        Boolean supportsImageInput,
        Integer status
) {
}
