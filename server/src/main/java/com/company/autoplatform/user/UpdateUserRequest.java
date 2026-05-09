package com.company.autoplatform.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateUserRequest(
        @NotBlank(message = "邮箱不能为空") String email,
        @NotBlank(message = "姓名不能为空") String displayName,
        @NotBlank(message = "成员角色不能为空") String roleCode,
        @NotNull(message = "状态不能为空") Integer status,
        List<String> workspaceCodes
) {
}
