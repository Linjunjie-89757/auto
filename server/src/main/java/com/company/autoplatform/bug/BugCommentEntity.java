package com.company.autoplatform.bug;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.autoplatform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tb_bug_comment")
public class BugCommentEntity extends BaseEntity {

    @TableField("bug_id")
    private Long bugId;

    private String content;

    @TableField("commenter_id")
    private Long commenterId;
}
