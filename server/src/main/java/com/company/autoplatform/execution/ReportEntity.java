package com.company.autoplatform.execution;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.autoplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tb_exec_report")
public class ReportEntity extends BaseEntity {

    @TableField("workspace_id")
    private Long workspaceId;

    @TableField("task_id")
    private Long taskId;

    @TableField("report_name")
    private String reportName;

    private String result;

    @TableField("failure_summary")
    private String failureSummary;

    @TableField("log_source")
    private String logSource;

    @TableField("log_text")
    private String logText;

    @TableField("attachments_json")
    private String attachmentsJson;
}
