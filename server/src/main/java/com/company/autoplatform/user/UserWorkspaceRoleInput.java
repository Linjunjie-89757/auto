package com.company.autoplatform.user;

import jakarta.validation.constraints.NotBlank;

public record UserWorkspaceRoleInput(
        @NotBlank(message = "空间编码不能为空") String workspaceCode,
        @NotBlank(message = "空间角色不能为空") String roleCode
) {
}
