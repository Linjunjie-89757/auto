package com.company.autoplatform.casecenter;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BatchDeleteCasesRequest(
        @NotEmpty(message = "用例列表不能为空") List<Long> caseIds
) {
}
