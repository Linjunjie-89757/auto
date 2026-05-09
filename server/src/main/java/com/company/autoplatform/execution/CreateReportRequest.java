package com.company.autoplatform.execution;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateReportRequest(
        String workspaceCode,
        @NotNull(message = "关联任务不能为空") Long taskId,
        @NotBlank(message = "报告名称不能为空") String reportName,
        @NotBlank(message = "执行结果不能为空") String result,
        String logSource,
        String failureSummary
) {
}
