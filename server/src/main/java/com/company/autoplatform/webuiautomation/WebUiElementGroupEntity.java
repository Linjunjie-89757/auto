package com.company.autoplatform.webuiautomation;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.autoplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tb_web_ui_element_group")
public class WebUiElementGroupEntity extends BaseEntity {

    @TableField("workspace_id")
    private Long workspaceId;

    @TableField("page_id")
    private Long pageId;

    @TableField("group_name")
    private String groupName;

    private String description;

    @TableField("sort_order")
    private Integer sortOrder;

    private String status;
}
