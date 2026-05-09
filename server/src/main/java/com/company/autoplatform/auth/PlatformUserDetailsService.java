package com.company.autoplatform.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.autoplatform.common.BadRequestException;
import com.company.autoplatform.user.UserEntity;
import com.company.autoplatform.user.UserMapper;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class PlatformUserDetailsService implements UserDetailsService {

    private final UserMapper userMapper;

    public PlatformUserDetailsService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserEntity user = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .and(wrapper -> wrapper.eq(UserEntity::getUsername, username)
                        .or()
                        .eq(UserEntity::getEmail, username))
                .last("limit 1"));
        if (user == null || user.getStatus() != 1) {
            throw new UsernameNotFoundException("用户不存在或已停用");
        }
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            throw new BadRequestException("当前账号未设置密码");
        }
        return new CurrentUserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getPassword(),
                user.getRoleCode(),
                user.getStatus()
        );
    }
}
