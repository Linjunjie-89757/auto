package com.company.autoplatform.settings;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.autoplatform.common.BadRequestException;
import com.company.autoplatform.common.NotFoundException;
import com.company.autoplatform.common.PageResponse;
import com.company.autoplatform.workspace.WorkspaceEntity;
import com.company.autoplatform.workspace.WorkspaceScope;
import com.company.autoplatform.workspace.WorkspaceService;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class SettingsService {

    private final DbConnectionMapper dbConnectionMapper;
    private final DbConnectionCrypto dbConnectionCrypto;
    private final WorkspaceService workspaceService;
    private final SettingsEnvironmentDomainService environmentDomainService;
    private final SettingsParamSetDomainService paramSetDomainService;

    public SettingsService(
            DbConnectionMapper dbConnectionMapper,
            DbConnectionCrypto dbConnectionCrypto,
            WorkspaceService workspaceService,
            SettingsEnvironmentDomainService environmentDomainService,
            SettingsParamSetDomainService paramSetDomainService
    ) {
        this.dbConnectionMapper = dbConnectionMapper;
        this.dbConnectionCrypto = dbConnectionCrypto;
        this.workspaceService = workspaceService;
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
        WorkspaceEntity workspace = resolveScopedWorkspace(workspaceCode);
        LambdaQueryWrapper<DbConnectionEntity> query = new LambdaQueryWrapper<>();
        if (workspace != null) {
            query.eq(DbConnectionEntity::getWorkspaceId, workspace.getId());
        } else if (!workspaceService.isPlatformAdmin()) {
            List<Long> workspaceIds = workspaceService.listReadableWorkspaceIds();
            if (workspaceIds.isEmpty()) {
                return new PageResponse<>(List.of(), 0);
            }
            query.in(DbConnectionEntity::getWorkspaceId, workspaceIds);
        }
        String trimmedKeyword = blankToNull(keyword);
        if (trimmedKeyword != null) {
            query.and(wrapper -> wrapper
                    .like(DbConnectionEntity::getConnectionName, trimmedKeyword)
                    .or()
                    .like(DbConnectionEntity::getJdbcUrl, trimmedKeyword)
                    .or()
                    .like(DbConnectionEntity::getUsername, trimmedKeyword)
                    .or()
                    .like(DbConnectionEntity::getDescription, trimmedKeyword));
        }
        String trimmedDbType = blankToNull(dbType);
        if (trimmedDbType != null) {
            query.eq(DbConnectionEntity::getDbType, trimmedDbType.trim().toUpperCase(Locale.ROOT));
        }
        if (status != null) {
            query.eq(DbConnectionEntity::getStatus, normalizeStatus(status));
        }
        var items = dbConnectionMapper.selectList(query.orderByAsc(DbConnectionEntity::getId)).stream()
                .map(this::toDbConnectionItem)
                .toList();
        return new PageResponse<>(items, items.size());
    }

    public DbConnectionItem createDbConnection(String headerWorkspaceCode, DbConnectionRequest request) {
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(
                workspaceService.resolveTargetWorkspace(headerWorkspaceCode, request.workspaceCode()));
        DbConnectionEntity entity = new DbConnectionEntity();
        entity.setWorkspaceId(workspace.getId());
        fillDbConnection(entity, request, false);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        dbConnectionMapper.insert(entity);
        return toDbConnectionItem(entity);
    }

    public DbConnectionItem updateDbConnection(Long id, String headerWorkspaceCode, DbConnectionRequest request) {
        DbConnectionEntity entity = requireDbConnection(id);
        validateReadable(entity.getWorkspaceId(), headerWorkspaceCode, "Current workspace cannot edit the database connection");
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(
                workspaceService.resolveTargetWorkspace(headerWorkspaceCode, request.workspaceCode()));
        if (!entity.getWorkspaceId().equals(workspace.getId())) {
            throw new BadRequestException("Cannot move database connection to another workspace");
        }
        fillDbConnection(entity, request, true);
        entity.setUpdatedAt(LocalDateTime.now());
        dbConnectionMapper.updateById(entity);
        return toDbConnectionItem(entity);
    }

    public DbConnectionItem updateDbConnectionStatus(Long id, String workspaceCode, UpdateSettingStatusRequest request) {
        DbConnectionEntity entity = requireDbConnection(id);
        validateReadable(entity.getWorkspaceId(), workspaceCode, "Current workspace cannot update the database connection");
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode());
        entity.setStatus(normalizeStatus(request.status()));
        entity.setUpdatedAt(LocalDateTime.now());
        dbConnectionMapper.updateById(entity);
        return toDbConnectionItem(entity);
    }

    public void deleteDbConnection(Long id, String workspaceCode) {
        DbConnectionEntity entity = requireDbConnection(id);
        validateReadable(entity.getWorkspaceId(), workspaceCode, "Current workspace cannot delete the database connection");
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode());
        dbConnectionMapper.deleteById(id);
    }

    public DbConnectionTestResult testDbConnection(String workspaceCode, DbConnectionTestRequest request) {
        DbConnectionEntity entity = request.id() == null ? new DbConnectionEntity() : requireDbConnection(request.id());
        if (request.id() != null) {
            validateReadable(entity.getWorkspaceId(), workspaceCode, "Current workspace cannot test the database connection");
        } else {
            WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(
                    workspaceService.resolveTargetWorkspace(workspaceCode, request.workspaceCode()));
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

    private WorkspaceEntity resolveScopedWorkspace(String workspaceCode) {
        String normalized = WorkspaceScope.normalize(workspaceCode);
        return WorkspaceScope.isAll(normalized) ? null : workspaceService.requireReadableWorkspace(normalized);
    }

    private void fillDbConnection(DbConnectionEntity entity, DbConnectionRequest request, boolean keepOldPassword) {
        String dbType = request.dbType() == null ? "" : request.dbType().trim().toUpperCase(Locale.ROOT);
        if (!"MYSQL".equals(dbType) && !"H2".equals(dbType)) {
            throw new BadRequestException("DB type must be MYSQL or H2");
        }
        entity.setConnectionName(request.connectionName().trim());
        entity.setDbType(dbType);
        entity.setDriverClassName(blankToNull(request.driverClassName()));
        entity.setJdbcUrl(request.jdbcUrl().trim());
        entity.setUsername(blankToNull(request.username()));
        if (request.password() != null && !request.password().isBlank()) {
            entity.setPasswordEncrypted(dbConnectionCrypto.encrypt(request.password()));
        } else if (!keepOldPassword) {
            entity.setPasswordEncrypted(null);
        }
        entity.setPoolMax(request.poolMax() == null || request.poolMax() <= 0 ? 10 : request.poolMax());
        entity.setTimeoutMs(request.timeoutMs() == null || request.timeoutMs() <= 0 ? 5000 : request.timeoutMs());
        entity.setDescription(blankToNull(request.description()));
        entity.setStatus(request.status() == null ? 1 : normalizeStatus(request.status()));
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





    private DbConnectionEntity requireDbConnection(Long id) {
        DbConnectionEntity entity = dbConnectionMapper.selectById(id);
        if (entity == null) {
            throw new NotFoundException("Database connection not found");
        }
        return entity;
    }

    private void validateReadable(Long workspaceId, String workspaceCode, String message) {
        WorkspaceEntity workspace = resolveScopedWorkspace(workspaceCode);
        if (workspace != null && !workspace.getId().equals(workspaceId)) {
            throw new BadRequestException(message);
        }
        if (workspace == null && !workspaceService.isPlatformAdmin()
                && !workspaceService.listReadableWorkspaceIds().contains(workspaceId)) {
            throw new BadRequestException(message);
        }
    }

    private Integer normalizeStatus(Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BadRequestException("状态只能是 0 或 1");
        }
        return status;
    }





    private DbConnectionItem toDbConnectionItem(DbConnectionEntity item) {
        WorkspaceEntity currentWorkspace = workspaceService.requireWorkspaceById(item.getWorkspaceId());
        return new DbConnectionItem(
                item.getId(),
                currentWorkspace.getWorkspaceCode(),
                currentWorkspace.getWorkspaceName(),
                item.getConnectionName(),
                item.getDbType(),
                item.getDriverClassName(),
                item.getJdbcUrl(),
                item.getUsername(),
                item.getPasswordEncrypted() != null && !item.getPasswordEncrypted().isBlank(),
                item.getPoolMax(),
                item.getTimeoutMs(),
                item.getDescription(),
                item.getStatus()
        );
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first.trim();
    }
}
