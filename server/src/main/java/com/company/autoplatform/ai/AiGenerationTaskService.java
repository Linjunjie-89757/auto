package com.company.autoplatform.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.autoplatform.common.BadRequestException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AiGenerationTaskService {

    private final AiGenerationTaskMapper aiGenerationTaskMapper;
    private final AiGenerationTaskDomainService taskDomainService;
    private final AiCaseService aiCaseService;
    private final AiGenerationTaskEventService eventService;
    private final AiGenerationTaskResponseSupport responseSupport;
    private final AiGenerationTaskResultMergeSupport resultMergeSupport;
    private final AiGenerationTaskEventMessageSupport eventMessageSupport;
    private final AiGenerationTaskSseSupport sseSupport;
    private final AiGenerationTaskExecutionStateSupport stateSupport;

    public AiGenerationTaskService(
            AiGenerationTaskMapper aiGenerationTaskMapper,
            AiGenerationTaskDomainService taskDomainService,
            AiCaseService aiCaseService,
            AiGenerationTaskEventService eventService,
            AiGenerationTaskResponseSupport responseSupport,
            AiGenerationTaskResultMergeSupport resultMergeSupport,
            AiGenerationTaskEventMessageSupport eventMessageSupport,
            AiGenerationTaskSseSupport sseSupport,
            AiGenerationTaskExecutionStateSupport stateSupport
    ) {
        this.aiGenerationTaskMapper = aiGenerationTaskMapper;
        this.taskDomainService = taskDomainService;
        this.aiCaseService = aiCaseService;
        this.eventService = eventService;
        this.responseSupport = responseSupport;
        this.resultMergeSupport = resultMergeSupport;
        this.eventMessageSupport = eventMessageSupport;
        this.sseSupport = sseSupport;
        this.stateSupport = stateSupport;
    }

    public AiGenerationTaskResponse createTask(String headerWorkspaceCode, CreateAiGenerationTaskRequest request) {
        return taskDomainService.createTask(headerWorkspaceCode, request);
    }

    public List<AiGenerationTaskResponse> listTasks(String workspaceCode) {
        return taskDomainService.listTasks(workspaceCode);
    }

    public AiGenerationTaskResponse getTask(String taskId, String workspaceCode) {
        return taskDomainService.getTask(taskId, workspaceCode);
    }

    public AiGenerationTaskResponse cancelTask(String taskId, String workspaceCode) {
        return taskDomainService.cancelTask(taskId, workspaceCode);
    }

    public AiGenerationTaskResponse retryTask(String taskId, String workspaceCode) {
        return taskDomainService.retryTask(taskId, workspaceCode);
    }

    public AiGenerationTaskResponse updateTask(String taskId, String workspaceCode, UpdateAiGenerationTaskRequest request) {
        return taskDomainService.updateTask(taskId, workspaceCode, request);
    }

    public void deleteTask(String taskId, String workspaceCode) {
        taskDomainService.deleteTask(taskId, workspaceCode);
    }

    public void executeTask(String taskId, String workspaceCode) {
        AiGenerationTaskEntity entity = requireTask(taskId);
        if (stateSupport.isCanceled(entity)) {
            stateSupport.markCanceled(entity, "任务已取消，未进入执行阶段。");
            return;
        }

        try {
            if ("COMPLETE".equals(normalizeOutputMode(entity.getOutputMode()))) {
                executeCompleteTask(entity, workspaceCode);
            } else {
                executeStreamTask(entity, workspaceCode);
            }
        } catch (TaskCanceledException exception) {
            stateSupport.markCanceled(requireTask(taskId), exception.getMessage());
        } catch (Exception exception) {
            stateSupport.markFailed(taskId, exception);
        }
    }

    private void executeCompleteTask(AiGenerationTaskEntity entity, String workspaceCode) {
        appendEvent(entity.getTaskId(), "TASK_STARTED", "SETUP", "INFO", "任务开始执行完整输出链路", null, null, null, null, null);
        stateSupport.transitionToGenerating(entity);
        GenerateAiCasesResponse generation = aiCaseService.generateCases(workspaceCode, new GenerateAiCasesRequest(
                workspaceCode,
                entity.getRequirementTitle(),
                entity.getRequirementContent(),
                null,
                null,
                responseSupport.readValue(entity.getAssetIdsJson(), new TypeReference<List<Long>>() {}, List.of()),
                List.of(),
                null,
                null
        ));

        entity = requireTask(entity.getTaskId());
        if (stateSupport.isCanceled(entity)) {
            throw new TaskCanceledException("任务已取消，生成结果未继续写入。");
        }

        entity.setProvider(generation.provider());
        entity.setModel(generation.model());
        entity.setGeneratedCount(generation.actualGeneratedCount() == null ? 0 : generation.actualGeneratedCount());
        entity.setWarningsJson(responseSupport.writeValue(generation.warnings()));
        entity.setInvalidCasesJson(responseSupport.writeValue(generation.invalidCases()));
        entity.setGeneratedCasesJson(responseSupport.writeValue(generation.generatedCases()));
        entity.setGenerationRawOutput(stateSupport.limitRawOutput(generation.rawContent()));
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
                        .map(resultMergeSupport::toExistingCaseItem)
                        .toList()
        ));

        entity = requireTask(entity.getTaskId());
        if (stateSupport.isCanceled(entity)) {
            throw new TaskCanceledException("任务已取消，评审结果未继续写入。");
        }

        entity.setStatus("COMPLETED");
        entity.setCurrentStep(4);
        entity.setStepMessage("任务已完成，可在记录详情中查看生成结果并继续处理。");
        List<GeneratedAiCaseItem> finalCases = resultMergeSupport.mergeCompleteReviewResult(generation.generatedCases(), review);
        entity.setGeneratedCasesJson(responseSupport.writeValue(finalCases));
        entity.setGeneratedCount(finalCases.size());
        entity.setReviewResultJson(responseSupport.writeValue(review));
        entity.setReviewRawOutput(stateSupport.limitRawOutput(review.rawContent()));
        entity.setFinishedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        aiGenerationTaskMapper.updateById(entity);
        appendCompleteReviewEvents(entity.getTaskId(), finalCases, review, generation.provider(), generation.model());
        appendEvent(entity.getTaskId(), "TASK_COMPLETED", "DONE", "INFO", "完整输出任务已完成", null, null, generation.provider(), generation.model(), null);
    }

    private void executeStreamTask(AiGenerationTaskEntity entity, String workspaceCode) {
        String taskId = entity.getTaskId();
        appendEvent(taskId, "TASK_STARTED", "SETUP", "INFO", "任务开始执行实时流式输出链路", null, null, null, null, null);
        stateSupport.transitionToGenerating(entity);
        List<GeneratedAiCaseItem> generatedCases = new ArrayList<>();
        AiCaseService.StreamedGenerateCasesResult generation = aiCaseService.streamGenerateCases(
                workspaceCode,
                new GenerateAiCasesRequest(
                        workspaceCode,
                        entity.getRequirementTitle(),
                        entity.getRequirementContent(),
                        null,
                        null,
                        responseSupport.readValue(entity.getAssetIdsJson(), new TypeReference<List<Long>>() {}, List.of()),
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
                    if (stateSupport.isCanceled(latest)) {
                        throw new TaskCanceledException("任务已取消，停止接收生成流。");
                    }
                    generatedCases.add(update.item());
                    stateSupport.persistGeneratedCasesSnapshot(latest, generatedCases, update.rawOutput());
                    appendEvent(taskId, "CASE_GENERATED", "GENERATING", "INFO", eventMessageSupport.buildGeneratedCaseEventMessage(update.itemIndex(), update.item()), update.itemIndex(), update.item().title(), latest.getProvider(), latest.getModel(), responseSupport.writeValue(update.item()));
                }
        );

        entity = requireTask(taskId);
        if (stateSupport.isCanceled(entity)) {
            throw new TaskCanceledException("任务已取消，生成结果未继续写入。");
        }
        generatedCases.clear();
        generatedCases.addAll(generation.generatedCases());
        entity.setProvider(generation.provider());
        entity.setModel(generation.model());
        entity.setGeneratedCount(generation.actualGeneratedCount() == null ? generatedCases.size() : generation.actualGeneratedCount());
        entity.setWarningsJson(responseSupport.writeValue(generation.warnings()));
        entity.setInvalidCasesJson(responseSupport.writeValue(generation.invalidCases()));
        entity.setGeneratedCasesJson(responseSupport.writeValue(generatedCases));
        entity.setGenerationRawOutput(stateSupport.limitRawOutput(generation.rawContent()));
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
                    responseSupport.writeValue(Map.of("reason", blankToNull(generation.fallbackReason()) == null ? "" : generation.fallbackReason()))
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
                        generatedCases.stream().map(resultMergeSupport::toExistingCaseItem).toList()
                ),
                modelInfo -> {
                    reviewProvider[0] = modelInfo.provider();
                    reviewModel[0] = modelInfo.model();
                    appendEvent(taskId, "REVIEW_STARTED", "REVIEWING", "INFO", "评审模型已就绪：" + modelInfo.model(), null, null, modelInfo.provider(), modelInfo.model(), null);
                },
                update -> {
                    AiGenerationTaskEntity latest = requireTask(taskId);
                    if (stateSupport.isCanceled(latest)) {
                        throw new TaskCanceledException("任务已取消，停止接收评审流。");
                    }
                    if ("SUPPLEMENTED".equals(update.status()) && update.supplementCase() != null) {
                        if (generatedCases.size() >= AiCaseService.FINAL_MAX_CASES) {
                            return;
                        }
                        GeneratedAiCaseItem supplemented = resultMergeSupport.withStreamSupplementMetadata(update);
                        generatedCases.add(supplemented);
                        latest.setGeneratedCasesJson(responseSupport.writeValue(generatedCases));
                        latest.setGeneratedCount(generatedCases.size());
                        latest.setReviewRawOutput(stateSupport.limitRawOutput(update.rawOutput()));
                        latest.setUpdatedAt(LocalDateTime.now());
                        aiGenerationTaskMapper.updateById(latest);
                        int itemIndex = generatedCases.size() - 1;
                        appendEvent(taskId, "CASE_SUPPLEMENTED", "REVIEWING", "INFO", eventMessageSupport.buildSupplementedCaseEventMessage(itemIndex, supplemented), itemIndex, supplemented.title(), reviewProvider[0], reviewModel[0], responseSupport.writeValue(Map.of(
                                "status", update.status(),
                                "summary", update.summary() == null ? "" : update.summary(),
                                "supplementReason", update.supplementReason() == null ? "" : update.supplementReason(),
                                "coverageGap", update.coverageGap() == null ? "" : update.coverageGap()
                        )));
                        return;
                    }
                    if (update.itemIndex() == null || update.itemIndex() < 0 || update.itemIndex() >= generatedCases.size()) {
                        return;
                    }
                    GeneratedAiCaseItem reviewed = resultMergeSupport.applyReviewUpdate(generatedCases.get(update.itemIndex()), update);
                    generatedCases.set(update.itemIndex(), reviewed);
                    latest.setGeneratedCasesJson(responseSupport.writeValue(generatedCases));
                    latest.setReviewRawOutput(stateSupport.limitRawOutput(update.rawOutput()));
                    latest.setUpdatedAt(LocalDateTime.now());
                    aiGenerationTaskMapper.updateById(latest);
                    appendEvent(taskId, "CASE_REVIEWED", "REVIEWING", "INFO", eventMessageSupport.buildReviewedCaseEventMessage(update.itemIndex(), reviewed.title(), update.status(), update.summary(), update.coverageComment(), update.evidenceComment()), update.itemIndex(), reviewed.title(), reviewProvider[0], reviewModel[0], responseSupport.writeValue(Map.of(
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
        if (stateSupport.isCanceled(entity)) {
            throw new TaskCanceledException("任务已取消，评审结果未继续写入。");
        }
        entity.setStatus("COMPLETED");
        entity.setCurrentStep(4);
        entity.setStepMessage("任务已完成，可在记录详情中查看生成结果并继续处理。");
        if (review.fallbackToComplete()) {
            generatedCases.clear();
            generatedCases.addAll(resultMergeSupport.mergeCompleteReviewResult(generation.generatedCases(), review.reviewResult()));
        }
        entity.setGeneratedCasesJson(responseSupport.writeValue(generatedCases));
        entity.setGeneratedCount(generatedCases.size());
        entity.setReviewResultJson(responseSupport.writeValue(review.reviewResult()));
        entity.setReviewRawOutput(stateSupport.limitRawOutput(review.rawContent()));
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
                    responseSupport.writeValue(Map.of("reason", blankToNull(review.fallbackReason()) == null ? "" : review.fallbackReason()))
            );
        }
        appendEvent(taskId, "REVIEW_COMPLETED", "REVIEWING", "INFO", "AI stream review completed", null, null, review.provider(), review.model(), null);
        appendEvent(taskId, "TASK_COMPLETED", "DONE", "INFO", "实时流式任务已完成", null, null, review.provider(), review.model(), null);
    }

    public StreamingResponseBody streamTaskEvents(String taskId, String workspaceCode) {
        return sseSupport.streamTaskEvents(taskId, workspaceCode);
    }

    private void appendCompleteReviewEvents(String taskId, List<GeneratedAiCaseItem> finalCases, AiReviewResult review, String provider, String model) {
        long optimized = finalCases.stream().filter(item -> "OPTIMIZED".equals(item.aiReviewStatus())).count();
        long supplemented = finalCases.stream().filter(item -> "SUPPLEMENTED".equals(item.aiReviewStatus())).count();
        long notRecommended = finalCases.stream().filter(item -> "NOT_RECOMMENDED".equals(item.aiReviewStatus())).count();
        appendEvent(taskId, "REVIEW_COMPLETED", "REVIEWING", "INFO", "AI review completed: optimized " + optimized + ", supplemented " + supplemented + ", not recommended " + notRecommended + ".", null, null, provider, model, responseSupport.writeValue(Map.of(
                "optimized", optimized,
                "supplemented", supplemented,
                "notRecommended", notRecommended,
                "unresolvedCoverageGaps", review == null || review.unresolvedCoverageGaps() == null ? List.of() : review.unresolvedCoverageGaps()
        )));
        appendEvent(taskId, "FINAL_CASES_READY", "DONE", "INFO", "Final usable case list contains " + finalCases.size() + " cases.", null, null, provider, model, null);
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

    private AiGenerationTaskEntity requireTask(String taskId) {
        AiGenerationTaskEntity entity = aiGenerationTaskMapper.selectOne(new LambdaQueryWrapper<AiGenerationTaskEntity>()
                .eq(AiGenerationTaskEntity::getTaskId, taskId)
                .last("limit 1"));
        if (entity == null) {
            throw new BadRequestException("AI generation task does not exist");
        }
        return entity;
    }

    private String normalizeOutputMode(String outputMode) {
        return outputMode == null ? "STREAM" : outputMode.trim().toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private static class TaskCanceledException extends RuntimeException {
        private TaskCanceledException(String message) {
            super(message);
        }
    }
}
