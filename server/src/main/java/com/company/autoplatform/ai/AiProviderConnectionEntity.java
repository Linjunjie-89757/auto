package com.company.autoplatform.ai;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.autoplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tb_ai_provider_connection")
public class AiProviderConnectionEntity extends BaseEntity {

    @TableField("workspace_id")
    private Long workspaceId;

    @TableField("connection_name")
    private String connectionName;

    @TableField("protocol_type")
    private String protocolType;

    @TableField("base_url")
    private String baseUrl;

    @TableField("api_key_cipher_text")
    private String apiKeyCipherText;

    private Integer status;

    @TableField("last_verified_at")
    private LocalDateTime lastVerifiedAt;

    @TableField("last_fetch_models_at")
    private LocalDateTime lastFetchModelsAt;
}
