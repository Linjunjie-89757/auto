package com.company.autoplatform.execution;

import jakarta.validation.constraints.NotBlank;

public record TaskTransitionRequest(
        @NotBlank(message = "目标状态不能为空") String toStatus
) {
}
