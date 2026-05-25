package com.company.autoplatform.apiautomation;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.autoplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tb_api_definition_case_run_history")
public class ApiDefinitionCaseRunHistoryEntity extends BaseEntity {

    @TableField("workspace_id")
    private Long workspaceId;

    @TableField("definition_id")
    private Long definitionId;

    @TableField("case_id")
    private Long caseId;

    @TableField("report_id")
    private Long reportId;

    @TableField("case_name")
    private String caseName;

    @TableField("run_result")
    private String runResult;

    @TableField("failure_summary")
    private String failureSummary;

    @TableField("operator_name")
    private String operatorName;

    @TableField("environment_id")
    private Long environmentId;

    @TableField("environment_name")
    private String environmentName;

    @TableField("variable_set_id")
    private Long variableSetId;

    @TableField("variable_set_name")
    private String variableSetName;

    @TableField("status_code")
    private Integer statusCode;

    @TableField("duration_ms")
    private Long durationMs;

    @TableField("response_size")
    private Long responseSize;
}
