package com.company.autoplatform.user;

public record BatchCreateUserItem(
        Integer index,
        String username,
        String email,
        String displayName,
        Boolean success,
        String message,
        UserItem user
) {
}
