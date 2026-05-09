package com.company.autoplatform.bug;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.autoplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tb_bug_flow_record")
public class BugFlowEntity extends BaseEntity {

    @TableField("bug_id")
    private Long bugId;

    @TableField("from_status")
    private String fromStatus;

    @TableField("to_status")
    private String toStatus;

    @TableField("operator_id")
    private Long operatorId;

    @TableField("action_comment")
    private String actionComment;
}
