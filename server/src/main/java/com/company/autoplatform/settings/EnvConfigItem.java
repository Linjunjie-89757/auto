package com.company.autoplatform.settings;

public record EnvConfigItem(
        Long id,
        String workspaceCode,
        String workspaceName,
        String envType,
        String envName,
        String baseUrl,
        String configJson,
        Integer status
) {
}
