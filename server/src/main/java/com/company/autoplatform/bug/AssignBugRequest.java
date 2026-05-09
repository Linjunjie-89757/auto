package com.company.autoplatform.bug;

import jakarta.validation.constraints.NotNull;

public record AssignBugRequest(
        String workspaceCode,
        @NotNull(message = "负责人不能为空") Long assigneeId
) {
}
