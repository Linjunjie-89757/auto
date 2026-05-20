package com.company.autoplatform.settings;

import jakarta.validation.constraints.NotBlank;

public record DbConnectionRequest(
        String workspaceCode,
        @NotBlank(message = "Connection name cannot be blank") String connectionName,
        @NotBlank(message = "DB type cannot be blank") String dbType,
        String driverClassName,
        @NotBlank(message = "JDBC URL cannot be blank") String jdbcUrl,
        String username,
        String password,
        Integer poolMax,
        Integer timeoutMs,
        String description,
        Integer status
) {
}
