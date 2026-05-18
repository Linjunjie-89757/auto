package com.company.autoplatform.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.autoplatform.user.UserEntity;
import com.company.autoplatform.user.UserMapper;
import com.company.autoplatform.workspace.WorkspaceMemberMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class SuperAdminBootstrap implements ApplicationRunner {

    private final UserMapper userMapper;
    private final WorkspaceMemberMapper workspaceMemberMapper;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.super-admin.username:superadmin}")
    private String username;

    @Value("${app.super-admin.email:superadmin@local}")
    private String email;

    @Value("${app.super-admin.display-name:Super Admin}")
    private String displayName;

    @Value("${app.super-admin.password:superadmin123}")
    private String password;

    public SuperAdminBootstrap(
            UserMapper userMapper,
            WorkspaceMemberMapper workspaceMemberMapper,
            PasswordEncoder passwordEncoder
    ) {
        this.userMapper = userMapper;
        this.workspaceMemberMapper = workspaceMemberMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        String resolvedPassword = resolvePassword();
        UserEntity entity = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getRoleCode, PlatformRole.SUPER_ADMIN)
                .last("limit 1"));
        boolean alreadySuperAdmin = entity != null;
        if (entity == null) {
            entity = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                    .eq(UserEntity::getUsername, username)
                    .last("limit 1"));
        }
        if (entity == null) {
            entity = new UserEntity();
            entity.setUsername(username);
            entity.setCreatedAt(LocalDateTime.now());
            entity.setPassword(passwordEncoder.encode(resolvedPassword));
            entity.setStatus(1);
            entity.setEmail(email);
            entity.setDisplayName(displayName);
            entity.setRoleCode(PlatformRole.SUPER_ADMIN);
            entity.setUpdatedAt(LocalDateTime.now());
            userMapper.insert(entity);
            return;
        }

        boolean needsPassword = !alreadySuperAdmin || entity.getPassword() == null || entity.getPassword().isBlank();
        boolean changed = !username.equals(entity.getUsername())
                || !email.equals(entity.getEmail())
                || !displayName.equals(entity.getDisplayName())
                || !PlatformRole.SUPER_ADMIN.equals(entity.getRoleCode())
                || entity.getStatus() == null || entity.getStatus() != 1
                || needsPassword;

        if (changed) {
            entity.setUsername(username);
            entity.setEmail(email);
            entity.setDisplayName(displayName);
            entity.setRoleCode(PlatformRole.SUPER_ADMIN);
            entity.setStatus(1);
            entity.setUpdatedAt(LocalDateTime.now());
            if (needsPassword) {
                entity.setPassword(passwordEncoder.encode(resolvedPassword));
            }
            userMapper.updateById(entity);
        }

        Long membershipCount = workspaceMemberMapper.selectCount(
                new LambdaQueryWrapper<com.company.autoplatform.workspace.WorkspaceMemberEntity>()
                        .eq(com.company.autoplatform.workspace.WorkspaceMemberEntity::getUserId, entity.getId())
        );
        if (membershipCount != null && membershipCount > 0) {
            workspaceMemberMapper.delete(new LambdaQueryWrapper<com.company.autoplatform.workspace.WorkspaceMemberEntity>()
                    .eq(com.company.autoplatform.workspace.WorkspaceMemberEntity::getUserId, entity.getId()));
        }
    }

    private String resolvePassword() {
        String value = password == null ? "" : password.trim();
        if (value.isBlank()) {
            throw new IllegalStateException("app.super-admin.password must not be blank");
        }
        return value;
    }
}
