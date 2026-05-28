package com.company.autoplatform.ai;

import jakarta.validation.constraints.NotBlank;

public record ProbeAiProviderModelRequest(
        @NotBlank(message = "模型名称不能为空") String modelName
) {
}
