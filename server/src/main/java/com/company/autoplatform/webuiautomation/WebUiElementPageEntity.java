package com.company.autoplatform.webuiautomation;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.autoplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tb_web_ui_element_page")
public class WebUiElementPageEntity extends BaseEntity {

    @TableField("workspace_id")
    private Long workspaceId;

    @TableField("module_id")
    private Long moduleId;

    @TableField("page_name")
    private String pageName;

    @TableField("page_path")
    private String pagePath;

    private String description;

    @TableField("sort_order")
    private Integer sortOrder;

    private String status;
}
