package com.company.autoplatform.webuiautomation;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.autoplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tb_web_ui_case_template")
public class WebUiCaseTemplateEntity extends BaseEntity {

    @TableField("workspace_id")
    private Long workspaceId;

    @TableField("module_name")
    private String moduleName;

    @TableField("template_name")
    private String templateName;

    private String description;

    @TableField("base_url")
    private String baseUrl;

    @TableField("browser_type")
    private String browserType;

    private Boolean headless;

    @TableField("default_timeout_ms")
    private Integer defaultTimeoutMs;

    private String status;
}
