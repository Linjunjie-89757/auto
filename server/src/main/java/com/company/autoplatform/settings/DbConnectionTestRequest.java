package com.company.autoplatform.settings;

public record DbConnectionTestRequest(
        Long id,
        String workspaceCode,
        String connectionName,
        String dbType,
        String driverClassName,
        String jdbcUrl,
        String username,
        String password,
        Integer timeoutMs
) {
}
