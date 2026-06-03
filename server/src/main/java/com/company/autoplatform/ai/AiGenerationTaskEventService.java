package com.company.autoplatform.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AiGenerationTaskEventService {

    private final AiGenerationTaskEventMapper eventMapper;

    public AiGenerationTaskEventService(AiGenerationTaskEventMapper eventMapper) {
        this.eventMapper = eventMapper;
    }

    @Transactional
    public synchronized AiGenerationTaskEventResponse append(
            String taskId,
            String eventType,
            String phase,
            String level,
            String message,
            Integer itemIndex,
            String itemTitle,
            String provider,
            String model,
            String payloadJson
    ) {
        AiGenerationTaskEventEntity latest = eventMapper.selectOne(new LambdaQueryWrapper<AiGenerationTaskEventEntity>()
                .eq(AiGenerationTaskEventEntity::getTaskId, taskId)
                .orderByDesc(AiGenerationTaskEventEntity::getSeq)
                .last("limit 1"));
        int nextSeq = latest == null || latest.getSeq() == null ? 1 : latest.getSeq() + 1;
        AiGenerationTaskEventEntity entity = new AiGenerationTaskEventEntity();
        entity.setTaskId(taskId);
        entity.setSeq(nextSeq);
        entity.setEventType(eventType);
        entity.setPhase(phase);
        entity.setLevel(level);
        entity.setMessage(message);
        entity.setItemIndex(itemIndex);
        entity.setItemTitle(itemTitle);
        entity.setProvider(provider);
        entity.setModel(model);
        entity.setPayloadJson(payloadJson);
        entity.setCreatedAt(LocalDateTime.now());
        eventMapper.insert(entity);
        return toResponse(entity);
    }

    public List<AiGenerationTaskEventResponse> list(String taskId) {
        return eventMapper.selectList(new LambdaQueryWrapper<AiGenerationTaskEventEntity>()
                        .eq(AiGenerationTaskEventEntity::getTaskId, taskId)
                        .orderByAsc(AiGenerationTaskEventEntity::getSeq))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<AiGenerationTaskEventResponse> listAfter(String taskId, int seq) {
        return eventMapper.selectList(new LambdaQueryWrapper<AiGenerationTaskEventEntity>()
                        .eq(AiGenerationTaskEventEntity::getTaskId, taskId)
                        .gt(AiGenerationTaskEventEntity::getSeq, seq)
                        .orderByAsc(AiGenerationTaskEventEntity::getSeq))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private AiGenerationTaskEventResponse toResponse(AiGenerationTaskEventEntity entity) {
        return new AiGenerationTaskEventResponse(
                entity.getId(),
                entity.getTaskId(),
                entity.getSeq(),
                entity.getEventType(),
                entity.getPhase(),
                entity.getLevel(),
                entity.getMessage(),
                entity.getItemIndex(),
                entity.getItemTitle(),
                entity.getProvider(),
                entity.getModel(),
                entity.getPayloadJson(),
                entity.getCreatedAt() == null ? null : entity.getCreatedAt().toString()
        );
    }
}
