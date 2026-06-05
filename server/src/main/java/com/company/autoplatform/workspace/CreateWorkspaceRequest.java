package com.company.autoplatform.workspace;

import jakarta.validation.constraints.NotBlank;

public record CreateWorkspaceRequest(
        String workspaceCode,
        @NotBlank(message = "空间名称不能为空") String workspaceName,
        String description,
        String workspaceType,
        Long ownerUserId,
        Integer status
) {
}
