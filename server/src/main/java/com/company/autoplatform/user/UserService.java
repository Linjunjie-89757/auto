package com.company.autoplatform.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.autoplatform.auth.CurrentUserContext;
import com.company.autoplatform.auth.PlatformRole;
import com.company.autoplatform.common.BadRequestException;
import com.company.autoplatform.workspace.WorkspaceEntity;
import com.company.autoplatform.workspace.WorkspaceMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class UserService {

    public static final String DEFAULT_PASSWORD = "zhyt@2025";

    private final UserMapper userMapper;
    private final WorkspaceMapper workspaceMapper;
    private final PasswordEncoder passwordEncoder;
    private final UserDomainService userDomainService;

    public UserService(
            UserMapper userMapper,
            WorkspaceMapper workspaceMapper,
            PasswordEncoder passwordEncoder,
            UserDomainService userDomainService
    ) {
        this.userMapper = userMapper;
        this.workspaceMapper = workspaceMapper;
        this.passwordEncoder = passwordEncoder;
        this.userDomainService = userDomainService;
    }

    public List<UserItem> listUsers() {
        return userDomainService.listUsers();
    }

    public UserItem createUser(CreateUserRequest request) {
        return userDomainService.createUser(request);
    }

    public BatchCreateUserResponse batchCreateUsers(BatchCreateUserRequest request) {
        requirePlatformAdmin();
        List<BatchCreateUserItem> results = new ArrayList<>();
        int successCount = 0;
        List<CreateUserRequest> users = request.users() == null ? List.of() : request.users();
        for (int i = 0; i < users.size(); i++) {
            CreateUserRequest item = users.get(i);
            try {
                UserItem created = createUser(item);
                successCount++;
                results.add(new BatchCreateUserItem(
                        i + 1,
                        safeTrim(item.username()),
                        safeTrim(item.email()),
                        safeTrim(item.displayName()),
                        true,
                        "创建成功",
                        created
                ));
            } catch (RuntimeException ex) {
                results.add(new BatchCreateUserItem(
                        i + 1,
                        item == null ? "" : safeTrim(item.username()),
                        item == null ? "" : safeTrim(item.email()),
                        item == null ? "" : safeTrim(item.displayName()),
                        false,
                        ex.getMessage(),
                        null
                ));
            }
        }
        return new BatchCreateUserResponse(users.size(), successCount, users.size() - successCount, results);
    }

    public UserItem updateUser(Long userId, UpdateUserRequest request) {
        return userDomainService.updateUser(userId, request);
    }

    public UserItem replaceWorkspaceRoles(Long userId, ReplaceUserWorkspaceRolesRequest request) {
        requirePlatformAdmin();
        UserEntity entity = requireAnyUser(userId);
        ensureVisibleTarget(entity);
        ensureAdminMutationAllowed(entity);
        userDomainService.replaceWorkspaceCodes(entity, request.workspaceCodes());
        return userDomainService.toItem(entity, userDomainService.findUserWorkspaces(userId));
    }

    public ResetPasswordResponse resetPassword(Long userId) {
        requirePlatformAdmin();
        UserEntity entity = requireAnyUser(userId);
        ensureVisibleTarget(entity);
        ensureAdminMutationAllowed(entity);
        entity.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        entity.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(entity);
        return new ResetPasswordResponse(entity.getId(), entity.getUsername(), DEFAULT_PASSWORD);
    }

    public void removeAdminFromWorkspace(Long userId, String workspaceCode) {
        requirePlatformAdmin();
        if (!isCurrentSuperAdmin()) {
            throw new BadRequestException("只有超级管理员可以移除管理员");
        }
        UserEntity entity = requireAnyUser(userId);
        ensureVisibleTarget(entity);
        if (!PlatformRole.PLATFORM_ADMIN.equalsIgnoreCase(entity.getRoleCode())) {
            throw new BadRequestException("当前成员不是管理员");
        }

        List<String> remainingWorkspaceCodes = workspaceMapper.selectList(new LambdaQueryWrapper<WorkspaceEntity>()
                        .eq(WorkspaceEntity::getStatus, 1)
                        .orderByAsc(WorkspaceEntity::getId))
                .stream()
                .map(WorkspaceEntity::getWorkspaceCode)
                .filter(code -> !code.equals(workspaceCode))
                .toList();

        entity.setRoleCode(PlatformRole.MEMBER);
        entity.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(entity);
        userDomainService.replaceWorkspaceCodes(entity, remainingWorkspaceCodes);
    }

    public UserEntity requireUser(Long userId) {
        return userDomainService.requireUser(userId);
    }

    public UserEntity findActiveUser(Long userId) {
        return userDomainService.findActiveUser(userId);
    }

    public UserEntity requireAnyUser(Long userId) {
        return userDomainService.requireAnyUser(userId);
    }

    public boolean isPlatformAdmin(Long userId) {
        UserEntity user = findActiveUser(userId);
        return user != null && PlatformRole.isAdminRole(user.getRoleCode());
    }

    public boolean isCurrentPlatformAdmin() {
        return PlatformRole.isAdminRole(CurrentUserContext.require().platformRole());
    }

    public boolean isSuperAdmin(Long userId) {
        UserEntity user = findActiveUser(userId);
        return user != null && PlatformRole.isSuperAdmin(user.getRoleCode());
    }

    public boolean isCurrentSuperAdmin() {
        return PlatformRole.isSuperAdmin(CurrentUserContext.require().platformRole());
    }

    public List<UserEntity> listPlatformAdminUsers() {
        return userMapper.selectList(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getRoleCode, PlatformRole.PLATFORM_ADMIN)
                .eq(UserEntity::getStatus, 1)
                .orderByAsc(UserEntity::getId));
    }

    public void requirePlatformAdmin() {
        if (!isCurrentPlatformAdmin()) {
            throw new BadRequestException("只有管理员可执行该操作");
        }
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isSuperAdminRole(String roleCode) {
        return PlatformRole.isSuperAdmin(roleCode);
    }

    private void ensureVisibleTarget(UserEntity targetUser) {
        if (isSuperAdminRole(targetUser.getRoleCode())) {
            throw new BadRequestException("超级管理员不在成员管理列表中维护");
        }
    }

    private void ensureAdminMutationAllowed(UserEntity targetUser) {
        if (PlatformRole.PLATFORM_ADMIN.equalsIgnoreCase(targetUser.getRoleCode()) && !isCurrentSuperAdmin()) {
            throw new BadRequestException("只有超级管理员可以操作管理员");
        }
    }
}
