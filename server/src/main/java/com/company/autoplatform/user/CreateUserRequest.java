package com.company.autoplatform.user;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record CreateUserRequest(
        @NotBlank(message = "账号不能为空") String username,
        @NotBlank(message = "邮箱不能为空") String email,
        @NotBlank(message = "姓名不能为空") String displayName,
        String roleCode,
        List<String> workspaceCodes
) {
}
