package com.company.autoplatform.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.autoplatform.user.UserEntity;
import com.company.autoplatform.user.UserMapper;
import com.company.autoplatform.user.UserService;
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

    @Value("${app.super-admin.display-name:超级管理员}")
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
            entity.setPassword(passwordEncoder.encode(resolvePassword()));
            entity.setStatus(1);
            entity.setEmail(email);
            entity.setDisplayName(displayName);
            entity.setRoleCode(PlatformRole.SUPER_ADMIN);
            entity.setUpdatedAt(LocalDateTime.now());
            userMapper.insert(entity);
            return;
        }

        entity.setUsername(username);
        entity.setEmail(email);
        entity.setDisplayName(displayName);
        entity.setRoleCode(PlatformRole.SUPER_ADMIN);
        entity.setStatus(1);
        entity.setUpdatedAt(LocalDateTime.now());
        if (!alreadySuperAdmin || entity.getPassword() == null || entity.getPassword().isBlank()) {
            entity.setPassword(passwordEncoder.encode(resolvePassword()));
        }
        userMapper.updateById(entity);
        workspaceMemberMapper.delete(new LambdaQueryWrapper<com.company.autoplatform.workspace.WorkspaceMemberEntity>()
                .eq(com.company.autoplatform.workspace.WorkspaceMemberEntity::getUserId, entity.getId()));
    }

    private String resolvePassword() {
        String value = password == null ? "" : password.trim();
        if (value.isBlank()) {
            throw new IllegalStateException("app.super-admin.password must not be blank");
        }
        return value;
    }
}
