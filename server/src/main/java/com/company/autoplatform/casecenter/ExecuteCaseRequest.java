package com.company.autoplatform.casecenter;

import jakarta.validation.constraints.NotBlank;

public record ExecuteCaseRequest(
        @NotBlank(message = "执行状态不能为空") String executionStatus,
        String executionComment,
        String executionNote
) {
}
