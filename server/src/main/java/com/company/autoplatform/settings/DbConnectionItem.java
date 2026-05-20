package com.company.autoplatform.settings;

public record DbConnectionItem(
        Long id,
        String workspaceCode,
        String workspaceName,
        String connectionName,
        String dbType,
        String driverClassName,
        String jdbcUrl,
        String username,
        Boolean passwordConfigured,
        Integer poolMax,
        Integer timeoutMs,
        String description,
        Integer status
) {
}
