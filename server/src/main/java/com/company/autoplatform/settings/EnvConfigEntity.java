package com.company.autoplatform.settings;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.autoplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tb_env_config")
public class EnvConfigEntity extends BaseEntity {

    @TableField("workspace_id")
    private Long workspaceId;

    @TableField("env_type")
    private String envType;

    @TableField("env_name")
    private String envName;

    @TableField("base_url")
    private String baseUrl;

    @TableField("config_json")
    private String configJson;

    private Integer status;
}
