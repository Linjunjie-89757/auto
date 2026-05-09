package com.company.autoplatform.workspace;

import jakarta.validation.constraints.NotBlank;

public record CreateWorkspaceRequest(
        @NotBlank(message = "空间编码不能为空") String workspaceCode,
        @NotBlank(message = "空间名称不能为空") String workspaceName,
        String description
) {
}
