package com.company.autoplatform.webuiautomation;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.autoplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tb_web_ui_run_batch")
public class WebUiRunBatchEntity extends BaseEntity {

    @TableField("workspace_id")
    private Long workspaceId;

    @TableField("batch_name")
    private String batchName;

    private String source;

    @TableField("environment_id")
    private Long environmentId;

    @TableField("environment_name")
    private String environmentName;

    private String status;

    @TableField("total_cases")
    private Integer totalCases;

    @TableField("success_cases")
    private Integer successCases;

    @TableField("failed_cases")
    private Integer failedCases;

    @TableField("duration_ms")
    private Long durationMs;

    @TableField("failure_summary")
    private String failureSummary;

    @TableField("operator_name")
    private String operatorName;

    @TableField("ci_token_id")
    private Long ciTokenId;

    @TableField("external_build_id")
    private String externalBuildId;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("finished_at")
    private LocalDateTime finishedAt;
}
