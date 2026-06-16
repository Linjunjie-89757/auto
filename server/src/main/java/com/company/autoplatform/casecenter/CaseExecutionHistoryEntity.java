package com.company.autoplatform.casecenter;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.autoplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tb_case_execution_history")
public class CaseExecutionHistoryEntity extends BaseEntity {

    @TableField("workspace_id")
    private Long workspaceId;

    @TableField("case_id")
    private Long caseId;

    @TableField("execution_status")
    private String executionStatus;

    @TableField("execution_comment")
    private String executionComment;

    @TableField("execution_note")
    private String executionNote;

    @TableField("executor_id")
    private Long executorId;

    @TableField("executor_name")
    private String executorName;

    @TableField("executed_at")
    private LocalDateTime executedAt;
}
