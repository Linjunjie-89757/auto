package com.company.autoplatform.webuiautomation;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.autoplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tb_web_ui_ci_token")
public class WebUiCiTokenEntity extends BaseEntity {

    @TableField("workspace_id")
    private Long workspaceId;

    @TableField("token_name")
    private String tokenName;

    @TableField("token_hash")
    private String tokenHash;

    private Integer status;

    @TableField("created_by")
    private String createdBy;

    @TableField("last_used_at")
    private LocalDateTime lastUsedAt;
}
