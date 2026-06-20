package com.company.autoplatform.webuiautomation;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.autoplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tb_web_ui_run_artifact")
public class WebUiRunArtifactEntity extends BaseEntity {

    @TableField("workspace_id")
    private Long workspaceId;

    @TableField("run_id")
    private Long runId;

    @TableField("step_id")
    private Long stepId;

    @TableField("artifact_type")
    private String artifactType;

    @TableField("file_name")
    private String fileName;

    @TableField("content_type")
    private String contentType;

    @TableField("file_size")
    private Long fileSize;

    @TableField("storage_path")
    private String storagePath;
}
