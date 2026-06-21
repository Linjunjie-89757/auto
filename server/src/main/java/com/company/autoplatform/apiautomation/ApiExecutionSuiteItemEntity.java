package com.company.autoplatform.apiautomation;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.autoplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tb_api_execution_suite_item")
public class ApiExecutionSuiteItemEntity extends BaseEntity {

    @TableField("workspace_id")
    private Long workspaceId;

    @TableField("suite_id")
    private Long suiteId;

    @TableField("item_type")
    private String itemType;

    @TableField("item_id")
    private Long itemId;

    @TableField("item_name_snapshot")
    private String itemNameSnapshot;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("enabled")
    private Boolean enabled;

    @TableField("description")
    private String description;
}
