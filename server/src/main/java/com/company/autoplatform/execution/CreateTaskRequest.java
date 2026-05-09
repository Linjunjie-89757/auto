package com.company.autoplatform.execution;

import jakarta.validation.constraints.NotBlank;

public record CreateTaskRequest(
        String workspaceCode,
        @NotBlank(message = "任务名称不能为空") String taskName,
        @NotBlank(message = "执行引擎不能为空") String engineType,
        @NotBlank(message = "任务状态不能为空") String status,
        String summary
) {
}
