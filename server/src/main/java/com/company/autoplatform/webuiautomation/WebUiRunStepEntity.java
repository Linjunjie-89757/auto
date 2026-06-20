package com.company.autoplatform.webuiautomation;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.autoplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tb_web_ui_run_step")
public class WebUiRunStepEntity extends BaseEntity {

    @TableField("run_id")
    private Long runId;

    @TableField("case_step_id")
    private Long caseStepId;

    @TableField("step_name")
    private String stepName;

    @TableField("step_type")
    private String stepType;

    private String status;

    @TableField("locator_type")
    private String locatorType;

    @TableField("locator_value")
    private String locatorValue;

    @TableField("input_value_snapshot")
    private String inputValueSnapshot;

    @TableField("duration_ms")
    private Long durationMs;

    @TableField("error_message")
    private String errorMessage;

    @TableField("screenshot_artifact_id")
    private Long screenshotArtifactId;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("finished_at")
    private LocalDateTime finishedAt;
}
