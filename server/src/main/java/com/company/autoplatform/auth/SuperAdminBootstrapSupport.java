package com.company.autoplatform.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.autoplatform.user.UserEntity;
import com.company.autoplatform.user.UserMapper;
import com.company.autoplatform.workspace.WorkspaceMemberEntity;
import com.company.autoplatform.workspace.WorkspaceMemberMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class SuperAdminBootstrapSupport {

    private final UserMapper userMapper;
    private final WorkspaceMemberMapper workspaceMemberMapper;
    private final PasswordEncoder passwordEncoder;

    public SuperAdminBootstrapSupport(
            UserMapper userMapper,
            WorkspaceMemberMapper workspaceMemberMapper,
            PasswordEncoder passwordEncoder
    ) {
        this.userMapper = userMapper;
        this.workspaceMemberMapper = workspaceMemberMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public void bootstrap(SuperAdminProperties properties) {
        String resolvedPassword = resolvePassword(properties.password());
        UserEntity entity = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getRoleCode, PlatformRole.SUPER_ADMIN)
                .last("limit 1"));
        boolean alreadySuperAdmin = entity != null;
        if (entity == null) {
            entity = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                    .eq(UserEntity::getUsername, properties.username())
                    .last("limit 1"));
        }
        if (entity == null) {
            entity = new UserEntity();
            entity.setUsername(properties.username());
            entity.setCreatedAt(LocalDateTime.now());
            entity.setPassword(passwordEncoder.encode(resolvedPassword));
            entity.setStatus(1);
            entity.setEmail(properties.email());
            entity.setDisplayName(properties.displayName());
            entity.setRoleCode(PlatformRole.SUPER_ADMIN);
            entity.setUpdatedAt(LocalDateTime.now());
            userMapper.insert(entity);
            return;
        }

        boolean needsPassword = !alreadySuperAdmin || entity.getPassword() == null || entity.getPassword().isBlank();
        boolean changed = !properties.username().equals(entity.getUsername())
                || !properties.email().equals(entity.getEmail())
                || !properties.displayName().equals(entity.getDisplayName())
                || !PlatformRole.SUPER_ADMIN.equals(entity.getRoleCode())
                || entity.getStatus() == null || entity.getStatus() != 1
                || needsPassword;

        if (changed) {
            entity.setUsername(properties.username());
            entity.setEmail(properties.email());
            entity.setDisplayName(properties.displayName());
            entity.setRoleCode(PlatformRole.SUPER_ADMIN);
            entity.setStatus(1);
            entity.setUpdatedAt(LocalDateTime.now());
            if (needsPassword) {
                entity.setPassword(passwordEncoder.encode(resolvedPassword));
            }
            userMapper.updateById(entity);
        }

        Long membershipCount = workspaceMemberMapper.selectCount(
                new LambdaQueryWrapper<WorkspaceMemberEntity>()
                        .eq(WorkspaceMemberEntity::getUserId, entity.getId())
        );
        if (membershipCount != null && membershipCount > 0) {
            workspaceMemberMapper.delete(new LambdaQueryWrapper<WorkspaceMemberEntity>()
                    .eq(WorkspaceMemberEntity::getUserId, entity.getId()));
        }
    }

    private String resolvePassword(String password) {
        String value = password == null ? "" : password.trim();
        if (value.isBlank()) {
            throw new IllegalStateException("app.super-admin.password must not be blank");
        }
        return value;
    }
}
