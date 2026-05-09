package com.company.autoplatform.settings;

import jakarta.validation.constraints.NotBlank;

public record CreateEnvConfigRequest(
        String workspaceCode,
        @NotBlank(message = "环境类型不能为空") String envType,
        @NotBlank(message = "环境名称不能为空") String envName,
        @NotBlank(message = "基础地址不能为空") String baseUrl,
        String configJson
) {
}
