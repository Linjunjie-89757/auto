package com.company.autoplatform.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BatchCreateUserRequest(
        @NotEmpty(message = "批量成员不能为空")
        List<@Valid CreateUserRequest> users
) {
}
