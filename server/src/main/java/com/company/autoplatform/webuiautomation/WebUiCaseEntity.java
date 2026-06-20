package com.company.autoplatform.webuiautomation;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.autoplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tb_web_ui_case")
public class WebUiCaseEntity extends BaseEntity {

    @TableField("workspace_id")
    private Long workspaceId;

    @TableField("module_name")
    private String moduleName;

    @TableField("case_name")
    private String caseName;

    private String description;

    @TableField("base_url")
    private String baseUrl;

    @TableField("browser_type")
    private String browserType;

    private Boolean headless;

    @TableField("default_timeout_ms")
    private Integer defaultTimeoutMs;

    private String status;

    @TableField("last_run_result")
    private String lastRunResult;

    @TableField("last_run_at")
    private LocalDateTime lastRunAt;
}
