package com.company.autoplatform.settings;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.autoplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tb_param_set")
public class ParamSetEntity extends BaseEntity {

    @TableField("workspace_id")
    private Long workspaceId;

    @TableField("param_type")
    private String paramType;

    @TableField("param_name")
    private String paramName;

    @TableField("content_json")
    private String contentJson;

    private Integer status;
}
