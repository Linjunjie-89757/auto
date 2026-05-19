package com.company.autoplatform.apiautomation;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.autoplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tb_api_run_step_result")
public class ApiRunStepResultEntity extends BaseEntity {

    @TableField("workspace_id")
    private Long workspaceId;

    @TableField("report_id")
    private Long reportId;

    @TableField("step_order")
    private Integer stepOrder;

    @TableField("step_name")
    private String stepName;

    @TableField("definition_id")
    private Long definitionId;

    private Boolean success;

    @TableField("duration_ms")
    private Long durationMs;

    @TableField("request_snapshot_json")
    private String requestSnapshotJson;

    @TableField("response_snapshot_json")
    private String responseSnapshotJson;

    @TableField("assertion_results_json")
    private String assertionResultsJson;

    @TableField("extraction_results_json")
    private String extractionResultsJson;

    @TableField("processor_results_json")
    private String processorResultsJson;

    @TableField("error_message")
    private String errorMessage;
}
