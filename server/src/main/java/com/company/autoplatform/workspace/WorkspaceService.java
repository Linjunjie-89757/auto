package com.company.autoplatform.workspace;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.autoplatform.auth.CurrentUserContext;
import com.company.autoplatform.auth.CurrentUserPrincipal;
import com.company.autoplatform.auth.PlatformRole;
import com.company.autoplatform.bug.BugMapper;
import com.company.autoplatform.casecenter.CaseMapper;
import com.company.autoplatform.common.BadRequestException;
import com.company.autoplatform.execution.ReportMapper;
import com.company.autoplatform.execution.TaskMapper;
import com.company.autoplatform.settings.EnvConfigMapper;
import com.company.autoplatform.settings.ParamSetMapper;
import com.company.autoplatform.user.UserEntity;
import com.company.autoplatform.user.UserService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class WorkspaceService {

    private static final String WORKSPACE_TYPE_PROJECT = "PROJECT";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_MEMBER = "MEMBER";

    private final WorkspaceMapper workspaceMapper;
    private final WorkspaceMemberMapper workspaceMemberMapper;
    private final UserService userService;
    private final CaseMapper caseMapper;
    private final TaskMapper taskMapper;
    private final ReportMapper reportMapper;
    private final BugMapper bugMapper;
    private final EnvConfigMapper envConfigMapper;
    private final ParamSetMapper paramSetMapper;

    public WorkspaceService(
            WorkspaceMapper workspaceMapper,
            WorkspaceMemberMapper workspaceMemberMapper,
            UserService userService,
            CaseMapper caseMapper,
            TaskMapper taskMapper,
            ReportMapper reportMapper,
            BugMapper bugMapper,
            EnvConfigMapper envConfigMapper,
            ParamSetMapper paramSetMapper
    ) {
        this.workspaceMapper = workspaceMapper;
        this.workspaceMemberMapper = workspaceMemberMapper;
        this.userService = userService;
        this.caseMapper = caseMapper;
        this.taskMapper = taskMapper;
        this.reportMapper = reportMapper;
        this.bugMapper = bugMapper;
        this.envConfigMapper = envConfigMapper;
        this.paramSetMapper = paramSetMapper;
    }

    public List<WorkspaceItem> listAll() {
        return listReadableWorkspaceEntities().stream()
                .map(this::toWorkspaceItem)
                .toList();
    }

    public List<WorkspaceItem> listSwitchable() {
        List<WorkspaceItem> result = new ArrayList<>();
        result.add(new WorkspaceItem(WorkspaceScope.ALL, "全部", "查看当前账号可见的全部空间数据", true,
                null, null, null, 1, null, null));
        result.addAll(listAll());
        return result;
    }

    public WorkspaceEntity requireWorkspace(String workspaceCode) {
        WorkspaceEntity workspace = workspaceMapper.selectOne(new LambdaQueryWrapper<WorkspaceEntity>()
                .eq(WorkspaceEntity::getWorkspaceCode, workspaceCode)
                .eq(WorkspaceEntity::getStatus, 1)
                .last("limit 1"));
        if (workspace == null) {
            throw new BadRequestException("无效的工作空间: " + workspaceCode);
        }
        return workspace;
    }

    public WorkspaceEntity requireReadableWorkspace(String workspaceCode) {
        WorkspaceEntity workspace = requireWorkspace(workspaceCode);
        if (!isPlatformAdmin() && !listReadableWorkspaceIds().contains(workspace.getId())) {
            throw new BadRequestException("当前账号无权访问该工作空间");
        }
        return workspace;
    }

    public WorkspaceEntity requireWritableWorkspace(String workspaceCode) {
        return requireReadableWorkspace(workspaceCode);
    }

    public WorkspaceEntity requireWorkspaceById(Long workspaceId) {
        WorkspaceEntity workspace = workspaceMapper.selectById(workspaceId);
        if (workspace == null || workspace.getStatus() != 1) {
            throw new BadRequestException("无效的工作空间");
        }
        return workspace;
    }

    public String resolveTargetWorkspace(String headerWorkspaceCode, String bodyWorkspaceCode) {
        String normalized = WorkspaceScope.normalize(headerWorkspaceCode);
        if (WorkspaceScope.isAll(normalized)) {
            if (bodyWorkspaceCode == null || bodyWorkspaceCode.isBlank()) {
                throw new BadRequestException("全部视角下必须明确选择目标空间");
            }
            requireWritableWorkspace(bodyWorkspaceCode);
            return bodyWorkspaceCode;
        }
        requireWritableWorkspace(normalized);
        return normalized;
    }

    public WorkspaceItem createWorkspace(CreateWorkspaceRequest request) {
        requirePlatformAdmin();
        String workspaceCode = request.workspaceCode();
        if (workspaceCode == null || workspaceCode.isBlank()) {
            workspaceCode = generateWorkspaceCode();
        }
        else {
            workspaceCode = workspaceCode.trim();
        }
        if (workspaceMapper.selectOne(new LambdaQueryWrapper<WorkspaceEntity>()
                .eq(WorkspaceEntity::getWorkspaceCode, workspaceCode)
                .last("limit 1")) != null) {
            throw new BadRequestException("空间编码已存在");
        }
        WorkspaceEntity entity = new WorkspaceEntity();
        entity.setWorkspaceCode(workspaceCode);
        applyWorkspaceRequest(entity, request, true);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        workspaceMapper.insert(entity);
        ensureOwnerMember(entity);
        return toWorkspaceItem(entity);
    }

    private String generateWorkspaceCode() {
        for (int i = 0; i < 5; i++) {
            String code = "ws_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            if (workspaceMapper.selectOne(new LambdaQueryWrapper<WorkspaceEntity>()
                    .eq(WorkspaceEntity::getWorkspaceCode, code)
                    .last("limit 1")) == null) {
                return code;
            }
        }
        throw new BadRequestException("空间编码生成失败，请稍后重试");
    }

    public WorkspaceItem updateWorkspace(String workspaceCode, CreateWorkspaceRequest request) {
        requirePlatformAdmin();
        WorkspaceEntity entity = requireWorkspace(workspaceCode);
        applyWorkspaceRequest(entity, request, false);
        entity.setUpdatedAt(LocalDateTime.now());
        workspaceMapper.updateById(entity);
        ensureOwnerMember(entity);
        return toWorkspaceItem(entity);
    }

    public void deleteWorkspace(String workspaceCode) {
        requirePlatformAdmin();
        WorkspaceEntity workspace = requireWorkspace(workspaceCode);
        validateWorkspaceDeletable(workspace.getId());
        workspaceMemberMapper.delete(new LambdaQueryWrapper<WorkspaceMemberEntity>()
                .eq(WorkspaceMemberEntity::getWorkspaceId, workspace.getId()));
        workspaceMapper.deleteById(workspace.getId());
    }

    public List<WorkspaceMemberItem> listMembers(String workspaceCode) {
        WorkspaceEntity workspace = requireReadableWorkspace(workspaceCode);
        Map<Long, WorkspaceMemberItem> result = new LinkedHashMap<>();

        List<UserEntity> admins = userService.listPlatformAdminUsers();
        for (UserEntity admin : admins) {
            result.put(admin.getId(), new WorkspaceMemberItem(
                    -admin.getId(),
                    admin.getId(),
                    admin.getUsername(),
                    admin.getEmail(),
                    admin.getDisplayName(),
                    "ADMIN",
                    admin.getStatus()
            ));
        }

        workspaceMemberMapper.selectList(new LambdaQueryWrapper<WorkspaceMemberEntity>()
                        .eq(WorkspaceMemberEntity::getWorkspaceId, workspace.getId())
                        .eq(WorkspaceMemberEntity::getStatus, 1)
                        .orderByAsc(WorkspaceMemberEntity::getId))
                .stream()
                .map(this::toMemberItem)
                .filter(Objects::nonNull)
                .forEach(item -> result.put(item.userId(), item));

        return new ArrayList<>(result.values());
    }

    public WorkspaceMemberItem createMember(String workspaceCode, CreateWorkspaceMemberRequest request) {
        requirePlatformAdmin();
        WorkspaceEntity workspace = requireWorkspace(workspaceCode);
        UserEntity user = userService.requireAnyUser(request.userId());
        if (userService.isPlatformAdmin(user.getId())) {
            throw new BadRequestException("管理员默认拥有全部空间，无需单独加入空间");
        }
        String roleCode = normalizeMemberRole(request.roleCode());

        WorkspaceMemberEntity entity = workspaceMemberMapper.selectOne(new LambdaQueryWrapper<WorkspaceMemberEntity>()
                .eq(WorkspaceMemberEntity::getWorkspaceId, workspace.getId())
                .eq(WorkspaceMemberEntity::getUserId, user.getId())
                .last("limit 1"));
        if (entity == null) {
            entity = new WorkspaceMemberEntity();
            entity.setWorkspaceId(workspace.getId());
            entity.setUserId(user.getId());
            entity.setCreatedAt(LocalDateTime.now());
            entity.setRoleCode(roleCode);
            entity.setStatus(1);
            entity.setUpdatedAt(LocalDateTime.now());
            workspaceMemberMapper.insert(entity);
        } else {
            entity.setRoleCode(roleCode);
            entity.setStatus(1);
            entity.setUpdatedAt(LocalDateTime.now());
            workspaceMemberMapper.updateById(entity);
        }
        return toMemberItem(entity);
    }

    public List<WorkspaceMemberItem> createMembers(String workspaceCode, BatchWorkspaceMemberRequest request) {
        requirePlatformAdmin();
        List<WorkspaceMemberItem> result = new ArrayList<>();
        for (Long userId : request.userIds()) {
            result.add(createMember(workspaceCode, new CreateWorkspaceMemberRequest(userId, request.roleCode())));
        }
        return result;
    }

    public WorkspaceMemberItem updateMember(String workspaceCode, Long memberId, UpdateWorkspaceMemberRequest request) {
        requirePlatformAdmin();
        WorkspaceEntity workspace = requireWorkspace(workspaceCode);
        WorkspaceMemberEntity entity = requireMember(memberId);
        if (!entity.getWorkspaceId().equals(workspace.getId())) {
            throw new BadRequestException("成员不属于当前工作空间");
        }
        if (Objects.equals(workspace.getOwnerUserId(), entity.getUserId()) && !ROLE_ADMIN.equals(normalizeMemberRole(request.roleCode()))) {
            throw new BadRequestException("负责人不能降级为普通成员，请先转让负责人");
        }
        entity.setRoleCode(normalizeMemberRole(request.roleCode()));
        entity.setStatus(1);
        entity.setUpdatedAt(LocalDateTime.now());
        workspaceMemberMapper.updateById(entity);
        return toMemberItem(entity);
    }

    public void deleteMember(String workspaceCode, Long memberId) {
        requirePlatformAdmin();
        if (memberId < 0) {
            userService.removeAdminFromWorkspace(-memberId, workspaceCode);
            return;
        }
        WorkspaceEntity workspace = requireWorkspace(workspaceCode);
        WorkspaceMemberEntity entity = requireMember(memberId);
        if (!entity.getWorkspaceId().equals(workspace.getId())) {
            throw new BadRequestException("成员不属于当前工作空间");
        }
        if (Objects.equals(workspace.getOwnerUserId(), entity.getUserId())) {
            throw new BadRequestException("负责人不能移除，请先转让负责人");
        }
        workspaceMemberMapper.deleteById(memberId);
    }

    public List<WorkspaceEntity> listReadableWorkspaceEntities() {
        if (isPlatformAdmin()) {
            return workspaceMapper.selectList(new LambdaQueryWrapper<WorkspaceEntity>()
                    .eq(WorkspaceEntity::getStatus, 1)
                    .orderByAsc(WorkspaceEntity::getId));
        }
        Set<Long> workspaceIds = new LinkedHashSet<>(listReadableWorkspaceIds());
        if (workspaceIds.isEmpty()) {
            return List.of();
        }
        return workspaceMapper.selectList(new LambdaQueryWrapper<WorkspaceEntity>()
                .eq(WorkspaceEntity::getStatus, 1)
                .in(WorkspaceEntity::getId, workspaceIds)
                .orderByAsc(WorkspaceEntity::getId));
    }

    public List<Long> listReadableWorkspaceIds() {
        CurrentUserPrincipal currentUser = CurrentUserContext.require();
        if (PlatformRole.isAdminRole(currentUser.platformRole())) {
            return workspaceMapper.selectList(new LambdaQueryWrapper<WorkspaceEntity>()
                            .eq(WorkspaceEntity::getStatus, 1)
                            .orderByAsc(WorkspaceEntity::getId))
                    .stream()
                    .map(WorkspaceEntity::getId)
                    .toList();
        }
        return workspaceMemberMapper.selectList(new LambdaQueryWrapper<WorkspaceMemberEntity>()
                        .eq(WorkspaceMemberEntity::getUserId, currentUser.userId())
                        .eq(WorkspaceMemberEntity::getStatus, 1)
                        .orderByAsc(WorkspaceMemberEntity::getId))
                .stream()
                .map(WorkspaceMemberEntity::getWorkspaceId)
                .distinct()
                .toList();
    }

    public List<String> listReadableWorkspaceCodes() {
        return listReadableWorkspaceEntities().stream()
                .map(WorkspaceEntity::getWorkspaceCode)
                .toList();
    }

    public boolean isSuperAdmin() {
        return PlatformRole.isSuperAdmin(CurrentUserContext.require().platformRole());
    }

    public boolean isPlatformAdmin() {
        return PlatformRole.isAdminRole(CurrentUserContext.require().platformRole());
    }

    public void requirePlatformAdmin() {
        if (!isPlatformAdmin()) {
            throw new BadRequestException("只有管理员可执行该操作");
        }
    }

    private WorkspaceMemberEntity requireMember(Long memberId) {
        WorkspaceMemberEntity entity = workspaceMemberMapper.selectById(memberId);
        if (entity == null || entity.getStatus() != 1) {
            throw new BadRequestException("成员不存在");
        }
        return entity;
    }

    private void applyWorkspaceRequest(WorkspaceEntity entity, CreateWorkspaceRequest request, boolean creating) {
        entity.setWorkspaceName(request.workspaceName().trim());
        entity.setDescription(request.description() == null ? "" : request.description().trim());
        entity.setWorkspaceType(normalizeWorkspaceType(request.workspaceType()));
        if (request.ownerUserId() != null) {
            userService.requireAnyUser(request.ownerUserId());
        }
        entity.setOwnerUserId(request.ownerUserId());
        entity.setStatus(normalizeWorkspaceStatus(request.status()));
        if (creating && entity.getStatus() == null) {
            entity.setStatus(1);
        }
    }

    private Integer normalizeWorkspaceStatus(Integer status) {
        if (status == null) {
            return 1;
        }
        if (status != 0 && status != 1) {
            throw new BadRequestException("无效的空间状态");
        }
        return status;
    }

    private String normalizeWorkspaceType(String workspaceType) {
        if (workspaceType == null || workspaceType.isBlank()) {
            return WORKSPACE_TYPE_PROJECT;
        }
        String normalized = workspaceType.trim().toUpperCase();
        if (!List.of("PROJECT", "TEAM", "PRODUCT").contains(normalized)) {
            throw new BadRequestException("无效的空间类型");
        }
        return normalized;
    }

    private String normalizeMemberRole(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return ROLE_MEMBER;
        }
        String normalized = roleCode.trim().toUpperCase();
        if (!List.of(ROLE_ADMIN, ROLE_MEMBER).contains(normalized)) {
            throw new BadRequestException("无效的成员角色");
        }
        return normalized;
    }

    private void ensureOwnerMember(WorkspaceEntity workspace) {
        if (workspace.getOwnerUserId() == null) {
            return;
        }
        UserEntity owner = userService.requireAnyUser(workspace.getOwnerUserId());
        if (userService.isPlatformAdmin(owner.getId())) {
            return;
        }
        WorkspaceMemberEntity entity = workspaceMemberMapper.selectOne(new LambdaQueryWrapper<WorkspaceMemberEntity>()
                .eq(WorkspaceMemberEntity::getWorkspaceId, workspace.getId())
                .eq(WorkspaceMemberEntity::getUserId, owner.getId())
                .last("limit 1"));
        if (entity == null) {
            entity = new WorkspaceMemberEntity();
            entity.setWorkspaceId(workspace.getId());
            entity.setUserId(owner.getId());
            entity.setRoleCode(ROLE_ADMIN);
            entity.setStatus(1);
            entity.setCreatedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());
            workspaceMemberMapper.insert(entity);
            return;
        }
        entity.setRoleCode(ROLE_ADMIN);
        entity.setStatus(1);
        entity.setUpdatedAt(LocalDateTime.now());
        workspaceMemberMapper.updateById(entity);
    }

    private WorkspaceItem toWorkspaceItem(WorkspaceEntity entity) {
        String ownerName = null;
        if (entity.getOwnerUserId() != null) {
            UserEntity owner = userService.findActiveUser(entity.getOwnerUserId());
            ownerName = owner == null ? null : owner.getDisplayName();
        }
        return new WorkspaceItem(
                entity.getWorkspaceCode(),
                entity.getWorkspaceName(),
                entity.getDescription(),
                false,
                entity.getWorkspaceType() == null ? WORKSPACE_TYPE_PROJECT : entity.getWorkspaceType(),
                entity.getOwnerUserId(),
                ownerName,
                entity.getStatus(),
                entity.getCreatedAt() == null ? null : entity.getCreatedAt().toString(),
                entity.getUpdatedAt() == null ? null : entity.getUpdatedAt().toString()
        );
    }

    private WorkspaceMemberItem toMemberItem(WorkspaceMemberEntity entity) {
        UserEntity user = userService.findActiveUser(entity.getUserId());
        if (user == null || userService.isSuperAdmin(user.getId())) {
            return null;
        }
        return new WorkspaceMemberItem(
                entity.getId(),
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                userService.isPlatformAdmin(user.getId()) ? ROLE_ADMIN : normalizeMemberRole(entity.getRoleCode()),
                entity.getStatus()
        );
    }

    private void validateWorkspaceDeletable(Long workspaceId) {
        boolean hasDependencies =
                workspaceMemberMapper.selectCount(new LambdaQueryWrapper<WorkspaceMemberEntity>()
                        .eq(WorkspaceMemberEntity::getWorkspaceId, workspaceId)) > 0
                        || caseMapper.selectCount(new LambdaQueryWrapper<com.company.autoplatform.casecenter.CaseEntity>()
                        .eq(com.company.autoplatform.casecenter.CaseEntity::getWorkspaceId, workspaceId)) > 0
                        || taskMapper.selectCount(new LambdaQueryWrapper<com.company.autoplatform.execution.TaskEntity>()
                        .eq(com.company.autoplatform.execution.TaskEntity::getWorkspaceId, workspaceId)) > 0
                        || reportMapper.selectCount(new LambdaQueryWrapper<com.company.autoplatform.execution.ReportEntity>()
                        .eq(com.company.autoplatform.execution.ReportEntity::getWorkspaceId, workspaceId)) > 0
                        || bugMapper.selectCount(new LambdaQueryWrapper<com.company.autoplatform.bug.BugEntity>()
                        .eq(com.company.autoplatform.bug.BugEntity::getWorkspaceId, workspaceId)) > 0
                        || envConfigMapper.selectCount(new LambdaQueryWrapper<com.company.autoplatform.settings.EnvConfigEntity>()
                        .eq(com.company.autoplatform.settings.EnvConfigEntity::getWorkspaceId, workspaceId)) > 0
                        || paramSetMapper.selectCount(new LambdaQueryWrapper<com.company.autoplatform.settings.ParamSetEntity>()
                        .eq(com.company.autoplatform.settings.ParamSetEntity::getWorkspaceId, workspaceId)) > 0;
        if (hasDependencies) {
            throw new BadRequestException("当前工作空间存在关联数据，不能删除");
        }
    }
}
