package com.company.autoplatform.user;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class UserCredentialSupport {

    private final PasswordEncoder passwordEncoder;

    public UserCredentialSupport(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    public String defaultPassword() {
        return UserService.DEFAULT_PASSWORD;
    }

    public String encodeDefaultPassword() {
        return passwordEncoder.encode(defaultPassword());
    }
}
