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

@Service
public class WorkspaceService {

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
                .map(item -> new WorkspaceItem(item.getWorkspaceCode(), item.getWorkspaceName(), item.getDescription(), false))
                .toList();
    }

    public List<WorkspaceItem> listSwitchable() {
        List<WorkspaceItem> result = new ArrayList<>();
        result.add(new WorkspaceItem(WorkspaceScope.ALL, "全部", "查看当前账号可见的全部空间数据", true));
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
        if (workspaceMapper.selectOne(new LambdaQueryWrapper<WorkspaceEntity>()
                .eq(WorkspaceEntity::getWorkspaceCode, request.workspaceCode())
                .last("limit 1")) != null) {
            throw new BadRequestException("空间编码已存在");
        }
        WorkspaceEntity entity = new WorkspaceEntity();
        entity.setWorkspaceCode(request.workspaceCode());
        entity.setWorkspaceName(request.workspaceName());
        entity.setDescription(request.description());
        entity.setStatus(1);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        workspaceMapper.insert(entity);
        return new WorkspaceItem(entity.getWorkspaceCode(), entity.getWorkspaceName(), entity.getDescription(), false);
    }

    public WorkspaceItem updateWorkspace(String workspaceCode, CreateWorkspaceRequest request) {
        requirePlatformAdmin();
        WorkspaceEntity entity = requireWorkspace(workspaceCode);
        entity.setWorkspaceName(request.workspaceName());
        entity.setDescription(request.description());
        entity.setUpdatedAt(LocalDateTime.now());
        workspaceMapper.updateById(entity);
        return new WorkspaceItem(entity.getWorkspaceCode(), entity.getWorkspaceName(), entity.getDescription(), false);
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

        WorkspaceMemberEntity entity = workspaceMemberMapper.selectOne(new LambdaQueryWrapper<WorkspaceMemberEntity>()
                .eq(WorkspaceMemberEntity::getWorkspaceId, workspace.getId())
                .eq(WorkspaceMemberEntity::getUserId, user.getId())
                .last("limit 1"));
        if (entity == null) {
            entity = new WorkspaceMemberEntity();
            entity.setWorkspaceId(workspace.getId());
            entity.setUserId(user.getId());
            entity.setCreatedAt(LocalDateTime.now());
            entity.setRoleCode("MEMBER");
            entity.setStatus(1);
            entity.setUpdatedAt(LocalDateTime.now());
            workspaceMemberMapper.insert(entity);
        } else {
            entity.setRoleCode("MEMBER");
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
            result.add(createMember(workspaceCode, new CreateWorkspaceMemberRequest(userId)));
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
        entity.setRoleCode("MEMBER");
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
                userService.isPlatformAdmin(user.getId()) ? "ADMIN" : "MEMBER",
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
