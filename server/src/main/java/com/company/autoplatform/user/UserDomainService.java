package com.company.autoplatform.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.autoplatform.auth.CurrentUserContext;
import com.company.autoplatform.auth.PlatformRole;
import com.company.autoplatform.common.BadRequestException;
import com.company.autoplatform.workspace.WorkspaceEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class UserDomainService {

    private final UserMapper userMapper;
    private final UserCredentialSupport userCredentialSupport;
    private final UserRoleSupport userRoleSupport;
    private final UserWorkspaceGrantSupport userWorkspaceGrantSupport;

    public UserDomainService(
            UserMapper userMapper,
            UserCredentialSupport userCredentialSupport,
            UserRoleSupport userRoleSupport,
            UserWorkspaceGrantSupport userWorkspaceGrantSupport
    ) {
        this.userMapper = userMapper;
        this.userCredentialSupport = userCredentialSupport;
        this.userRoleSupport = userRoleSupport;
        this.userWorkspaceGrantSupport = userWorkspaceGrantSupport;
    }

    public List<UserItem> listUsers() {
        CurrentUserContext.require();
        List<UserEntity> users = userMapper.selectList(new LambdaQueryWrapper<UserEntity>().orderByAsc(UserEntity::getId));
        Map<Long, List<WorkspaceEntity>> workspaceMap = userWorkspaceGrantSupport.buildUserWorkspaceMap();
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

        userWorkspaceGrantSupport.replaceWorkspaceCodes(entity, request.workspaceCodes());
        return toItem(entity, userWorkspaceGrantSupport.findUserWorkspaces(entity.getId()));
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

        userWorkspaceGrantSupport.replaceWorkspaceCodes(entity, request.workspaceCodes() == null ? List.of() : request.workspaceCodes());
        return toItem(entity, userWorkspaceGrantSupport.findUserWorkspaces(entity.getId()));
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

    private void validateEmailAvailable(String email, Long excludeUserId) {
        UserEntity existing = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getEmail, email)
                .last("limit 1"));
        if (existing != null && (excludeUserId == null || !existing.getId().equals(excludeUserId))) {
            throw new BadRequestException("邮箱已存在");
        }
    }

}
