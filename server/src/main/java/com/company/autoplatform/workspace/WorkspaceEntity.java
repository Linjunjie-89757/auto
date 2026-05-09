package com.company.autoplatform.workspace;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.autoplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tb_sys_workspace")
public class WorkspaceEntity extends BaseEntity {

    @TableField("workspace_code")
    private String workspaceCode;

    @TableField("workspace_name")
    private String workspaceName;

    private String description;

    private Integer status;
}
