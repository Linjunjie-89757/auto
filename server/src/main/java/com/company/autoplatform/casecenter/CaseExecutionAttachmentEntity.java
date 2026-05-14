package com.company.autoplatform.casecenter;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.autoplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tb_case_execution_attachment")
public class CaseExecutionAttachmentEntity extends BaseEntity {

    @TableField("case_id")
    private Long caseId;

    @TableField("workspace_id")
    private Long workspaceId;

    @TableField("file_name")
    private String fileName;

    @TableField("stored_path")
    private String storedPath;

    @TableField("content_type")
    private String contentType;

    @TableField("file_size")
    private Long fileSize;
}
