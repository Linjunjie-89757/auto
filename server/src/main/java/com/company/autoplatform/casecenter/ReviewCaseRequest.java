package com.company.autoplatform.casecenter;

import jakarta.validation.constraints.NotBlank;

public record ReviewCaseRequest(
        @NotBlank(message = "评审状态不能为空") String reviewStatus,
        String reviewComment
) {
}
