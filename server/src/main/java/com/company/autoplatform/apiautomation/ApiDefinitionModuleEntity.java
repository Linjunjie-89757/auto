package com.company.autoplatform.apiautomation;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.autoplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tb_api_definition_module")
public class ApiDefinitionModuleEntity extends BaseEntity {

    @TableField("workspace_id")
    private Long workspaceId;

    @TableField("parent_id")
    private Long parentId;

    @TableField("module_name")
    private String moduleName;

    @TableField("sort_order")
    private Integer sortOrder;
}
