package com.company.autoplatform.apiautomation;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.autoplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tb_api_execution_suite_run_history")
public class ApiExecutionSuiteRunHistoryEntity extends BaseEntity {

    @TableField("workspace_id")
    private Long workspaceId;

    @TableField("suite_id")
    private Long suiteId;

    @TableField("suite_name")
    private String suiteName;

    @TableField("report_id")
    private Long reportId;

    @TableField("result")
    private String result;

    @TableField("failure_summary")
    private String failureSummary;

    @TableField("total_count")
    private Integer totalCount;

    @TableField("success_count")
    private Integer successCount;

    @TableField("failed_count")
    private Integer failedCount;

    @TableField("skipped_count")
    private Integer skippedCount;

    @TableField("duration_ms")
    private Long durationMs;

    @TableField("environment_id")
    private Long environmentId;

    @TableField("variable_set_id")
    private Long variableSetId;

    @TableField("operator_id")
    private Long operatorId;

    @TableField("operator_name")
    private String operatorName;

    @TableField("detail_json")
    private String detailJson;
}
