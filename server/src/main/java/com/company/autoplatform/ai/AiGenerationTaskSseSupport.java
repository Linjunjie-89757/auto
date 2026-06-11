package com.company.autoplatform.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.autoplatform.common.BadRequestException;
import com.company.autoplatform.workspace.WorkspaceEntity;
import com.company.autoplatform.workspace.WorkspaceScope;
import com.company.autoplatform.workspace.WorkspaceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class AiGenerationTaskSseSupport {

    private static final List<String> TERMINAL_STATUSES = List.of("COMPLETED", "FAILED", "CANCELED");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AiGenerationTaskMapper aiGenerationTaskMapper;
    private final AiGenerationTaskEventService eventService;
    private final WorkspaceService workspaceService;

    public AiGenerationTaskSseSupport(
            AiGenerationTaskMapper aiGenerationTaskMapper,
            AiGenerationTaskEventService eventService,
            WorkspaceService workspaceService
    ) {
        this.aiGenerationTaskMapper = aiGenerationTaskMapper;
        this.eventService = eventService;
        this.workspaceService = workspaceService;
    }

    StreamingResponseBody streamTaskEvents(String taskId, String workspaceCode) {
        AiGenerationTaskEntity entity = requireTask(taskId);
        WorkspaceEntity workspace = workspaceService.requireReadableWorkspace(
                workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode()
        );
        validateReadableWorkspaceScope(workspaceCode, workspace);
        return outputStream -> streamEventsTo(taskId, outputStream);
    }

    private void streamEventsTo(String taskId, OutputStream outputStream) throws IOException {
        int lastSeq = 0;
        for (AiGenerationTaskEventResponse event : eventService.list(taskId)) {
            writeSseData(outputStream, event);
            lastSeq = Math.max(lastSeq, event.seq() == null ? 0 : event.seq());
        }
        while (true) {
            AiGenerationTaskEntity latest = requireTask(taskId);
            List<AiGenerationTaskEventResponse> events = eventService.listAfter(taskId, lastSeq);
            for (AiGenerationTaskEventResponse event : events) {
                writeSseData(outputStream, event);
                lastSeq = Math.max(lastSeq, event.seq() == null ? lastSeq : event.seq());
            }
            if (TERMINAL_STATUSES.contains(latest.getStatus()) && events.isEmpty()) {
                break;
            }
            outputStream.write(": ping\n\n".getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            try {
                Thread.sleep(800);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void writeSseData(OutputStream outputStream, AiGenerationTaskEventResponse event) throws IOException {
        outputStream.write(("data: " + OBJECT_MAPPER.writeValueAsString(event) + "\n\n").getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }

    private AiGenerationTaskEntity requireTask(String taskId) {
        AiGenerationTaskEntity entity = aiGenerationTaskMapper.selectOne(new LambdaQueryWrapper<AiGenerationTaskEntity>()
                .eq(AiGenerationTaskEntity::getTaskId, taskId)
                .last("limit 1"));
        if (entity == null) {
            throw new BadRequestException("AI generation task does not exist");
        }
        return entity;
    }

    private void validateReadableWorkspaceScope(String workspaceCode, WorkspaceEntity workspace) {
        String normalized = WorkspaceScope.normalize(workspaceCode);
        if (WorkspaceScope.isAll(normalized)) {
            workspaceService.requireReadableWorkspace(workspace.getWorkspaceCode());
            return;
        }
        if (!normalized.equals(workspace.getWorkspaceCode())) {
            throw new BadRequestException("Task does not belong to the current workspace");
        }
    }
}
