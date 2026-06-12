package com.company.autoplatform.auth;

import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthCurrentUserSupport currentUserSupport;

    public AuthService(AuthCurrentUserSupport currentUserSupport) {
        this.currentUserSupport = currentUserSupport;
    }

    public CurrentUserResponse currentUser() {
        return currentUserSupport.currentUser();
    }
}
