package com.company.autoplatform.bug;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.autoplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tb_bug_case_relation")
public class BugCaseRelationEntity extends BaseEntity {

    @TableField("bug_id")
    private Long bugId;

    @TableField("case_id")
    private Long caseId;

    @TableField("created_by")
    private Long createdBy;
}
