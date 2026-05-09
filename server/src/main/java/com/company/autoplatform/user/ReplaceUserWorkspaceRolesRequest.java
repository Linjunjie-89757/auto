package com.company.autoplatform.user;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ReplaceUserWorkspaceRolesRequest(
        @NotNull(message = "所属空间不能为空") List<String> workspaceCodes
) {
}
