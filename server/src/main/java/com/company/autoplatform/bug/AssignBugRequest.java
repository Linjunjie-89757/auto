package com.company.autoplatform.bug;

import jakarta.validation.constraints.NotNull;

public record AssignBugRequest(
        String workspaceCode,
        @NotNull(message = "\u5904\u7406\u4eba\u4e0d\u80fd\u4e3a\u7a7a") Long assigneeId
) {
}
