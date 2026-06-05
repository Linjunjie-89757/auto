package com.company.autoplatform.ai;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.autoplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tb_ai_case_config")
public class AiCaseConfigEntity extends BaseEntity {

    @TableField("workspace_id")
    private Long workspaceId;

    @TableField("owner_user_id")
    private Long ownerUserId;

    @TableField("role_type")
    private String roleType;

    @TableField("protocol_type")
    private String protocolType;

    private String provider;

    private String model;

    @TableField("base_url")
    private String baseUrl;

    @TableField("api_key_cipher_text")
    private String apiKeyCipherText;

    @TableField("prompt_template")
    private String promptTemplate;

    @TableField("review_checklist")
    private String reviewChecklist;

    private Double temperature;

    @TableField("top_p")
    private Double topP;

    @TableField("max_cases")
    private Integer maxCases;

    @TableField("provider_connection_id")
    private Long providerConnectionId;

    @TableField("capability_override_json")
    private String capabilityOverrideJson;

    @TableField("supports_image_input")
    private Integer supportsImageInput;

    private Integer status;
}
