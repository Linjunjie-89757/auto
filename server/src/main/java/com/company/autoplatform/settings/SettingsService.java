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

    private final EnvConfigMapper envConfigMapper;
    private final ParamSetMapper paramSetMapper;
    private final DbConnectionMapper dbConnectionMapper;
    private final DbConnectionCrypto dbConnectionCrypto;
    private final WorkspaceService workspaceService;

    public SettingsService(
            EnvConfigMapper envConfigMapper,
            ParamSetMapper paramSetMapper,
            DbConnectionMapper dbConnectionMapper,
            DbConnectionCrypto dbConnectionCrypto,
            WorkspaceService workspaceService
    ) {
        this.envConfigMapper = envConfigMapper;
        this.paramSetMapper = paramSetMapper;
        this.dbConnectionMapper = dbConnectionMapper;
        this.dbConnectionCrypto = dbConnectionCrypto;
        this.workspaceService = workspaceService;
    }

    public PageResponse<EnvConfigItem> listEnvs(String workspaceCode) {
        WorkspaceEntity workspace = resolveScopedWorkspace(workspaceCode);
        LambdaQueryWrapper<EnvConfigEntity> query = new LambdaQueryWrapper<>();
        if (workspace != null) {
            query.eq(EnvConfigEntity::getWorkspaceId, workspace.getId());
        } else if (!workspaceService.isPlatformAdmin()) {
            List<Long> workspaceIds = workspaceService.listReadableWorkspaceIds();
            if (workspaceIds.isEmpty()) {
                return new PageResponse<>(List.of(), 0);
            }
            query.in(EnvConfigEntity::getWorkspaceId, workspaceIds);
        }
        var items = envConfigMapper.selectList(query.orderByAsc(EnvConfigEntity::getId)).stream()
                .map(this::toEnvItem)
                .toList();
        return new PageResponse<>(items, items.size());
    }

    public EnvConfigItem createEnv(String headerWorkspaceCode, CreateEnvConfigRequest request) {
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(
                workspaceService.resolveTargetWorkspace(headerWorkspaceCode, request.workspaceCode()));
        EnvConfigEntity entity = new EnvConfigEntity();
        entity.setWorkspaceId(workspace.getId());
        entity.setEnvType(request.envType());
        entity.setEnvName(request.envName());
        entity.setBaseUrl(request.baseUrl());
        entity.setConfigJson(request.configJson());
        entity.setStatus(1);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        envConfigMapper.insert(entity);
        return toEnvItem(entity);
    }

    public EnvConfigItem updateEnv(Long id, String headerWorkspaceCode, CreateEnvConfigRequest request) {
        EnvConfigEntity entity = requireEnv(id);
        validateReadable(entity.getWorkspaceId(), headerWorkspaceCode, "当前空间上下文不可编辑该环境");
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(
                workspaceService.resolveTargetWorkspace(headerWorkspaceCode, request.workspaceCode()));
        if (!entity.getWorkspaceId().equals(workspace.getId())) {
            throw new BadRequestException("不允许修改环境归属空间");
        }
        entity.setEnvType(request.envType());
        entity.setEnvName(request.envName());
        entity.setBaseUrl(request.baseUrl());
        entity.setConfigJson(request.configJson());
        entity.setUpdatedAt(LocalDateTime.now());
        envConfigMapper.updateById(entity);
        return toEnvItem(entity);
    }

    public EnvConfigItem updateEnvStatus(Long id, String workspaceCode, UpdateSettingStatusRequest request) {
        EnvConfigEntity entity = requireEnv(id);
        validateReadable(entity.getWorkspaceId(), workspaceCode, "当前空间上下文不可修改该环境");
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode());
        entity.setStatus(normalizeStatus(request.status()));
        entity.setUpdatedAt(LocalDateTime.now());
        envConfigMapper.updateById(entity);
        return toEnvItem(entity);
    }

    public void deleteEnv(Long id, String workspaceCode) {
        EnvConfigEntity entity = requireEnv(id);
        validateReadable(entity.getWorkspaceId(), workspaceCode, "当前空间上下文不可删除该环境");
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode());
        envConfigMapper.deleteById(id);
    }

    public PageResponse<ParamSetItem> listParams(String workspaceCode) {
        WorkspaceEntity workspace = resolveScopedWorkspace(workspaceCode);
        LambdaQueryWrapper<ParamSetEntity> query = new LambdaQueryWrapper<>();
        if (workspace != null) {
            query.eq(ParamSetEntity::getWorkspaceId, workspace.getId());
        } else if (!workspaceService.isPlatformAdmin()) {
            List<Long> workspaceIds = workspaceService.listReadableWorkspaceIds();
            if (workspaceIds.isEmpty()) {
                return new PageResponse<>(List.of(), 0);
            }
            query.in(ParamSetEntity::getWorkspaceId, workspaceIds);
        }
        var items = paramSetMapper.selectList(query.orderByAsc(ParamSetEntity::getId)).stream()
                .map(this::toParamItem)
                .toList();
        return new PageResponse<>(items, items.size());
    }

    public ParamSetItem createParam(String headerWorkspaceCode, CreateParamSetRequest request) {
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(
                workspaceService.resolveTargetWorkspace(headerWorkspaceCode, request.workspaceCode()));
        ParamSetEntity entity = new ParamSetEntity();
        entity.setWorkspaceId(workspace.getId());
        entity.setParamType(request.paramType());
        entity.setParamName(request.paramName());
        entity.setContentJson(request.contentJson());
        entity.setStatus(1);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        paramSetMapper.insert(entity);
        return toParamItem(entity);
    }

    public ParamSetItem updateParam(Long id, String headerWorkspaceCode, CreateParamSetRequest request) {
        ParamSetEntity entity = requireParam(id);
        validateReadable(entity.getWorkspaceId(), headerWorkspaceCode, "当前空间上下文不可编辑该参数集");
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(
                workspaceService.resolveTargetWorkspace(headerWorkspaceCode, request.workspaceCode()));
        if (!entity.getWorkspaceId().equals(workspace.getId())) {
            throw new BadRequestException("不允许修改参数集归属空间");
        }
        entity.setParamType(request.paramType());
        entity.setParamName(request.paramName());
        entity.setContentJson(request.contentJson());
        entity.setUpdatedAt(LocalDateTime.now());
        paramSetMapper.updateById(entity);
        return toParamItem(entity);
    }

    public ParamSetItem updateParamStatus(Long id, String workspaceCode, UpdateSettingStatusRequest request) {
        ParamSetEntity entity = requireParam(id);
        validateReadable(entity.getWorkspaceId(), workspaceCode, "当前空间上下文不可修改该参数集");
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode());
        entity.setStatus(normalizeStatus(request.status()));
        entity.setUpdatedAt(LocalDateTime.now());
        paramSetMapper.updateById(entity);
        return toParamItem(entity);
    }

    public void deleteParam(Long id, String workspaceCode) {
        ParamSetEntity entity = requireParam(id);
        validateReadable(entity.getWorkspaceId(), workspaceCode, "当前空间上下文不可删除该参数集");
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode());
        paramSetMapper.deleteById(id);
    }

    public PageResponse<DbConnectionItem> listDbConnections(String workspaceCode) {
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

    private EnvConfigEntity requireEnv(Long id) {
        EnvConfigEntity entity = envConfigMapper.selectById(id);
        if (entity == null) {
            throw new NotFoundException("环境不存在");
        }
        return entity;
    }

    private ParamSetEntity requireParam(Long id) {
        ParamSetEntity entity = paramSetMapper.selectById(id);
        if (entity == null) {
            throw new NotFoundException("参数集不存在");
        }
        return entity;
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

    private EnvConfigItem toEnvItem(EnvConfigEntity item) {
        WorkspaceEntity currentWorkspace = workspaceService.requireWorkspaceById(item.getWorkspaceId());
        return new EnvConfigItem(
                item.getId(),
                currentWorkspace.getWorkspaceCode(),
                currentWorkspace.getWorkspaceName(),
                item.getEnvType(),
                item.getEnvName(),
                item.getBaseUrl(),
                item.getConfigJson(),
                item.getStatus()
        );
    }

    private ParamSetItem toParamItem(ParamSetEntity item) {
        WorkspaceEntity currentWorkspace = workspaceService.requireWorkspaceById(item.getWorkspaceId());
        return new ParamSetItem(
                item.getId(),
                currentWorkspace.getWorkspaceCode(),
                currentWorkspace.getWorkspaceName(),
                item.getParamType(),
                item.getParamName(),
                item.getContentJson(),
                item.getStatus()
        );
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
