package com.company.autoplatform.settings;

import com.company.autoplatform.common.BadRequestException;
import com.company.autoplatform.common.PageResponse;
import com.company.autoplatform.workspace.WorkspaceEntity;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

@Service
public class SettingsService {

    private final DbConnectionCrypto dbConnectionCrypto;
    private final DbConnectionDomainService dbConnectionDomainService;
    private final SettingsEnvironmentDomainService environmentDomainService;
    private final SettingsParamSetDomainService paramSetDomainService;

    public SettingsService(
            DbConnectionCrypto dbConnectionCrypto,
            DbConnectionDomainService dbConnectionDomainService,
            SettingsEnvironmentDomainService environmentDomainService,
            SettingsParamSetDomainService paramSetDomainService
    ) {
        this.dbConnectionCrypto = dbConnectionCrypto;
        this.dbConnectionDomainService = dbConnectionDomainService;
        this.environmentDomainService = environmentDomainService;
        this.paramSetDomainService = paramSetDomainService;
    }

    public PageResponse<EnvConfigItem> listEnvs(String workspaceCode, String keyword, String envType, Integer status) {
        return environmentDomainService.listEnvs(workspaceCode, keyword, envType, status);
    }

    public EnvConfigItem createEnv(String headerWorkspaceCode, CreateEnvConfigRequest request) {
        return environmentDomainService.createEnv(headerWorkspaceCode, request);
    }

    public EnvConfigItem updateEnv(Long id, String headerWorkspaceCode, CreateEnvConfigRequest request) {
        return environmentDomainService.updateEnv(id, headerWorkspaceCode, request);
    }

    public EnvConfigItem updateEnvStatus(Long id, String workspaceCode, UpdateSettingStatusRequest request) {
        return environmentDomainService.updateEnvStatus(id, workspaceCode, request);
    }

    public void deleteEnv(Long id, String workspaceCode) {
        environmentDomainService.deleteEnv(id, workspaceCode);
    }

    public PageResponse<ParamSetItem> listParams(String workspaceCode, String keyword, String paramType, Integer status) {
        return paramSetDomainService.listParams(workspaceCode, keyword, paramType, status);
    }

    public ParamSetItem createParam(String headerWorkspaceCode, CreateParamSetRequest request) {
        return paramSetDomainService.createParam(headerWorkspaceCode, request);
    }

    public ParamSetItem updateParam(Long id, String headerWorkspaceCode, CreateParamSetRequest request) {
        return paramSetDomainService.updateParam(id, headerWorkspaceCode, request);
    }

    public ParamSetItem updateParamStatus(Long id, String workspaceCode, UpdateSettingStatusRequest request) {
        return paramSetDomainService.updateParamStatus(id, workspaceCode, request);
    }

    public void deleteParam(Long id, String workspaceCode) {
        paramSetDomainService.deleteParam(id, workspaceCode);
    }

    public PageResponse<DbConnectionItem> listDbConnections(String workspaceCode, String keyword, String dbType, Integer status) {
        return dbConnectionDomainService.listDbConnections(workspaceCode, keyword, dbType, status);
    }

    public DbConnectionItem createDbConnection(String headerWorkspaceCode, DbConnectionRequest request) {
        return dbConnectionDomainService.createDbConnection(headerWorkspaceCode, request);
    }

    public DbConnectionItem updateDbConnection(Long id, String headerWorkspaceCode, DbConnectionRequest request) {
        return dbConnectionDomainService.updateDbConnection(id, headerWorkspaceCode, request);
    }

    public DbConnectionItem updateDbConnectionStatus(Long id, String workspaceCode, UpdateSettingStatusRequest request) {
        return dbConnectionDomainService.updateDbConnectionStatus(id, workspaceCode, request);
    }

    public void deleteDbConnection(Long id, String workspaceCode) {
        dbConnectionDomainService.deleteDbConnection(id, workspaceCode);
    }

    public DbConnectionTestResult testDbConnection(String workspaceCode, DbConnectionTestRequest request) {
        DbConnectionEntity entity = request.id() == null ? new DbConnectionEntity() : dbConnectionDomainService.requireReadableDbConnection(request.id(), workspaceCode, "Current workspace cannot test the database connection");
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
