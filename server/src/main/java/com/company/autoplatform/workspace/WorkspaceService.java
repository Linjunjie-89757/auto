package com.company.autoplatform.workspace;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.autoplatform.auth.CurrentUserContext;
import com.company.autoplatform.auth.CurrentUserPrincipal;
import com.company.autoplatform.auth.PlatformRole;
import com.company.autoplatform.common.BadRequestException;
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

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_MEMBER = "MEMBER";

    private final WorkspaceMapper workspaceMapper;
    private final WorkspaceMemberMapper workspaceMemberMapper;
    private final UserService userService;
    private final WorkspaceDomainService workspaceDomainService;

    public WorkspaceService(
            WorkspaceMapper workspaceMapper,
            WorkspaceMemberMapper workspaceMemberMapper,
            UserService userService,
            WorkspaceDomainService workspaceDomainService
    ) {
        this.workspaceMapper = workspaceMapper;
        this.workspaceMemberMapper = workspaceMemberMapper;
        this.userService = userService;
        this.workspaceDomainService = workspaceDomainService;
    }

    public List<WorkspaceItem> listAll() {
        return workspaceDomainService.listAll();
    }

    public List<WorkspaceItem> listSwitchable() {
        return workspaceDomainService.listSwitchable();
    }

    public WorkspaceEntity requireWorkspace(String workspaceCode) {
        return workspaceDomainService.requireWorkspace(workspaceCode);
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
        return workspaceDomainService.requireWorkspaceById(workspaceId);
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
        return workspaceDomainService.createWorkspace(request);
    }

    public WorkspaceItem updateWorkspace(String workspaceCode, CreateWorkspaceRequest request) {
        return workspaceDomainService.updateWorkspace(workspaceCode, request);
    }

    public void deleteWorkspace(String workspaceCode) {
        workspaceDomainService.deleteWorkspace(workspaceCode);
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
}
