package com.company.autoplatform.bug;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ReplaceBugCasesRequest(
        @NotNull(message = "关联用例不能为空") List<Long> caseIds
) {
}
