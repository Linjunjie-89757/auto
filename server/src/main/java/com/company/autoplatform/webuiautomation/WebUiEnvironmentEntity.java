package com.company.autoplatform.webuiautomation;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.autoplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tb_web_ui_environment")
public class WebUiEnvironmentEntity extends BaseEntity {

    @TableField("workspace_id")
    private Long workspaceId;

    @TableField("environment_name")
    private String environmentName;

    @TableField("base_url")
    private String baseUrl;

    @TableField("browser_type")
    private String browserType;

    private Boolean headless;

    @TableField("default_timeout_ms")
    private Integer defaultTimeoutMs;

    private Integer status;
}
