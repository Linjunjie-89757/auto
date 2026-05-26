package com.company.autoplatform.apiautomation;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.autoplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tb_api_definition_case_change_history")
public class ApiDefinitionCaseChangeHistoryEntity extends BaseEntity {

    @TableField("workspace_id")
    private Long workspaceId;

    @TableField("definition_id")
    private Long definitionId;

    @TableField("case_id")
    private Long caseId;

    @TableField("case_name")
    private String caseName;

    @TableField("change_type")
    private String changeType;

    @TableField("change_summary")
    private String changeSummary;

    @TableField("operator_id")
    private Long operatorId;

    @TableField("operator_name")
    private String operatorName;
}
