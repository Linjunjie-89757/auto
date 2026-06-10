package com.company.autoplatform.user;

import com.company.autoplatform.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ApiResponse<List<UserItem>> listUsers() {
        return ApiResponse.ok(userService.listUsers());
    }

    @PostMapping
    public ApiResponse<UserItem> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.ok(userService.createUser(request), "成员创建成功");
    }

    @PostMapping("/batch")
    public ApiResponse<BatchCreateUserResponse> batchCreateUsers(@Valid @RequestBody BatchCreateUserRequest request) {
        return ApiResponse.ok(userService.batchCreateUsers(request), "批量新增账号完成");
    }

    @PutMapping("/{userId}")
    public ApiResponse<UserItem> updateUser(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        return ApiResponse.ok(userService.updateUser(userId, request), "成员信息更新成功");
    }

    @PutMapping("/{userId}/workspace-roles")
    public ApiResponse<UserItem> replaceWorkspaceRoles(
            @PathVariable Long userId,
            @Valid @RequestBody ReplaceUserWorkspaceRolesRequest request
    ) {
        return ApiResponse.ok(userService.replaceWorkspaceRoles(userId, request), "空间权限更新成功");
    }

    @PostMapping("/{userId}/reset-password")
    public ApiResponse<ResetPasswordResponse> resetPassword(@PathVariable Long userId) {
        return ApiResponse.ok(userService.resetPassword(userId), "密码已重置");
    }
}
