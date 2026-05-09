package com.company.autoplatform.workspace;

import jakarta.validation.constraints.NotBlank;

public record UpdateWorkspaceMemberRequest(
        @NotBlank(message = "角色不能为空") String roleCode
) {
}
