package com.company.autoplatform.settings;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.autoplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tb_db_connection")
public class DbConnectionEntity extends BaseEntity {

    @TableField("workspace_id")
    private Long workspaceId;

    @TableField("connection_name")
    private String connectionName;

    @TableField("db_type")
    private String dbType;

    @TableField("driver_class_name")
    private String driverClassName;

    @TableField("jdbc_url")
    private String jdbcUrl;

    private String username;

    @TableField("password_encrypted")
    private String passwordEncrypted;

    @TableField("pool_max")
    private Integer poolMax;

    @TableField("timeout_ms")
    private Integer timeoutMs;

    private String description;

    private Integer status;
}
