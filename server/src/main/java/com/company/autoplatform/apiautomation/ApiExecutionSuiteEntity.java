package com.company.autoplatform.apiautomation;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.autoplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tb_api_execution_suite")
public class ApiExecutionSuiteEntity extends BaseEntity {

    @TableField("workspace_id")
    private Long workspaceId;

    @TableField("module_id")
    private Long moduleId;

    @TableField("suite_name")
    private String suiteName;

    @TableField("priority")
    private String priority;

    @TableField("status")
    private String status;

    @TableField("description")
    private String description;

    @TableField("environment_id")
    private Long environmentId;

    @TableField("variable_set_id")
    private Long variableSetId;

    @TableField("run_mode")
    private String runMode;

    @TableField("run_on")
    private String runOn;

    @TableField("notify_enabled")
    private Boolean notifyEnabled;

    @TableField("continue_on_failure")
    private Boolean continueOnFailure;

    @TableField("global_timeout_ms")
    private Integer globalTimeoutMs;

    @TableField("step_failure_retry_count")
    private Integer stepFailureRetryCount;

    @TableField("default_step_wait_ms")
    private Integer defaultStepWaitMs;

    @TableField("schedule_enabled")
    private Boolean scheduleEnabled;

    @TableField("cron_expression")
    private String cronExpression;

    @TableField("branch_name")
    private String branchName;

    @TableField("trigger_source")
    private String triggerSource;

    @TableField("branch_note")
    private String branchNote;

    @TableField("last_run_result")
    private String lastRunResult;

    @TableField("last_run_at")
    private LocalDateTime lastRunAt;
}
