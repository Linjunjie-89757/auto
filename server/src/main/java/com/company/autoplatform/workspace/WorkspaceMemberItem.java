package com.company.autoplatform.workspace;

public record WorkspaceMemberItem(
        Long id,
        Long userId,
        String username,
        String email,
        String displayName,
        String roleCode,
        Integer status
) {
}
