package com.company.autoplatform.user;

public record ResetPasswordResponse(
        Long userId,
        String username,
        String defaultPassword
) {
}
