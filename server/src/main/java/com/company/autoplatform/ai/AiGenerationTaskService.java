package com.company.autoplatform.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.autoplatform.auth.CurrentUserContext;
import com.company.autoplatform.casecenter.CaseDirectoryEntity;
import com.company.autoplatform.casecenter.CaseDirectoryMapper;
import com.company.autoplatform.common.BadRequestException;
import com.company.autoplatform.user.UserEntity;
import com.company.autoplatform.user.UserService;
import com.company.autoplatform.workspace.WorkspaceEntity;
import com.company.autoplatform.workspace.WorkspaceScope;
import com.company.autoplatform.workspace.WorkspaceService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class AiGenerationTaskService {

    private static final List<String> RUNNING_STATUSES = List.of("PENDING", "GENERATING", "REVIEWING");
    private static final List<String> TERMINAL_STATUSES = List.of("COMPLETED", "FAILED", "CANCELED");
    private static final int RAW_OUTPUT_LIMIT = 12000;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AiGenerationTaskMapper aiGenerationTaskMapper;
    private final AiCaseService aiCaseService;
    private final AiGenerationTaskEventService eventService;
    private final WorkspaceService workspaceService;
    private final CaseDirectoryMapper caseDirectoryMapper;
    private final UserService userService;

    public AiGenerationTaskService(
            AiGenerationTaskMapper aiGenerationTaskMapper,
            AiCaseService aiCaseService,
            AiGenerationTaskEventService eventService,
            WorkspaceService workspaceService,
            CaseDirectoryMapper caseDirectoryMapper,
            UserService userService
    ) {
        this.aiGenerationTaskMapper = aiGenerationTaskMapper;
        this.aiCaseService = aiCaseService;
        this.eventService = eventService;
        this.workspaceService = workspaceService;
        this.caseDirectoryMapper = caseDirectoryMapper;
        this.userService = userService;
    }

    public AiGenerationTaskResponse createTask(String headerWorkspaceCode, CreateAiGenerationTaskRequest request) {
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(
                workspaceService.resolveTargetWorkspace(headerWorkspaceCode, request.workspaceCode())
        );
        validateOutputMode(request.outputMode());
        validateDirectory(workspace, request.directoryId());
        aiCaseService.validateGenerationImageSupport(request.assetIds());

        AiGenerationTaskEntity entity = new AiGenerationTaskEntity();
        LocalDateTime now = LocalDateTime.now();
        Long currentUserId = CurrentUserContext.get();
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
        entity.setGenerationRawOutput(null);
        entity.setReviewRawOutput(null);
        entity.setAdoptedCaseIndexesJson(writeValue(List.of()));
        entity.setDeletedCaseIndexesJson(writeValue(List.of()));
        entity.setSavedCaseCount(0);
        entity.setGeneratedCount(0);
        entity.setCancelRequested(0);
        entity.setSourceTaskId(null);
        entity.setFinishedAt(null);
        entity.setCreatedBy(currentUserId);
        entity.setUpdatedBy(currentUserId);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        aiGenerationTaskMapper.insert(entity);
        appendEvent(entity.getTaskId(), "TASK_CREATED", "SETUP", "INFO", "任务已创建，等待开始生成", null, null, null, null, null);
        int ignoredAssetCount = request.ignoredAssetCount() == null ? 0 : Math.max(request.ignoredAssetCount(), 0);
        if (ignoredAssetCount > 0) {
            appendEvent(
                    entity.getTaskId(),
                    "IMAGE_ASSETS_IGNORED",
                    "SETUP",
                    "WARN",
                    "当前生成模型不支持图片输入，已忽略 " + ignoredAssetCount + " 个图片素材，仅基于文本需求继续生成。",
                    null,
                    null,
                    null,
                    null,
                    writeValue(Map.of("ignoredAssetCount", ignoredAssetCount))
            );
        }

        return toResponse(entity, workspace, collectUserMap(List.of(entity)));
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
        Map<Long, UserEntity> userMap = collectUserMap(tasks);

        return tasks.stream()
                .map(task -> toResponse(task, workspaces.stream()
                        .filter(item -> item.getId().equals(task.getWorkspaceId()))
                        .findFirst()
                        .orElseGet(() -> workspaceService.requireWorkspaceById(task.getWorkspaceId())), userMap, false))
                .toList();
    }

    public AiGenerationTaskResponse getTask(String taskId, String workspaceCode) {
        AiGenerationTaskEntity entity = requireTask(taskId);
        WorkspaceEntity workspace = workspaceService.requireReadableWorkspace(
                workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode()
        );
        validateReadableWorkspaceScope(workspaceCode, workspace);
        return toResponse(entity, workspace, collectUserMap(List.of(entity)));
    }

    public AiGenerationTaskResponse cancelTask(String taskId, String workspaceCode) {
        AiGenerationTaskEntity entity = requireTask(taskId);
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(
                workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode()
        );
        validateReadableWorkspaceScope(workspaceCode, workspace);
        if (!RUNNING_STATUSES.contains(entity.getStatus())) {
            return toResponse(entity, workspace, collectUserMap(List.of(entity)));
        }
        entity.setUpdatedBy(CurrentUserContext.get());
        markCanceled(entity, "任务已取消，后续步骤不再继续执行。");
        AiGenerationTaskEntity latest = requireTask(taskId);
        return toResponse(latest, workspace, collectUserMap(List.of(latest)));
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
        Long currentUserId = CurrentUserContext.get();
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
        entity.setGenerationRawOutput(null);
        entity.setReviewRawOutput(null);
        entity.setAdoptedCaseIndexesJson(writeValue(List.of()));
        entity.setDeletedCaseIndexesJson(writeValue(List.of()));
        entity.setSavedCaseCount(0);
        entity.setGeneratedCount(0);
        entity.setCancelRequested(0);
        entity.setSourceTaskId(source.getTaskId());
        entity.setFinishedAt(null);
        entity.setCreatedBy(currentUserId);
        entity.setUpdatedBy(currentUserId);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        aiGenerationTaskMapper.insert(entity);
        appendEvent(entity.getTaskId(), "TASK_RETRIED", "SETUP", "INFO", "已创建重试任务，等待重新生成", null, null, null, null, writeValue(Map.of("sourceTaskId", source.getTaskId())));

        return toResponse(entity, workspace, collectUserMap(List.of(entity)));
    }

    public AiGenerationTaskResponse updateTask(String taskId, String workspaceCode, UpdateAiGenerationTaskRequest request) {
        AiGenerationTaskEntity entity = requireTask(taskId);
        WorkspaceEntity currentWorkspace = workspaceService.requireWorkspaceById(entity.getWorkspaceId());
        validateReadableWorkspaceScope(workspaceCode, currentWorkspace);
        WorkspaceEntity workspace = request.workspaceCode() == null || request.workspaceCode().isBlank()
                ? workspaceService.requireWritableWorkspace(currentWorkspace.getWorkspaceCode())
                : workspaceService.requireWritableWorkspace(request.workspaceCode().trim());
        if (!workspace.getId().equals(entity.getWorkspaceId())) {
            entity.setWorkspaceId(workspace.getId());
        }
        if (request.directoryId() != null) {
            validateDirectory(workspace, request.directoryId());
            entity.setDirectoryId(request.directoryId());
        }
        if (request.directoryName() != null) {
            entity.setDirectoryName(blankToNull(request.directoryName()));
        }
        if (request.generatedCases() != null) {
            entity.setGeneratedCasesJson(writeValue(request.generatedCases()));
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
        entity.setUpdatedBy(CurrentUserContext.get());
        entity.setUpdatedAt(LocalDateTime.now());
        aiGenerationTaskMapper.updateById(entity);
        return toResponse(entity, workspace, collectUserMap(List.of(entity)));
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
            if ("COMPLETE".equals(normalizeOutputMode(entity.getOutputMode()))) {
                executeCompleteTask(entity, workspaceCode);
            } else {
                executeStreamTask(entity, workspaceCode);
            }
        } catch (TaskCanceledException exception) {
            markCanceled(requireTask(taskId), exception.getMessage());
        } catch (Exception exception) {
            markFailed(taskId, exception);
        }
    }

    private void executeCompleteTask(AiGenerationTaskEntity entity, String workspaceCode) {
        appendEvent(entity.getTaskId(), "TASK_STARTED", "SETUP", "INFO", "任务开始执行完整输出链路", null, null, null, null, null);
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

        entity = requireTask(entity.getTaskId());
        if (isCanceled(entity)) {
            throw new TaskCanceledException("任务已取消，生成结果未继续写入。");
        }

        entity.setProvider(generation.provider());
        entity.setModel(generation.model());
        entity.setGeneratedCount(generation.actualGeneratedCount() == null ? 0 : generation.actualGeneratedCount());
        entity.setWarningsJson(writeValue(generation.warnings()));
        entity.setInvalidCasesJson(writeValue(generation.invalidCases()));
        entity.setGeneratedCasesJson(writeValue(generation.generatedCases()));
        entity.setGenerationRawOutput(limitRawOutput(generation.rawContent()));
        entity.setStatus("REVIEWING");
        entity.setCurrentStep(3);
        entity.setStepMessage("已完成用例生成，正在进行 AI 自动评审。");
        entity.setUpdatedAt(LocalDateTime.now());
        aiGenerationTaskMapper.updateById(entity);
        if (generation.ignoredImages()) {
            appendEvent(entity.getTaskId(), "IMAGE_ASSETS_IGNORED", "GENERATING", "WARN", "当前生成模型实际不支持图片输入，已自动忽略图片素材并改为纯文本生成。", null, null, generation.provider(), generation.model(), null);
        }
        appendEvent(entity.getTaskId(), "GENERATION_COMPLETED", "GENERATING", "INFO", "完整输出已生成 " + generation.generatedCases().size() + " 条用例", null, null, generation.provider(), generation.model(), null);
        appendEvent(entity.getTaskId(), "REVIEW_STARTED", "REVIEWING", "INFO", "开始执行 AI 自动评审", null, null, null, null, null);

        AiReviewResult review = aiCaseService.reviewGeneratedCases(workspaceCode, new ReviewAiGeneratedCasesRequest(
                entity.getRequirementTitle(),
                entity.getRequirementContent(),
                null,
                generation.remainingCoverageGaps(),
                generation.generatedCases().stream()
                        .map(this::toExistingCaseItem)
                        .toList()
        ));

        entity = requireTask(entity.getTaskId());
        if (isCanceled(entity)) {
            throw new TaskCanceledException("任务已取消，评审结果未继续写入。");
        }

        entity.setStatus("COMPLETED");
        entity.setCurrentStep(4);
        entity.setStepMessage("任务已完成，可在记录详情中查看生成结果并继续处理。");
        List<GeneratedAiCaseItem> finalCases = mergeCompleteReviewResult(generation.generatedCases(), review);
        entity.setGeneratedCasesJson(writeValue(finalCases));
        entity.setGeneratedCount(finalCases.size());
        entity.setReviewResultJson(writeValue(review));
        entity.setReviewRawOutput(limitRawOutput(review.rawContent()));
        entity.setFinishedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        aiGenerationTaskMapper.updateById(entity);
        appendCompleteReviewEvents(entity.getTaskId(), finalCases, review, generation.provider(), generation.model());
        appendEvent(entity.getTaskId(), "TASK_COMPLETED", "DONE", "INFO", "完整输出任务已完成", null, null, generation.provider(), generation.model(), null);
    }

    private void executeStreamTask(AiGenerationTaskEntity entity, String workspaceCode) {
        String taskId = entity.getTaskId();
        appendEvent(taskId, "TASK_STARTED", "SETUP", "INFO", "任务开始执行实时流式输出链路", null, null, null, null, null);
        transitionToGenerating(entity);
        List<GeneratedAiCaseItem> generatedCases = new ArrayList<>();
        AiCaseService.StreamedGenerateCasesResult generation = aiCaseService.streamGenerateCases(
                workspaceCode,
                new GenerateAiCasesRequest(
                        workspaceCode,
                        entity.getRequirementTitle(),
                        entity.getRequirementContent(),
                        null,
                        null,
                        readValue(entity.getAssetIdsJson(), new TypeReference<List<Long>>() {}, List.of()),
                        List.of(),
                        null,
                        null
                ),
                modelInfo -> {
                    AiGenerationTaskEntity latest = requireTask(taskId);
                    latest.setProvider(modelInfo.provider());
                    latest.setModel(modelInfo.model());
                    latest.setUpdatedAt(LocalDateTime.now());
                    aiGenerationTaskMapper.updateById(latest);
                    appendEvent(taskId, "GENERATION_MODEL_READY", "GENERATING", "INFO", "生成模型已就绪：" + modelInfo.model(), null, null, modelInfo.provider(), modelInfo.model(), null);
                },
                update -> {
                    AiGenerationTaskEntity latest = requireTask(taskId);
                    if (isCanceled(latest)) {
                        throw new TaskCanceledException("任务已取消，停止接收生成流。");
                    }
                    generatedCases.add(update.item());
                    persistGeneratedCasesSnapshot(latest, generatedCases, update.rawOutput());
                    appendEvent(taskId, "CASE_GENERATED", "GENERATING", "INFO", buildGeneratedCaseEventMessage(update.itemIndex(), update.item()), update.itemIndex(), update.item().title(), latest.getProvider(), latest.getModel(), writeValue(update.item()));
                }
        );

        entity = requireTask(taskId);
        if (isCanceled(entity)) {
            throw new TaskCanceledException("任务已取消，生成结果未继续写入。");
        }
        generatedCases.clear();
        generatedCases.addAll(generation.generatedCases());
        entity.setProvider(generation.provider());
        entity.setModel(generation.model());
        entity.setGeneratedCount(generation.actualGeneratedCount() == null ? generatedCases.size() : generation.actualGeneratedCount());
        entity.setWarningsJson(writeValue(generation.warnings()));
        entity.setInvalidCasesJson(writeValue(generation.invalidCases()));
        entity.setGeneratedCasesJson(writeValue(generatedCases));
        entity.setGenerationRawOutput(limitRawOutput(generation.rawContent()));
        entity.setStatus("REVIEWING");
        entity.setCurrentStep(3);
        entity.setStepMessage("已完成用例生成，正在进行 AI 自动评审。");
        entity.setUpdatedAt(LocalDateTime.now());
        aiGenerationTaskMapper.updateById(entity);
        if (generation.ignoredImages()) {
            appendEvent(taskId, "IMAGE_ASSETS_IGNORED", "GENERATING", "WARN", "当前生成模型实际不支持图片输入，已自动忽略图片素材并改为纯文本生成。", null, null, generation.provider(), generation.model(), null);
        }
        if (generation.fallbackToComplete()) {
            appendEvent(
                    taskId,
                    "GENERATION_STREAM_FALLBACK",
                    "GENERATING",
                    "WARN",
                    "当前生成模型不支持实时流式或流式请求失败，已降级为完整输出。",
                    null,
                    null,
                    generation.provider(),
                    generation.model(),
                    writeValue(Map.of("reason", blankToNull(generation.fallbackReason()) == null ? "" : generation.fallbackReason()))
            );
        }
        appendEvent(taskId, "GENERATION_COMPLETED", "GENERATING", "INFO", "已完成 " + generatedCases.size() + " 条用例生成", null, null, generation.provider(), generation.model(), null);

        final String[] reviewProvider = new String[]{null};
        final String[] reviewModel = new String[]{null};
        AiCaseService.StreamedReviewResult review = aiCaseService.streamReviewGeneratedCases(
                workspaceCode,
                new ReviewAiGeneratedCasesRequest(
                        entity.getRequirementTitle(),
                        entity.getRequirementContent(),
                        null,
                        generation.remainingCoverageGaps(),
                        generatedCases.stream().map(this::toExistingCaseItem).toList()
                ),
                modelInfo -> {
                    reviewProvider[0] = modelInfo.provider();
                    reviewModel[0] = modelInfo.model();
                    appendEvent(taskId, "REVIEW_STARTED", "REVIEWING", "INFO", "评审模型已就绪：" + modelInfo.model(), null, null, modelInfo.provider(), modelInfo.model(), null);
                },
                update -> {
                    AiGenerationTaskEntity latest = requireTask(taskId);
                    if (isCanceled(latest)) {
                        throw new TaskCanceledException("任务已取消，停止接收评审流。");
                    }
                    if (update.itemIndex() == null || update.itemIndex() < 0 || update.itemIndex() >= generatedCases.size()) {
                        return;
                    }
                    GeneratedAiCaseItem reviewed = applyReviewUpdate(generatedCases.get(update.itemIndex()), update);
                    generatedCases.set(update.itemIndex(), reviewed);
                    latest.setGeneratedCasesJson(writeValue(generatedCases));
                    latest.setReviewRawOutput(limitRawOutput(update.rawOutput()));
                    latest.setUpdatedAt(LocalDateTime.now());
                    aiGenerationTaskMapper.updateById(latest);
                    appendEvent(taskId, "CASE_REVIEWED", "REVIEWING", "INFO", buildReviewedCaseEventMessage(update.itemIndex(), update.status(), update.summary(), update.coverageComment(), update.evidenceComment()), update.itemIndex(), reviewed.title(), reviewProvider[0], reviewModel[0], writeValue(Map.of(
                            "status", update.status(),
                            "summary", update.summary() == null ? "" : update.summary(),
                            "coverageComment", update.coverageComment() == null ? "" : update.coverageComment(),
                            "evidenceComment", update.evidenceComment() == null ? "" : update.evidenceComment(),
                            "reviewComment", update.reviewComment() == null ? "" : update.reviewComment(),
                            "optimizationReason", update.optimizationReason() == null ? "" : update.optimizationReason(),
                            "coverageGap", update.coverageGap() == null ? "" : update.coverageGap()
                    )));
                }
        );

        entity = requireTask(taskId);
        if (isCanceled(entity)) {
            throw new TaskCanceledException("任务已取消，评审结果未继续写入。");
        }
        entity.setStatus("COMPLETED");
        entity.setCurrentStep(4);
        entity.setStepMessage("任务已完成，可在记录详情中查看生成结果并继续处理。");
        entity.setGeneratedCasesJson(writeValue(generatedCases));
        entity.setReviewResultJson(writeValue(review.reviewResult()));
        entity.setReviewRawOutput(limitRawOutput(review.rawContent()));
        entity.setFinishedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        aiGenerationTaskMapper.updateById(entity);
        if (review.fallbackToComplete()) {
            appendEvent(
                    taskId,
                    "REVIEW_STREAM_FALLBACK",
                    "REVIEWING",
                    "WARN",
                    "当前评审模型不支持实时流式或流式请求失败，已降级为完整输出。",
                    null,
                    null,
                    review.provider(),
                    review.model(),
                    writeValue(Map.of("reason", blankToNull(review.fallbackReason()) == null ? "" : review.fallbackReason()))
            );
        }
        appendEvent(taskId, "TASK_COMPLETED", "DONE", "INFO", "实时流式任务已完成", null, null, review.provider(), review.model(), null);
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
        appendEvent(entity.getTaskId(), "TASK_CANCELED", "DONE", "WARN", stepMessage, null, null, entity.getProvider(), entity.getModel(), null);
    }

    public StreamingResponseBody streamTaskEvents(String taskId, String workspaceCode) {
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

    private void persistGeneratedCasesSnapshot(AiGenerationTaskEntity entity, List<GeneratedAiCaseItem> generatedCases, String rawOutput) {
        entity.setGeneratedCasesJson(writeValue(generatedCases));
        entity.setGeneratedCount(generatedCases.size());
        entity.setGenerationRawOutput(limitRawOutput(rawOutput));
        entity.setUpdatedAt(LocalDateTime.now());
        aiGenerationTaskMapper.updateById(entity);
    }

    private AiExistingCaseItem toExistingCaseItem(GeneratedAiCaseItem item) {
        return new AiExistingCaseItem(
                item.title(),
                item.caseType(),
                item.priority(),
                item.precondition(),
                item.steps(),
                item.expectedResult(),
                item.testAngle(),
                item.generationReason(),
                item.requirementEvidence()
        );
    }

    private GeneratedAiCaseItem withReview(GeneratedAiCaseItem item, String status, String summary) {
        return new GeneratedAiCaseItem(
                item.title(),
                item.caseType(),
                item.priority(),
                item.precondition(),
                item.steps(),
                item.expectedResult(),
                item.riskNotes(),
                item.testAngle(),
                item.generationReason(),
                item.requirementEvidence(),
                item.aiSource() == null ? "INITIAL" : item.aiSource(),
                item.reviewComment(),
                item.optimizationReason(),
                item.supplementReason(),
                item.coverageGap(),
                item.originalCaseSnapshot(),
                item.warnings(),
                status,
                summary,
                item.manualEdited(),
                item.manualEditedByName(),
                item.manualEditedAt()
        );
    }

    private GeneratedAiCaseItem applyReviewUpdate(GeneratedAiCaseItem original, AiCaseService.ReviewCaseStreamUpdate update) {
        GeneratedAiCaseItem base = update.optimizedCase() != null && "OPTIMIZED".equals(update.status())
                ? update.optimizedCase()
                : original;
        return new GeneratedAiCaseItem(
                base.title(),
                base.caseType(),
                base.priority(),
                base.precondition(),
                base.steps(),
                base.expectedResult(),
                base.riskNotes(),
                firstNonBlank(base.testAngle(), original.testAngle()),
                firstNonBlank(base.generationReason(), original.generationReason()),
                firstNonBlank(base.requirementEvidence(), original.requirementEvidence()),
                "OPTIMIZED".equals(update.status()) && update.optimizedCase() != null ? "REVIEW_OPTIMIZED" : firstNonBlank(original.aiSource(), "INITIAL"),
                firstNonBlank(update.reviewComment(), update.summary(), base.reviewComment(), original.reviewComment()),
                firstNonBlank(update.optimizationReason(), base.optimizationReason(), original.optimizationReason()),
                firstNonBlank(base.supplementReason(), original.supplementReason()),
                firstNonBlank(update.coverageGap(), base.coverageGap(), original.coverageGap()),
                "OPTIMIZED".equals(update.status()) && update.optimizedCase() != null ? original : original.originalCaseSnapshot(),
                base.warnings() == null ? original.warnings() : base.warnings(),
                update.status(),
                update.summary(),
                original.manualEdited(),
                original.manualEditedByName(),
                original.manualEditedAt()
        );
    }

    private List<GeneratedAiCaseItem> mergeCompleteReviewResult(List<GeneratedAiCaseItem> generatedCases, AiReviewResult review) {
        List<GeneratedAiCaseItem> finalCases = new ArrayList<>();
        for (GeneratedAiCaseItem item : generatedCases) {
            finalCases.add(withSource(item, "INITIAL"));
        }
        if (review == null) {
            return finalCases;
        }
        for (AiReviewCaseDecision decision : review.caseDecisions() == null ? List.<AiReviewCaseDecision>of() : review.caseDecisions()) {
            if (decision.caseIndex() == null || decision.caseIndex() < 0 || decision.caseIndex() >= finalCases.size()) {
                continue;
            }
            GeneratedAiCaseItem current = finalCases.get(decision.caseIndex());
            GeneratedAiCaseItem next = applyReviewDecision(current, decision);
            finalCases.set(decision.caseIndex(), next);
        }
        for (GeneratedAiCaseItem item : review.supplementCases() == null ? List.<GeneratedAiCaseItem>of() : review.supplementCases()) {
            if (finalCases.size() >= AiCaseService.FINAL_MAX_CASES) {
                break;
            }
            finalCases.add(withSupplementMetadata(item));
        }
        return finalCases;
    }

    private GeneratedAiCaseItem applyReviewDecision(GeneratedAiCaseItem original, AiReviewCaseDecision decision) {
        String status = decision.status() == null ? "CONFIRM_REQUIRED" : decision.status();
        GeneratedAiCaseItem base = "OPTIMIZED".equals(status) && decision.optimizedCase() != null
                ? decision.optimizedCase()
                : original;
        return new GeneratedAiCaseItem(
                base.title(),
                base.caseType(),
                base.priority(),
                base.precondition(),
                base.steps(),
                base.expectedResult(),
                base.riskNotes(),
                firstNonBlank(base.testAngle(), original.testAngle()),
                firstNonBlank(base.generationReason(), original.generationReason()),
                firstNonBlank(base.requirementEvidence(), original.requirementEvidence()),
                "OPTIMIZED".equals(status) && decision.optimizedCase() != null ? "REVIEW_OPTIMIZED" : firstNonBlank(original.aiSource(), "INITIAL"),
                firstNonBlank(decision.reviewComment(), decision.summary(), base.reviewComment(), original.reviewComment()),
                firstNonBlank(decision.optimizationReason(), base.optimizationReason(), original.optimizationReason()),
                firstNonBlank(base.supplementReason(), original.supplementReason()),
                firstNonBlank(decision.coverageGap(), base.coverageGap(), original.coverageGap()),
                "OPTIMIZED".equals(status) && decision.optimizedCase() != null ? original : original.originalCaseSnapshot(),
                base.warnings() == null ? original.warnings() : base.warnings(),
                status,
                decision.summary(),
                original.manualEdited(),
                original.manualEditedByName(),
                original.manualEditedAt()
        );
    }

    private GeneratedAiCaseItem withSource(GeneratedAiCaseItem item, String source) {
        return new GeneratedAiCaseItem(
                item.title(), item.caseType(), item.priority(), item.precondition(), item.steps(), item.expectedResult(),
                item.riskNotes(), item.testAngle(), item.generationReason(), item.requirementEvidence(),
                firstNonBlank(item.aiSource(), source), item.reviewComment(), item.optimizationReason(), item.supplementReason(),
                item.coverageGap(), item.originalCaseSnapshot(), item.warnings(), item.aiReviewStatus(), item.aiReviewSummary(),
                item.manualEdited(), item.manualEditedByName(), item.manualEditedAt()
        );
    }

    private GeneratedAiCaseItem withSupplementMetadata(GeneratedAiCaseItem item) {
        return new GeneratedAiCaseItem(
                item.title(), item.caseType(), item.priority(), item.precondition(), item.steps(), item.expectedResult(),
                item.riskNotes(), item.testAngle(), item.generationReason(), item.requirementEvidence(),
                "REVIEW_SUPPLEMENTED", item.reviewComment(), item.optimizationReason(), item.supplementReason(),
                item.coverageGap(), null, item.warnings(), "SUPPLEMENTED", firstNonBlank(item.aiReviewSummary(), item.supplementReason(), item.coverageGap()),
                item.manualEdited(), item.manualEditedByName(), item.manualEditedAt()
        );
    }

    private void appendCompleteReviewEvents(String taskId, List<GeneratedAiCaseItem> finalCases, AiReviewResult review, String provider, String model) {
        long optimized = finalCases.stream().filter(item -> "OPTIMIZED".equals(item.aiReviewStatus())).count();
        long supplemented = finalCases.stream().filter(item -> "SUPPLEMENTED".equals(item.aiReviewStatus())).count();
        long notRecommended = finalCases.stream().filter(item -> "NOT_RECOMMENDED".equals(item.aiReviewStatus())).count();
        appendEvent(taskId, "REVIEW_COMPLETED", "REVIEWING", "INFO", "AI review completed: optimized " + optimized + ", supplemented " + supplemented + ", not recommended " + notRecommended + ".", null, null, provider, model, writeValue(Map.of(
                "optimized", optimized,
                "supplemented", supplemented,
                "notRecommended", notRecommended,
                "unresolvedCoverageGaps", review == null || review.unresolvedCoverageGaps() == null ? List.of() : review.unresolvedCoverageGaps()
        )));
        appendEvent(taskId, "FINAL_CASES_READY", "DONE", "INFO", "Final usable case list contains " + finalCases.size() + " cases.", null, null, provider, model, null);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = blankToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String reviewStatusLabel(String status) {
        if ("APPROVED".equals(status)) {
            return "通过";
        }
        if ("REJECTED".equals(status)) {
            return "需重生成";
        }
        return "建议优化";
    }

    private String buildGeneratedCaseEventMessage(Integer itemIndex, GeneratedAiCaseItem item) {
        StringBuilder message = new StringBuilder("第 ")
                .append((itemIndex == null ? 0 : itemIndex) + 1)
                .append(" 条用例已加入列表");
        String angle = blankToNull(item.testAngle());
        String reason = blankToNull(item.generationReason());
        String evidence = blankToNull(item.requirementEvidence());
        if (angle != null) {
            message.append("｜角度：").append(angle);
        }
        if (evidence != null) {
            message.append("｜依据：").append(evidence);
        }
        if (reason != null) {
            message.append("｜原因：").append(reason);
        } else if (blankToNull(item.riskNotes()) != null) {
            message.append("｜关注：").append(item.riskNotes());
        }
        return message.toString();
    }

    private String buildReviewedCaseEventMessage(Integer itemIndex, String status, String summary, String coverageComment, String evidenceComment) {
        StringBuilder message = new StringBuilder("第 ")
                .append((itemIndex == null ? 0 : itemIndex) + 1)
                .append(" 条用例评审")
                .append(reviewStatusLabel(status));
        String normalizedSummary = blankToNull(summary);
        if (normalizedSummary != null) {
            message.append("｜理由：").append(normalizedSummary);
        }
        String normalizedCoverage = blankToNull(coverageComment);
        if (normalizedCoverage != null && !normalizedCoverage.equals(normalizedSummary)) {
            message.append("｜覆盖：").append(normalizedCoverage);
        }
        String normalizedEvidence = blankToNull(evidenceComment);
        if (normalizedEvidence != null) {
            message.append("｜依据评价：").append(normalizedEvidence);
        }
        return message.toString();
    }

    private void markFailed(String taskId, Exception exception) {
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
        appendEvent(taskId, "TASK_FAILED", latest.getCurrentStep() != null && latest.getCurrentStep() >= 3 ? "REVIEWING" : "GENERATING", "ERROR", exception.getMessage(), null, null, latest.getProvider(), latest.getModel(), null);
    }

    private AiGenerationTaskEventResponse appendEvent(
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
        return eventService.append(
                taskId,
                eventType,
                phase,
                level,
                message == null || message.isBlank() ? "-" : message,
                itemIndex,
                itemTitle,
                provider,
                model,
                payloadJson
        );
    }

    private String limitRawOutput(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() <= RAW_OUTPUT_LIMIT) {
            return value;
        }
        return value.substring(value.length() - RAW_OUTPUT_LIMIT);
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

    private AiGenerationTaskResponse toResponse(AiGenerationTaskEntity entity, WorkspaceEntity workspace, Map<Long, UserEntity> userMap) {
        return toResponse(entity, workspace, userMap, true);
    }

    private AiGenerationTaskResponse toResponse(AiGenerationTaskEntity entity, WorkspaceEntity workspace, Map<Long, UserEntity> userMap, boolean includeEvents) {
        UserEntity creator = entity.getCreatedBy() == null ? null : userMap.get(entity.getCreatedBy());
        UserEntity updater = entity.getUpdatedBy() == null ? null : userMap.get(entity.getUpdatedBy());
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
                creator == null ? null : creator.getDisplayName(),
                updater == null ? null : updater.getDisplayName(),
                entity.getProvider(),
                entity.getModel(),
                entity.getGeneratedCount() == null ? 0 : entity.getGeneratedCount(),
                entity.getSavedCaseCount() == null ? 0 : entity.getSavedCaseCount(),
                readValue(entity.getWarningsJson(), new TypeReference<List<String>>() {}, List.of()),
                readValue(entity.getInvalidCasesJson(), new TypeReference<List<AiInvalidCaseItem>>() {}, List.of()),
                readValue(entity.getGeneratedCasesJson(), new TypeReference<List<GeneratedAiCaseItem>>() {}, List.of()),
                readReviewResult(entity.getReviewResultJson()),
                entity.getGenerationRawOutput(),
                entity.getReviewRawOutput(),
                includeEvents ? eventService.list(entity.getTaskId()) : List.of(),
                readValue(entity.getAdoptedCaseIndexesJson(), new TypeReference<List<Integer>>() {}, List.of()),
                readValue(entity.getDeletedCaseIndexesJson(), new TypeReference<List<Integer>>() {}, List.of()),
                entity.getCancelRequested() != null && entity.getCancelRequested() == 1,
                entity.getSourceTaskId(),
                entity.getCreatedAt() == null ? null : entity.getCreatedAt().toString(),
                entity.getUpdatedAt() == null ? null : entity.getUpdatedAt().toString(),
                entity.getFinishedAt() == null ? null : entity.getFinishedAt().toString()
        );
    }

    private Map<Long, UserEntity> collectUserMap(List<AiGenerationTaskEntity> entities) {
        return entities.stream()
                .flatMap(item -> Stream.of(item.getCreatedBy(), item.getUpdatedBy()))
                .filter(id -> id != null && id > 0)
                .distinct()
                .map(userService::requireUser)
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
    }

    private static class TaskCanceledException extends RuntimeException {
        private TaskCanceledException(String message) {
            super(message);
        }
    }
}
