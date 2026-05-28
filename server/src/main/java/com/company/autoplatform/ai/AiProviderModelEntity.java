package com.company.autoplatform.ai;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.autoplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tb_ai_provider_model_cache")
public class AiProviderModelEntity extends BaseEntity {

    @TableField("connection_id")
    private Long connectionId;

    @TableField("model_name")
    private String modelName;

    @TableField("display_name")
    private String displayName;

    @TableField("raw_metadata_json")
    private String rawMetadataJson;

    @TableField("detected_capabilities_json")
    private String detectedCapabilitiesJson;

    @TableField("selectable")
    private Integer selectable;

    @TableField("last_probed_at")
    private LocalDateTime lastProbedAt;
}
