package com.company.autoplatform.workspace;

import jakarta.validation.constraints.NotNull;

public record CreateWorkspaceMemberRequest(
        @NotNull(message = "用户不能为空") Long userId,
        String roleCode
) {
}
