package com.company.autoplatform.settings;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.autoplatform.common.BadRequestException;
import com.company.autoplatform.common.NotFoundException;
import com.company.autoplatform.common.PageResponse;
import com.company.autoplatform.workspace.WorkspaceEntity;
import com.company.autoplatform.workspace.WorkspaceScope;
import com.company.autoplatform.workspace.WorkspaceService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class DbConnectionDomainService {

    private final DbConnectionMapper dbConnectionMapper;
    private final DbConnectionCrypto dbConnectionCrypto;
    private final WorkspaceService workspaceService;

    public DbConnectionDomainService(
            DbConnectionMapper dbConnectionMapper,
            DbConnectionCrypto dbConnectionCrypto,
            WorkspaceService workspaceService
    ) {
        this.dbConnectionMapper = dbConnectionMapper;
        this.dbConnectionCrypto = dbConnectionCrypto;
        this.workspaceService = workspaceService;
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

    DbConnectionEntity requireReadableDbConnection(Long id, String workspaceCode, String message) {
        DbConnectionEntity entity = requireDbConnection(id);
        validateReadable(entity.getWorkspaceId(), workspaceCode, message);
        return entity;
    }

    WorkspaceEntity requireWritableWorkspace(String headerWorkspaceCode, String requestWorkspaceCode) {
        return workspaceService.requireWritableWorkspace(
                workspaceService.resolveTargetWorkspace(headerWorkspaceCode, requestWorkspaceCode));
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
}
