package com.company.autoplatform.settings;

public record ParamSetItem(
        Long id,
        String workspaceCode,
        String workspaceName,
        String paramType,
        String paramName,
        String contentJson,
        Integer status
) {
}
