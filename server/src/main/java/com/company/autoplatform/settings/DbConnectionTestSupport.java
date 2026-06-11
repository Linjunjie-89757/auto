package com.company.autoplatform.settings;

import com.company.autoplatform.common.BadRequestException;
import com.company.autoplatform.workspace.WorkspaceEntity;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

@Service
public class DbConnectionTestSupport {

    private final DbConnectionCrypto dbConnectionCrypto;
    private final DbConnectionDomainService dbConnectionDomainService;

    public DbConnectionTestSupport(
            DbConnectionCrypto dbConnectionCrypto,
            DbConnectionDomainService dbConnectionDomainService
    ) {
        this.dbConnectionCrypto = dbConnectionCrypto;
        this.dbConnectionDomainService = dbConnectionDomainService;
    }

    public DbConnectionTestResult testDbConnection(String workspaceCode, DbConnectionTestRequest request) {
        DbConnectionEntity entity = request.id() == null ? new DbConnectionEntity()
                : dbConnectionDomainService.requireReadableDbConnection(
                request.id(),
                workspaceCode,
                "Current workspace cannot test the database connection");
        if (request.id() == null) {
            WorkspaceEntity workspace = dbConnectionDomainService.requireWritableWorkspace(workspaceCode, request.workspaceCode());
            entity.setWorkspaceId(workspace.getId());
        }
        String driverClassName = firstNonBlank(request.driverClassName(), entity.getDriverClassName());
        String jdbcUrl = firstNonBlank(request.jdbcUrl(), entity.getJdbcUrl());
        String username = firstNonBlank(request.username(), entity.getUsername());
        String password = request.password() == null || request.password().isBlank()
                ? dbConnectionCrypto.decrypt(entity.getPasswordEncrypted())
                : request.password();
        Integer timeoutMs = request.timeoutMs() == null || request.timeoutMs() <= 0 ? entity.getTimeoutMs() : request.timeoutMs();
        testConnection(driverClassName, jdbcUrl, username, password, timeoutMs);
        return new DbConnectionTestResult(true, "Connection succeeded");
    }

    private void testConnection(String driverClassName, String jdbcUrl, String username, String password, Integer timeoutMs) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new BadRequestException("JDBC URL cannot be blank");
        }
        if (driverClassName != null && !driverClassName.isBlank()) {
            try {
                Class.forName(driverClassName.trim());
            } catch (ClassNotFoundException exception) {
                throw new BadRequestException("JDBC driver is not available: " + driverClassName);
            }
        }
        int loginTimeoutSeconds = Math.max(1, (timeoutMs == null ? 5000 : timeoutMs) / 1000);
        int previousTimeout = DriverManager.getLoginTimeout();
        DriverManager.setLoginTimeout(loginTimeoutSeconds);
        try (Connection ignored = DriverManager.getConnection(
                jdbcUrl.trim(),
                username == null ? "" : username,
                password == null ? "" : password
        )) {
            // Opening the connection is the test.
        } catch (SQLException exception) {
            throw new BadRequestException("Connection failed: " + exception.getMessage());
        } finally {
            DriverManager.setLoginTimeout(previousTimeout);
        }
    }

    private String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first.trim();
    }
}
