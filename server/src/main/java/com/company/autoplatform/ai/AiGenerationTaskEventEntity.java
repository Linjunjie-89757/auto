package com.company.autoplatform.ai;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_ai_generation_task_event")
public class AiGenerationTaskEventEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("task_id")
    private String taskId;

    private Integer seq;

    @TableField("event_type")
    private String eventType;

    private String phase;

    private String level;

    private String message;

    @TableField("item_index")
    private Integer itemIndex;

    @TableField("item_title")
    private String itemTitle;

    private String provider;

    private String model;

    @TableField("payload_json")
    private String payloadJson;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
