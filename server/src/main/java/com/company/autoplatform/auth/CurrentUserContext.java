package com.company.autoplatform.auth;

import com.company.autoplatform.common.BadRequestException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUserContext {

    private CurrentUserContext() {
    }

    public static Long get() {
        return require().userId();
    }

    public static CurrentUserPrincipal require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            throw new BadRequestException("当前用户未登录");
        }
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof CurrentUserPrincipal currentUser)) {
            throw new BadRequestException("当前登录态无效");
        }
        return currentUser;
    }
}
