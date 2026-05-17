package com.company.autoplatform.bug;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.autoplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tb_bug_attachment")
public class BugAttachmentEntity extends BaseEntity {

    @TableField("bug_id")
    private Long bugId;

    @TableField("created_by")
    private Long createdBy;

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
