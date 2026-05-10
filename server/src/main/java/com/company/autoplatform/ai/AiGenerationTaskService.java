package com.company.autoplatform.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.autoplatform.casecenter.CaseDirectoryEntity;
import com.company.autoplatform.casecenter.CaseDirectoryMapper;
import com.company.autoplatform.common.BadRequestException;
import com.company.autoplatform.workspace.WorkspaceEntity;
import com.company.autoplatform.workspace.WorkspaceScope;
import com.company.autoplatform.workspace.WorkspaceService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AiGenerationTaskService {

    private static final List<String> RUNNING_STATUSES = List.of("PENDING", "GENERATING", "REVIEWING");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AiGenerationTaskMapper aiGenerationTaskMapper;
    private final AiCaseService aiCaseService;
    private final WorkspaceService workspaceService;
    private final CaseDirectoryMapper caseDirectoryMapper;

    public AiGenerationTaskService(
            AiGenerationTaskMapper aiGenerationTaskMapper,
            AiCaseService aiCaseService,
            WorkspaceService workspaceService,
            CaseDirectoryMapper caseDirectoryMapper
    ) {
        this.aiGenerationTaskMapper = aiGenerationTaskMapper;
        this.aiCaseService = aiCaseService;
        this.workspaceService = workspaceService;
        this.caseDirectoryMapper = caseDirectoryMapper;
    }

    public AiGenerationTaskResponse createTask(String headerWorkspaceCode, CreateAiGenerationTaskRequest request) {
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(
                workspaceService.resolveTargetWorkspace(headerWorkspaceCode, request.workspaceCode())
        );
        validateOutputMode(request.outputMode());
        validateDirectory(workspace, request.directoryId());

        AiGenerationTaskEntity entity = new AiGenerationTaskEntity();
        LocalDateTime now = LocalDateTime.now();
        entity.setTaskId(generateTaskId());
        entity.setWorkspaceId(workspace.getId());
        entity.setRequirementTitle(request.requirementTitle().trim());
        entity.setRequirementContent(request.requirementContent().trim());
        entity.setOutputMode(normalizeOutputMode(request.outputMode()));
        entity.setStatus("PENDING");
        entity.setCurrentStep(1);
        entity.setStepMessage("任务已创建，等待开始生成测试用例。");
        entity.setErrorMessage(null);
        entity.setDirectoryId(request.directoryId());
        entity.setDirectoryName(blankToNull(request.directoryName()));
        entity.setProvider(null);
        entity.setModel(null);
        entity.setAssetIdsJson(writeValue(request.assetIds() == null ? List.of() : request.assetIds()));
        entity.setWarningsJson(writeValue(List.of()));
        entity.setInvalidCasesJson(writeValue(List.of()));
        entity.setGeneratedCasesJson(writeValue(List.of()));
        entity.setReviewResultJson(null);
        entity.setAdoptedCaseIndexesJson(writeValue(List.of()));
        entity.setDeletedCaseIndexesJson(writeValue(List.of()));
        entity.setSavedCaseCount(0);
        entity.setGeneratedCount(0);
        entity.setCancelRequested(0);
        entity.setSourceTaskId(null);
        entity.setFinishedAt(null);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        aiGenerationTaskMapper.insert(entity);

        return toResponse(entity, workspace);
    }

    public List<AiGenerationTaskResponse> listTasks(String workspaceCode) {
        List<WorkspaceEntity> workspaces = resolveReadableWorkspaces(workspaceCode);
        if (workspaces.isEmpty()) {
            return List.of();
        }
        List<Long> workspaceIds = workspaces.stream().map(WorkspaceEntity::getId).toList();
        List<AiGenerationTaskEntity> tasks = aiGenerationTaskMapper.selectList(new LambdaQueryWrapper<AiGenerationTaskEntity>()
                .in(AiGenerationTaskEntity::getWorkspaceId, workspaceIds)
                .orderByDesc(AiGenerationTaskEntity::getUpdatedAt)
                .orderByDesc(AiGenerationTaskEntity::getId));

        return tasks.stream()
                .map(task -> toResponse(task, workspaces.stream()
                        .filter(item -> item.getId().equals(task.getWorkspaceId()))
                        .findFirst()
                        .orElseGet(() -> workspaceService.requireWorkspaceById(task.getWorkspaceId()))))
                .toList();
    }

    public AiGenerationTaskResponse getTask(String taskId, String workspaceCode) {
        AiGenerationTaskEntity entity = requireTask(taskId);
        WorkspaceEntity workspace = workspaceService.requireReadableWorkspace(
                workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode()
        );
        validateReadableWorkspaceScope(workspaceCode, workspace);
        return toResponse(entity, workspace);
    }

    public AiGenerationTaskResponse cancelTask(String taskId, String workspaceCode) {
        AiGenerationTaskEntity entity = requireTask(taskId);
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(
                workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode()
        );
        validateReadableWorkspaceScope(workspaceCode, workspace);
        if (!RUNNING_STATUSES.contains(entity.getStatus())) {
            return toResponse(entity, workspace);
        }
        markCanceled(entity, "任务已取消，后续步骤不再继续执行。");
        return toResponse(requireTask(taskId), workspace);
    }

    public AiGenerationTaskResponse retryTask(String taskId, String workspaceCode) {
        AiGenerationTaskEntity source = requireTask(taskId);
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(
                workspaceService.requireWorkspaceById(source.getWorkspaceId()).getWorkspaceCode()
        );
        validateReadableWorkspaceScope(workspaceCode, workspace);
        if (!"FAILED".equals(source.getStatus())) {
            throw new BadRequestException("Only failed tasks can be retried");
        }

        AiGenerationTaskEntity entity = new AiGenerationTaskEntity();
        LocalDateTime now = LocalDateTime.now();
        entity.setTaskId(generateTaskId());
        entity.setWorkspaceId(source.getWorkspaceId());
        entity.setRequirementTitle(source.getRequirementTitle());
        entity.setRequirementContent(source.getRequirementContent());
        entity.setOutputMode(source.getOutputMode());
        entity.setStatus("PENDING");
        entity.setCurrentStep(1);
        entity.setStepMessage("已创建重试任务，等待重新生成测试用例。");
        entity.setErrorMessage(null);
        entity.setDirectoryId(source.getDirectoryId());
        entity.setDirectoryName(source.getDirectoryName());
        entity.setProvider(null);
        entity.setModel(null);
        entity.setAssetIdsJson(source.getAssetIdsJson());
        entity.setWarningsJson(writeValue(List.of()));
        entity.setInvalidCasesJson(writeValue(List.of()));
        entity.setGeneratedCasesJson(writeValue(List.of()));
        entity.setReviewResultJson(null);
        entity.setAdoptedCaseIndexesJson(writeValue(List.of()));
        entity.setDeletedCaseIndexesJson(writeValue(List.of()));
        entity.setSavedCaseCount(0);
        entity.setGeneratedCount(0);
        entity.setCancelRequested(0);
        entity.setSourceTaskId(source.getTaskId());
        entity.setFinishedAt(null);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        aiGenerationTaskMapper.insert(entity);

        return toResponse(entity, workspace);
    }

    public AiGenerationTaskResponse updateTask(String taskId, String workspaceCode, UpdateAiGenerationTaskRequest request) {
        AiGenerationTaskEntity entity = requireTask(taskId);
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(
                workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode()
        );
        validateReadableWorkspaceScope(workspaceCode, workspace);
        if (request.directoryId() != null) {
            validateDirectory(workspace, request.directoryId());
            entity.setDirectoryId(request.directoryId());
        }
        if (request.directoryName() != null) {
            entity.setDirectoryName(blankToNull(request.directoryName()));
        }
        if (request.adoptedCaseIndexes() != null) {
            entity.setAdoptedCaseIndexesJson(writeValue(normalizeIndexes(request.adoptedCaseIndexes())));
        }
        if (request.deletedCaseIndexes() != null) {
            entity.setDeletedCaseIndexesJson(writeValue(normalizeIndexes(request.deletedCaseIndexes())));
        }
        if (request.savedCaseCount() != null) {
            entity.setSavedCaseCount(Math.max(request.savedCaseCount(), 0));
        }
        entity.setUpdatedAt(LocalDateTime.now());
        aiGenerationTaskMapper.updateById(entity);
        return toResponse(entity, workspace);
    }

    public void deleteTask(String taskId, String workspaceCode) {
        AiGenerationTaskEntity entity = requireTask(taskId);
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(
                workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode()
        );
        validateReadableWorkspaceScope(workspaceCode, workspace);
        aiGenerationTaskMapper.deleteById(entity.getId());
    }

    public void executeTask(String taskId, String workspaceCode) {
        AiGenerationTaskEntity entity = requireTask(taskId);
        if (isCanceled(entity)) {
            markCanceled(entity, "任务已取消，未进入执行阶段。");
            return;
        }

        try {
            transitionToGenerating(entity);
            GenerateAiCasesResponse generation = aiCaseService.generateCases(workspaceCode, new GenerateAiCasesRequest(
                    workspaceCode,
                    entity.getRequirementTitle(),
                    entity.getRequirementContent(),
                    null,
                    null,
                    readValue(entity.getAssetIdsJson(), new TypeReference<List<Long>>() {}, List.of()),
                    List.of(),
                    null,
                    null
            ));

            entity = requireTask(taskId);
            if (isCanceled(entity)) {
                markCanceled(entity, "任务已取消，生成结果未继续写入。");
                return;
            }

            entity.setProvider(generation.provider());
            entity.setModel(generation.model());
            entity.setGeneratedCount(generation.actualGeneratedCount() == null ? 0 : generation.actualGeneratedCount());
            entity.setWarningsJson(writeValue(generation.warnings()));
            entity.setInvalidCasesJson(writeValue(generation.invalidCases()));
            entity.setGeneratedCasesJson(writeValue(generation.generatedCases()));
            entity.setStatus("REVIEWING");
            entity.setCurrentStep(3);
            entity.setStepMessage("已完成用例生成，正在进行 AI 自动评审。");
            entity.setUpdatedAt(LocalDateTime.now());
            aiGenerationTaskMapper.updateById(entity);

            AiReviewResult review = aiCaseService.reviewGeneratedCases(workspaceCode, new ReviewAiGeneratedCasesRequest(
                    entity.getRequirementTitle(),
                    entity.getRequirementContent(),
                    null,
                    generation.generatedCases().stream()
                            .map(item -> new AiExistingCaseItem(
                                    item.title(),
                                    item.caseType(),
                                    item.priority(),
                                    item.precondition(),
                                    item.steps(),
                                    item.expectedResult()
                            ))
                            .toList()
            ));

            entity = requireTask(taskId);
            if (isCanceled(entity)) {
                markCanceled(entity, "任务已取消，评审结果未继续写入。");
                return;
            }

            entity.setStatus("COMPLETED");
            entity.setCurrentStep(4);
            entity.setStepMessage("任务已完成，可前往记录页查看生成结果并继续处理。");
            entity.setReviewResultJson(writeValue(review));
            entity.setFinishedAt(LocalDateTime.now());
            entity.setUpdatedAt(LocalDateTime.now());
            aiGenerationTaskMapper.updateById(entity);
        } catch (Exception exception) {
            AiGenerationTaskEntity latest = requireTask(taskId);
            if (isCanceled(latest)) {
                markCanceled(latest, "任务已取消，错误结果已忽略。");
                return;
            }
            latest.setStatus("FAILED");
            latest.setCurrentStep(Math.min(latest.getCurrentStep() == null ? 2 : latest.getCurrentStep(), 3));
            latest.setStepMessage("任务执行失败，请检查 AI 配置或稍后重试。");
            latest.setErrorMessage(exception.getMessage());
            latest.setFinishedAt(LocalDateTime.now());
            latest.setUpdatedAt(LocalDateTime.now());
            aiGenerationTaskMapper.updateById(latest);
        }
    }

    private void transitionToGenerating(AiGenerationTaskEntity entity) {
        entity.setStatus("GENERATING");
        entity.setCurrentStep(2);
        entity.setStepMessage("正在根据需求生成测试用例。");
        entity.setErrorMessage(null);
        entity.setUpdatedAt(LocalDateTime.now());
        aiGenerationTaskMapper.updateById(entity);
    }

    private void markCanceled(AiGenerationTaskEntity entity, String stepMessage) {
        entity.setCancelRequested(1);
        entity.setStatus("CANCELED");
        entity.setStepMessage(stepMessage);
        entity.setFinishedAt(entity.getFinishedAt() == null ? LocalDateTime.now() : entity.getFinishedAt());
        entity.setUpdatedAt(LocalDateTime.now());
        aiGenerationTaskMapper.updateById(entity);
    }

    private boolean isCanceled(AiGenerationTaskEntity entity) {
        return entity.getCancelRequested() != null && entity.getCancelRequested() == 1;
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

    private void validateDirectory(WorkspaceEntity workspace, Long directoryId) {
        if (directoryId == null) {
            return;
        }
        CaseDirectoryEntity entity = caseDirectoryMapper.selectById(directoryId);
        if (entity == null || !entity.getWorkspaceId().equals(workspace.getId())) {
            throw new BadRequestException("Case directory does not belong to the target workspace");
        }
    }

    private List<WorkspaceEntity> resolveReadableWorkspaces(String workspaceCode) {
        String normalized = WorkspaceScope.normalize(workspaceCode);
        if (WorkspaceScope.isAll(normalized)) {
            return workspaceService.listReadableWorkspaceEntities();
        }
        return List.of(workspaceService.requireReadableWorkspace(normalized));
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

    private String generateTaskId() {
        return "TASK_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private String normalizeOutputMode(String outputMode) {
        return outputMode == null ? "STREAM" : outputMode.trim().toUpperCase(Locale.ROOT);
    }

    private void validateOutputMode(String outputMode) {
        String normalized = normalizeOutputMode(outputMode);
        if (!"STREAM".equals(normalized) && !"COMPLETE".equals(normalized)) {
            throw new BadRequestException("Output mode must be STREAM or COMPLETE");
        }
    }

    private String blankToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private List<Integer> normalizeIndexes(List<Integer> indexes) {
        return indexes == null ? List.of() : indexes.stream()
                .filter(item -> item != null && item >= 0)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private String writeValue(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BadRequestException("Failed to serialize AI generation task data");
        }
    }

    private <T> T readValue(String raw, TypeReference<T> typeReference, T fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return OBJECT_MAPPER.readValue(raw, typeReference);
        } catch (JsonProcessingException exception) {
            return fallback;
        }
    }

    private AiReviewResult readReviewResult(String raw) {
        return readValue(raw, new TypeReference<AiReviewResult>() {}, null);
    }

    private AiGenerationTaskResponse toResponse(AiGenerationTaskEntity entity, WorkspaceEntity workspace) {
        return new AiGenerationTaskResponse(
                entity.getTaskId(),
                workspace.getWorkspaceCode(),
                workspace.getWorkspaceName(),
                entity.getRequirementTitle(),
                entity.getRequirementContent(),
                entity.getOutputMode(),
                entity.getStatus(),
                entity.getCurrentStep(),
                entity.getStepMessage(),
                entity.getErrorMessage(),
                entity.getDirectoryId(),
                entity.getDirectoryName(),
                entity.getProvider(),
                entity.getModel(),
                entity.getGeneratedCount() == null ? 0 : entity.getGeneratedCount(),
                entity.getSavedCaseCount() == null ? 0 : entity.getSavedCaseCount(),
                readValue(entity.getWarningsJson(), new TypeReference<List<String>>() {}, List.of()),
                readValue(entity.getInvalidCasesJson(), new TypeReference<List<AiInvalidCaseItem>>() {}, List.of()),
                readValue(entity.getGeneratedCasesJson(), new TypeReference<List<GeneratedAiCaseItem>>() {}, List.of()),
                readReviewResult(entity.getReviewResultJson()),
                readValue(entity.getAdoptedCaseIndexesJson(), new TypeReference<List<Integer>>() {}, List.of()),
                readValue(entity.getDeletedCaseIndexesJson(), new TypeReference<List<Integer>>() {}, List.of()),
                entity.getCancelRequested() != null && entity.getCancelRequested() == 1,
                entity.getSourceTaskId(),
                entity.getCreatedAt() == null ? null : entity.getCreatedAt().toString(),
                entity.getUpdatedAt() == null ? null : entity.getUpdatedAt().toString(),
                entity.getFinishedAt() == null ? null : entity.getFinishedAt().toString()
        );
    }
}
