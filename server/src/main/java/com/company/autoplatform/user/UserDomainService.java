package com.company.autoplatform.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.autoplatform.auth.CurrentUserContext;
import com.company.autoplatform.auth.PlatformRole;
import com.company.autoplatform.common.BadRequestException;
import com.company.autoplatform.workspace.WorkspaceEntity;
import com.company.autoplatform.workspace.WorkspaceMapper;
import com.company.autoplatform.workspace.WorkspaceMemberEntity;
import com.company.autoplatform.workspace.WorkspaceMemberMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class UserDomainService {

    private final UserMapper userMapper;
    private final WorkspaceMemberMapper workspaceMemberMapper;
    private final WorkspaceMapper workspaceMapper;
    private final UserCredentialSupport userCredentialSupport;
    private final UserRoleSupport userRoleSupport;

    public UserDomainService(
            UserMapper userMapper,
            WorkspaceMemberMapper workspaceMemberMapper,
            WorkspaceMapper workspaceMapper,
            UserCredentialSupport userCredentialSupport,
            UserRoleSupport userRoleSupport
    ) {
        this.userMapper = userMapper;
        this.workspaceMemberMapper = workspaceMemberMapper;
        this.workspaceMapper = workspaceMapper;
        this.userCredentialSupport = userCredentialSupport;
        this.userRoleSupport = userRoleSupport;
    }

    public List<UserItem> listUsers() {
        CurrentUserContext.require();
        List<UserEntity> users = userMapper.selectList(new LambdaQueryWrapper<UserEntity>().orderByAsc(UserEntity::getId));
        Map<Long, List<WorkspaceEntity>> workspaceMap = buildUserWorkspaceMap();
        return users.stream()
                .filter(user -> !userRoleSupport.isSuperAdminRole(user.getRoleCode()))
                .map(user -> toItem(user, workspaceMap.getOrDefault(user.getId(), List.of())))
                .toList();
    }

    public UserItem createUser(CreateUserRequest request) {
        userRoleSupport.requirePlatformAdmin();
        String username = request.username().trim();
        String email = request.email().trim();
        if (userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, username)
                .last("limit 1")) != null) {
            throw new BadRequestException("账号已存在");
        }
        validateEmailAvailable(email, null);

        String storedRole = userRoleSupport.normalizeStoredRole(request.roleCode());
        userRoleSupport.requireAssignableRole(storedRole);

        UserEntity entity = new UserEntity();
        entity.setUsername(username);
        entity.setEmail(email);
        entity.setDisplayName(request.displayName().trim());
        entity.setRoleCode(storedRole);
        entity.setPassword(userCredentialSupport.encodeDefaultPassword());
        entity.setStatus(1);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(entity);

        replaceWorkspaceCodes(entity, request.workspaceCodes());
        return toItem(entity, findUserWorkspaces(entity.getId()));
    }

    public UserItem updateUser(Long userId, UpdateUserRequest request) {
        userRoleSupport.requirePlatformAdmin();
        UserEntity entity = requireAnyUser(userId);
        userRoleSupport.ensureVisibleTarget(entity);

        String email = request.email().trim();
        validateEmailAvailable(email, userId);

        String storedRole = userRoleSupport.normalizeStoredRole(request.roleCode());
        userRoleSupport.requireAssignableRole(storedRole);
        userRoleSupport.ensureAdminMutationAllowed(entity);

        entity.setEmail(email);
        entity.setDisplayName(request.displayName().trim());
        entity.setRoleCode(storedRole);
        entity.setStatus(request.status());
        entity.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(entity);

        replaceWorkspaceCodes(entity, request.workspaceCodes() == null ? List.of() : request.workspaceCodes());
        return toItem(entity, findUserWorkspaces(entity.getId()));
    }

    public UserEntity requireUser(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null || user.getStatus() != 1) {
            throw new BadRequestException("用户不存在");
        }
        return user;
    }

    public UserEntity findActiveUser(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null || user.getStatus() != 1) {
            return null;
        }
        return user;
    }

    public UserEntity requireAnyUser(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new BadRequestException("用户不存在");
        }
        return user;
    }

    void replaceWorkspaceCodes(UserEntity user, List<String> requestedWorkspaceCodes) {
        if (userRoleSupport.isStoredAdminRole(user.getRoleCode())) {
            clearUserWorkspaces(user.getId());
            return;
        }

        List<String> normalizedCodes = normalizeWorkspaceCodes(requestedWorkspaceCodes);
        if (normalizedCodes.isEmpty()) {
            clearUserWorkspaces(user.getId());
            return;
        }

        Map<String, WorkspaceMemberEntity> existingMemberships = workspaceMemberMapper.selectList(
                        new LambdaQueryWrapper<WorkspaceMemberEntity>().eq(WorkspaceMemberEntity::getUserId, user.getId()))
                .stream()
                .collect(Collectors.toMap(
                        item -> requireWorkspaceById(item.getWorkspaceId()).getWorkspaceCode(),
                        Function.identity(),
                        (left, right) -> left
                ));

        Set<String> requestedSet = new LinkedHashSet<>(normalizedCodes);
        for (String workspaceCode : requestedSet) {
            WorkspaceEntity workspace = requireWorkspaceByCode(workspaceCode);
            WorkspaceMemberEntity existing = existingMemberships.remove(workspaceCode);
            if (existing == null) {
                WorkspaceMemberEntity entity = new WorkspaceMemberEntity();
                entity.setWorkspaceId(workspace.getId());
                entity.setUserId(user.getId());
                entity.setRoleCode("MEMBER");
                entity.setStatus(1);
                entity.setCreatedAt(LocalDateTime.now());
                entity.setUpdatedAt(LocalDateTime.now());
                workspaceMemberMapper.insert(entity);
            } else {
                existing.setRoleCode("MEMBER");
                existing.setStatus(1);
                existing.setUpdatedAt(LocalDateTime.now());
                workspaceMemberMapper.updateById(existing);
            }
        }

        for (WorkspaceMemberEntity redundant : existingMemberships.values()) {
            workspaceMemberMapper.deleteById(redundant.getId());
        }
    }

    List<WorkspaceEntity> findUserWorkspaces(Long userId) {
        if (userRoleSupport.isPlatformAdmin(userId)) {
            return workspaceMapper.selectList(new LambdaQueryWrapper<WorkspaceEntity>()
                    .eq(WorkspaceEntity::getStatus, 1)
                    .orderByAsc(WorkspaceEntity::getId));
        }
        List<Long> workspaceIds = workspaceMemberMapper.selectList(new LambdaQueryWrapper<WorkspaceMemberEntity>()
                        .eq(WorkspaceMemberEntity::getUserId, userId)
                        .eq(WorkspaceMemberEntity::getStatus, 1)
                        .orderByAsc(WorkspaceMemberEntity::getId))
                .stream()
                .map(WorkspaceMemberEntity::getWorkspaceId)
                .distinct()
                .toList();
        if (workspaceIds.isEmpty()) {
            return List.of();
        }
        return workspaceMapper.selectList(new LambdaQueryWrapper<WorkspaceEntity>()
                .eq(WorkspaceEntity::getStatus, 1)
                .in(WorkspaceEntity::getId, workspaceIds)
                .orderByAsc(WorkspaceEntity::getId));
    }

    UserItem toItem(UserEntity user, List<WorkspaceEntity> workspaces) {
        return new UserItem(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                userRoleSupport.isSuperAdminRole(user.getRoleCode()) ? "SUPER_ADMIN" : (PlatformRole.PLATFORM_ADMIN.equalsIgnoreCase(user.getRoleCode()) ? "ADMIN" : "MEMBER"),
                user.getStatus(),
                workspaces.stream().map(WorkspaceEntity::getWorkspaceCode).toList(),
                workspaces.stream().map(WorkspaceEntity::getWorkspaceName).toList()
        );
    }

    private void clearUserWorkspaces(Long userId) {
        workspaceMemberMapper.delete(new LambdaQueryWrapper<WorkspaceMemberEntity>().eq(WorkspaceMemberEntity::getUserId, userId));
    }

    private List<String> normalizeWorkspaceCodes(List<String> workspaceCodes) {
        if (workspaceCodes == null) {
            return List.of();
        }
        return workspaceCodes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private Map<Long, List<WorkspaceEntity>> buildUserWorkspaceMap() {
        List<WorkspaceEntity> allWorkspaces = workspaceMapper.selectList(new LambdaQueryWrapper<WorkspaceEntity>()
                .eq(WorkspaceEntity::getStatus, 1)
                .orderByAsc(WorkspaceEntity::getId));
        Map<Long, WorkspaceEntity> workspaceById = allWorkspaces.stream()
                .collect(Collectors.toMap(WorkspaceEntity::getId, Function.identity()));
        Map<Long, List<WorkspaceEntity>> result = new java.util.HashMap<>();

        List<UserEntity> admins = userMapper.selectList(new LambdaQueryWrapper<UserEntity>()
                .in(UserEntity::getRoleCode, List.of(PlatformRole.SUPER_ADMIN, PlatformRole.PLATFORM_ADMIN))
                .orderByAsc(UserEntity::getId));
        for (UserEntity admin : admins) {
            result.put(admin.getId(), allWorkspaces);
        }

        List<WorkspaceMemberEntity> memberships = workspaceMemberMapper.selectList(new LambdaQueryWrapper<WorkspaceMemberEntity>()
                .eq(WorkspaceMemberEntity::getStatus, 1)
                .orderByAsc(WorkspaceMemberEntity::getId));
        for (WorkspaceMemberEntity membership : memberships) {
            WorkspaceEntity workspace = workspaceById.get(membership.getWorkspaceId());
            if (workspace == null) {
                continue;
            }
            result.computeIfAbsent(membership.getUserId(), ignored -> new ArrayList<>()).add(workspace);
        }
        return result;
    }

    private WorkspaceEntity requireWorkspaceById(Long workspaceId) {
        WorkspaceEntity workspace = workspaceMapper.selectById(workspaceId);
        if (workspace == null || workspace.getStatus() != 1) {
            throw new BadRequestException("工作空间不存在");
        }
        return workspace;
    }

    private WorkspaceEntity requireWorkspaceByCode(String workspaceCode) {
        WorkspaceEntity workspace = workspaceMapper.selectOne(new LambdaQueryWrapper<WorkspaceEntity>()
                .eq(WorkspaceEntity::getWorkspaceCode, workspaceCode)
                .eq(WorkspaceEntity::getStatus, 1)
                .last("limit 1"));
        if (workspace == null) {
            throw new BadRequestException("工作空间不存在");
        }
        return workspace;
    }

    private void validateEmailAvailable(String email, Long excludeUserId) {
        UserEntity existing = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getEmail, email)
                .last("limit 1"));
        if (existing != null && (excludeUserId == null || !existing.getId().equals(excludeUserId))) {
            throw new BadRequestException("邮箱已存在");
        }
    }

}
