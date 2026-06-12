package com.company.autoplatform.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.autoplatform.auth.PlatformRole;
import com.company.autoplatform.common.BadRequestException;
import com.company.autoplatform.workspace.WorkspaceEntity;
import com.company.autoplatform.workspace.WorkspaceMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class UserService {

    public static final String DEFAULT_PASSWORD = "zhyt@2025";

    private final UserMapper userMapper;
    private final WorkspaceMapper workspaceMapper;
    private final UserDomainService userDomainService;
    private final UserCredentialSupport userCredentialSupport;
    private final UserRoleSupport userRoleSupport;

    public UserService(
            UserMapper userMapper,
            WorkspaceMapper workspaceMapper,
            UserDomainService userDomainService,
            UserCredentialSupport userCredentialSupport,
            UserRoleSupport userRoleSupport
    ) {
        this.userMapper = userMapper;
        this.workspaceMapper = workspaceMapper;
        this.userDomainService = userDomainService;
        this.userCredentialSupport = userCredentialSupport;
        this.userRoleSupport = userRoleSupport;
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
        userRoleSupport.ensureVisibleTarget(entity);
        userRoleSupport.ensureAdminMutationAllowed(entity);
        userDomainService.replaceWorkspaceCodes(entity, request.workspaceCodes());
        return userDomainService.toItem(entity, userDomainService.findUserWorkspaces(userId));
    }

    public ResetPasswordResponse resetPassword(Long userId) {
        requirePlatformAdmin();
        UserEntity entity = requireAnyUser(userId);
        userRoleSupport.ensureVisibleTarget(entity);
        userRoleSupport.ensureAdminMutationAllowed(entity);
        entity.setPassword(userCredentialSupport.encodeDefaultPassword());
        entity.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(entity);
        return new ResetPasswordResponse(entity.getId(), entity.getUsername(), userCredentialSupport.defaultPassword());
    }

    public void removeAdminFromWorkspace(Long userId, String workspaceCode) {
        requirePlatformAdmin();
        if (!isCurrentSuperAdmin()) {
            throw new BadRequestException("只有超级管理员可以移除管理员");
        }
        UserEntity entity = requireAnyUser(userId);
        userRoleSupport.ensureVisibleTarget(entity);
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
        return userRoleSupport.isPlatformAdmin(userId);
    }

    public boolean isCurrentPlatformAdmin() {
        return userRoleSupport.isCurrentPlatformAdmin();
    }

    public boolean isSuperAdmin(Long userId) {
        return userRoleSupport.isSuperAdmin(userId);
    }

    public boolean isCurrentSuperAdmin() {
        return userRoleSupport.isCurrentSuperAdmin();
    }

    public List<UserEntity> listPlatformAdminUsers() {
        return userMapper.selectList(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getRoleCode, PlatformRole.PLATFORM_ADMIN)
                .eq(UserEntity::getStatus, 1)
                .orderByAsc(UserEntity::getId));
    }

    public void requirePlatformAdmin() {
        userRoleSupport.requirePlatformAdmin();
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

}
