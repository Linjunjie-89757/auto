package com.company.autoplatform.settings;

import jakarta.validation.constraints.NotBlank;

public record CreateParamSetRequest(
        String workspaceCode,
        @NotBlank(message = "参数类型不能为空") String paramType,
        @NotBlank(message = "参数名称不能为空") String paramName,
        String contentJson
) {
}
