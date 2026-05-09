package com.company.autoplatform.execution;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.autoplatform.common.BadRequestException;
import com.company.autoplatform.common.JsonUtils;
import com.company.autoplatform.common.NotFoundException;
import com.company.autoplatform.common.PageResponse;
import com.company.autoplatform.workspace.WorkspaceEntity;
import com.company.autoplatform.workspace.WorkspaceScope;
import com.company.autoplatform.workspace.WorkspaceService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

@Service
public class ExecutionService {

    private static final Set<String> TASK_STATUSES = Set.of("READY", "RUNNING", "SUCCESS", "FAILED", "CANCELED");
    private static final Set<String> REPORT_RESULTS = Set.of("SUCCESS", "FAILED");
    private static final Set<String> REPORT_LOG_SOURCES = Set.of("MANUAL", "API", "WEB", "APP", "SYSTEM");
    private static final String DEFAULT_LOG_SOURCE = "MANUAL";

    private final TaskMapper taskMapper;
    private final ReportMapper reportMapper;
    private final ReportAttachmentMapper reportAttachmentMapper;
    private final WorkspaceService workspaceService;
    private final ReportAttachmentStorageService reportAttachmentStorageService;

    public ExecutionService(
            TaskMapper taskMapper,
            ReportMapper reportMapper,
            ReportAttachmentMapper reportAttachmentMapper,
            WorkspaceService workspaceService,
            ReportAttachmentStorageService reportAttachmentStorageService
    ) {
        this.taskMapper = taskMapper;
        this.reportMapper = reportMapper;
        this.reportAttachmentMapper = reportAttachmentMapper;
        this.workspaceService = workspaceService;
        this.reportAttachmentStorageService = reportAttachmentStorageService;
    }

    public PageResponse<TaskSummaryResponse> listTasks(String workspaceCode) {
        String normalized = WorkspaceScope.normalize(workspaceCode);
        LambdaQueryWrapper<TaskEntity> query = new LambdaQueryWrapper<>();
        if (!WorkspaceScope.isAll(normalized)) {
            WorkspaceEntity workspace = workspaceService.requireReadableWorkspace(normalized);
            query.eq(TaskEntity::getWorkspaceId, workspace.getId());
        } else if (!workspaceService.isPlatformAdmin()) {
            List<Long> workspaceIds = workspaceService.listReadableWorkspaceIds();
            if (workspaceIds.isEmpty()) {
                return new PageResponse<>(List.of(), 0);
            }
            query.in(TaskEntity::getWorkspaceId, workspaceIds);
        }
        var items = taskMapper.selectList(query.orderByAsc(TaskEntity::getId)).stream()
                .map(this::toTaskSummary)
                .toList();
        return new PageResponse<>(items, items.size());
    }

    public TaskDetailResponse getTask(Long id, String workspaceCode) {
        TaskEntity entity = requireTask(id);
        validateReadableWorkspace(entity.getWorkspaceId(), workspaceCode, "该任务");
        return toTaskDetail(entity);
    }

    public TaskSummaryResponse createTask(String headerWorkspaceCode, CreateTaskRequest request) {
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(
                workspaceService.resolveTargetWorkspace(headerWorkspaceCode, request.workspaceCode()));
        TaskEntity entity = new TaskEntity();
        entity.setWorkspaceId(workspace.getId());
        entity.setTaskName(request.taskName().trim());
        entity.setEngineType(normalizeEngineType(request.engineType()));
        entity.setTaskStatus(normalizeTaskStatus(request.status()));
        entity.setSummary(blankToNull(request.summary()));
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        taskMapper.insert(entity);
        return getTaskSummary(entity.getId());
    }

    public TaskSummaryResponse updateTask(Long id, String headerWorkspaceCode, CreateTaskRequest request) {
        TaskEntity entity = requireTask(id);
        validateReadableWorkspace(entity.getWorkspaceId(), headerWorkspaceCode, "该任务");
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(
                workspaceService.resolveTargetWorkspace(headerWorkspaceCode, request.workspaceCode()));
        if (!entity.getWorkspaceId().equals(workspace.getId())) {
            throw new BadRequestException("不允许修改任务归属空间");
        }
        entity.setTaskName(request.taskName().trim());
        entity.setEngineType(normalizeEngineType(request.engineType()));
        entity.setTaskStatus(normalizeTaskStatus(request.status()));
        entity.setSummary(blankToNull(request.summary()));
        entity.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(entity);
        return getTaskSummary(id);
    }

    public void deleteTask(Long id, String workspaceCode) {
        TaskEntity entity = requireTask(id);
        validateReadableWorkspace(entity.getWorkspaceId(), workspaceCode, "该任务");
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode());
        long reportCount = reportMapper.selectCount(new LambdaQueryWrapper<ReportEntity>()
                .eq(ReportEntity::getTaskId, id));
        if (reportCount > 0) {
            throw new BadRequestException("任务下已有关联报告，不能直接删除");
        }
        taskMapper.deleteById(id);
    }

    public TaskDetailResponse transitionTask(Long id, String workspaceCode, TaskTransitionRequest request) {
        TaskEntity entity = requireTask(id);
        validateReadableWorkspace(entity.getWorkspaceId(), workspaceCode, "该任务");
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode());
        String targetStatus = normalizeTaskStatus(request.toStatus());
        validateTaskTransition(entity.getTaskStatus(), targetStatus);
        entity.setTaskStatus(targetStatus);
        entity.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(entity);
        return toTaskDetail(entity);
    }

    public PageResponse<ReportSummaryResponse> listReports(String workspaceCode) {
        String normalized = WorkspaceScope.normalize(workspaceCode);
        LambdaQueryWrapper<ReportEntity> query = new LambdaQueryWrapper<>();
        if (!WorkspaceScope.isAll(normalized)) {
            WorkspaceEntity workspace = workspaceService.requireReadableWorkspace(normalized);
            query.eq(ReportEntity::getWorkspaceId, workspace.getId());
        } else if (!workspaceService.isPlatformAdmin()) {
            List<Long> workspaceIds = workspaceService.listReadableWorkspaceIds();
            if (workspaceIds.isEmpty()) {
                return new PageResponse<>(List.of(), 0);
            }
            query.in(ReportEntity::getWorkspaceId, workspaceIds);
        }
        var items = reportMapper.selectList(query.orderByAsc(ReportEntity::getId)).stream()
                .map(this::toReportSummary)
                .toList();
        return new PageResponse<>(items, items.size());
    }

    public ReportDetailResponse getReport(Long id, String workspaceCode) {
        ReportEntity entity = requireReport(id);
        validateReadableWorkspace(entity.getWorkspaceId(), workspaceCode, "该报告");
        return toReportDetail(entity);
    }

    public ReportSummaryResponse createReport(String headerWorkspaceCode, CreateReportRequest request) {
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(
                workspaceService.resolveTargetWorkspace(headerWorkspaceCode, request.workspaceCode()));
        TaskEntity task = requireTask(request.taskId());
        if (!task.getWorkspaceId().equals(workspace.getId())) {
            throw new BadRequestException("报告归属空间必须与关联任务一致");
        }
        ReportEntity entity = new ReportEntity();
        entity.setWorkspaceId(workspace.getId());
        entity.setTaskId(request.taskId());
        entity.setReportName(request.reportName().trim());
        entity.setResult(normalizeReportResult(request.result()));
        entity.setLogSource(normalizeLogSource(request.logSource()));
        entity.setFailureSummary(blankToNull(request.failureSummary()));
        entity.setLogText(null);
        entity.setAttachmentsJson(JsonUtils.toJson(List.of()));
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        reportMapper.insert(entity);
        return getReportSummary(entity.getId());
    }

    public ReportSummaryResponse updateReport(Long id, String headerWorkspaceCode, CreateReportRequest request) {
        ReportEntity entity = requireReport(id);
        validateReadableWorkspace(entity.getWorkspaceId(), headerWorkspaceCode, "该报告");
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(
                workspaceService.resolveTargetWorkspace(headerWorkspaceCode, request.workspaceCode()));
        if (!entity.getWorkspaceId().equals(workspace.getId())) {
            throw new BadRequestException("不允许修改报告归属空间");
        }
        TaskEntity task = requireTask(request.taskId());
        if (!task.getWorkspaceId().equals(workspace.getId())) {
            throw new BadRequestException("报告归属空间必须与关联任务一致");
        }
        entity.setTaskId(request.taskId());
        entity.setReportName(request.reportName().trim());
        entity.setResult(normalizeReportResult(request.result()));
        entity.setLogSource(normalizeLogSource(request.logSource()));
        entity.setFailureSummary(blankToNull(request.failureSummary()));
        if (entity.getAttachmentsJson() == null) {
            entity.setAttachmentsJson(JsonUtils.toJson(List.of()));
        }
        entity.setUpdatedAt(LocalDateTime.now());
        reportMapper.updateById(entity);
        return getReportSummary(id);
    }

    public void deleteReport(Long id, String workspaceCode) {
        ReportEntity entity = requireReport(id);
        validateReadableWorkspace(entity.getWorkspaceId(), workspaceCode, "该报告");
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode());
        deleteAttachmentEntities(entity.getId());
        reportMapper.deleteById(id);
    }

    public ReportDetailResponse updateReportContent(Long id, String workspaceCode, UpdateReportContentRequest request) {
        ReportEntity entity = requireReport(id);
        validateReadableWorkspace(entity.getWorkspaceId(), workspaceCode, "该报告");
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode());
        entity.setFailureSummary(blankToNull(request.failureSummary()));
        entity.setLogText(blankToNull(request.logText()));
        entity.setLogSource(normalizeLogSource(request.logSource()));
        entity.setUpdatedAt(LocalDateTime.now());
        reportMapper.updateById(entity);
        return toReportDetail(entity);
    }

    public List<ReportAttachmentResponse> uploadReportAttachments(Long reportId, String workspaceCode, List<MultipartFile> files) {
        ReportEntity report = requireReport(reportId);
        validateReadableWorkspace(report.getWorkspaceId(), workspaceCode, "该报告");
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(report.getWorkspaceId()).getWorkspaceCode());

        List<StoredReportFile> storedFiles = reportAttachmentStorageService.storeAll(report.getWorkspaceId(), reportId, files);
        List<ReportAttachmentEntity> createdAttachments = new ArrayList<>();
        try {
            for (int i = 0; i < storedFiles.size(); i++) {
                MultipartFile file = files.get(i);
                StoredReportFile storedFile = storedFiles.get(i);
                ReportAttachmentEntity attachment = new ReportAttachmentEntity();
                attachment.setReportId(reportId);
                attachment.setWorkspaceId(report.getWorkspaceId());
                attachment.setFileName(file.getOriginalFilename());
                attachment.setStoredPath(storedFile.storedPath());
                attachment.setContentType(storedFile.contentType());
                attachment.setFileSize(storedFile.fileSize());
                attachment.setCreatedAt(LocalDateTime.now());
                attachment.setUpdatedAt(LocalDateTime.now());
                reportAttachmentMapper.insert(attachment);
                createdAttachments.add(attachment);
            }
        } catch (RuntimeException exception) {
            for (ReportAttachmentEntity attachment : createdAttachments) {
                if (attachment.getId() != null) {
                    reportAttachmentMapper.deleteById(attachment.getId());
                }
                reportAttachmentStorageService.delete(attachment.getStoredPath());
            }
            for (StoredReportFile storedFile : storedFiles) {
                reportAttachmentStorageService.delete(storedFile.storedPath());
            }
            throw exception;
        }

        report.setUpdatedAt(LocalDateTime.now());
        reportMapper.updateById(report);
        return createdAttachments.stream()
                .map(attachment -> toAttachmentResponse(report, attachment))
                .toList();
    }

    public void deleteReportAttachment(Long reportId, Long attachmentId, String workspaceCode) {
        ReportEntity report = requireReport(reportId);
        validateReadableWorkspace(report.getWorkspaceId(), workspaceCode, "该报告");
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(report.getWorkspaceId()).getWorkspaceCode());
        ReportAttachmentEntity attachment = requireAttachment(attachmentId);
        if (!attachment.getReportId().equals(reportId)) {
            throw new BadRequestException("附件不属于当前报告");
        }
        reportAttachmentMapper.deleteById(attachmentId);
        reportAttachmentStorageService.delete(attachment.getStoredPath());
        report.setUpdatedAt(LocalDateTime.now());
        reportMapper.updateById(report);
    }

    public ReportFileDownload downloadReportAttachment(Long reportId, Long attachmentId, String workspaceCode) {
        ReportEntity report = requireReport(reportId);
        validateReadableWorkspace(report.getWorkspaceId(), workspaceCode, "该报告");
        ReportAttachmentEntity attachment = requireAttachment(attachmentId);
        if (!attachment.getReportId().equals(reportId)) {
            throw new BadRequestException("附件不属于当前报告");
        }
        return reportAttachmentStorageService.load(attachment);
    }

    public TaskEntity requireTask(Long taskId) {
        TaskEntity task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new NotFoundException("任务不存在");
        }
        return task;
    }

    public ReportEntity requireReport(Long reportId) {
        ReportEntity report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new NotFoundException("报告不存在");
        }
        return report;
    }

    public ReportAttachmentEntity requireAttachment(Long attachmentId) {
        ReportAttachmentEntity attachment = reportAttachmentMapper.selectById(attachmentId);
        if (attachment == null) {
            throw new NotFoundException("附件不存在");
        }
        return attachment;
    }

    private void validateReadableWorkspace(Long workspaceId, String workspaceCode, String entityLabel) {
        String normalized = WorkspaceScope.normalize(workspaceCode);
        if (WorkspaceScope.isAll(normalized)) {
            if (!workspaceService.isPlatformAdmin() && !workspaceService.listReadableWorkspaceIds().contains(workspaceId)) {
                throw new BadRequestException("当前空间视角下不可访问" + entityLabel);
            }
            return;
        }
        WorkspaceEntity workspace = workspaceService.requireReadableWorkspace(normalized);
        if (!workspace.getId().equals(workspaceId)) {
            throw new BadRequestException("当前空间视角下不可访问" + entityLabel);
        }
    }

    private TaskSummaryResponse getTaskSummary(Long id) {
        return toTaskSummary(requireTask(id));
    }

    private ReportSummaryResponse getReportSummary(Long id) {
        return toReportSummary(requireReport(id));
    }

    private TaskSummaryResponse toTaskSummary(TaskEntity item) {
        WorkspaceEntity workspace = workspaceService.requireWorkspaceById(item.getWorkspaceId());
        return new TaskSummaryResponse(
                item.getId(),
                item.getTaskName(),
                item.getEngineType(),
                item.getTaskStatus(),
                item.getSummary(),
                workspace.getWorkspaceCode(),
                workspace.getWorkspaceName()
        );
    }

    private TaskDetailResponse toTaskDetail(TaskEntity item) {
        WorkspaceEntity workspace = workspaceService.requireWorkspaceById(item.getWorkspaceId());
        List<ReportSummaryResponse> reports = reportMapper.selectList(new LambdaQueryWrapper<ReportEntity>()
                        .eq(ReportEntity::getTaskId, item.getId())
                        .orderByAsc(ReportEntity::getId))
                .stream()
                .map(this::toReportSummary)
                .toList();
        return new TaskDetailResponse(
                item.getId(),
                item.getTaskName(),
                item.getEngineType(),
                item.getTaskStatus(),
                item.getSummary(),
                workspace.getWorkspaceCode(),
                workspace.getWorkspaceName(),
                item.getCreatedAt(),
                item.getUpdatedAt(),
                reports
        );
    }

    private ReportSummaryResponse toReportSummary(ReportEntity item) {
        WorkspaceEntity workspace = workspaceService.requireWorkspaceById(item.getWorkspaceId());
        return new ReportSummaryResponse(
                item.getId(),
                item.getTaskId(),
                item.getReportName(),
                item.getResult(),
                normalizeLogSource(item.getLogSource()),
                workspace.getWorkspaceCode(),
                workspace.getWorkspaceName(),
                item.getFailureSummary()
        );
    }

    private ReportDetailResponse toReportDetail(ReportEntity item) {
        WorkspaceEntity workspace = workspaceService.requireWorkspaceById(item.getWorkspaceId());
        TaskEntity task = requireTask(item.getTaskId());
        List<ReportAttachmentResponse> attachments = reportAttachmentMapper.selectList(new LambdaQueryWrapper<ReportAttachmentEntity>()
                        .eq(ReportAttachmentEntity::getReportId, item.getId())
                        .orderByAsc(ReportAttachmentEntity::getId))
                .stream()
                .map(attachment -> toAttachmentResponse(item, attachment))
                .toList();
        if (attachments.isEmpty()) {
            List<String> legacyAttachments = JsonUtils.toStringList(item.getAttachmentsJson());
            attachments = IntStream.range(0, legacyAttachments.size())
                    .mapToObj(index -> new ReportAttachmentResponse(
                            -1L * (index + 1),
                            legacyAttachments.get(index),
                            null,
                            null,
                            null,
                            item.getUpdatedAt()
                    ))
                    .toList();
        }
        return new ReportDetailResponse(
                item.getId(),
                item.getTaskId(),
                task.getTaskName(),
                item.getReportName(),
                item.getResult(),
                normalizeLogSource(item.getLogSource()),
                workspace.getWorkspaceCode(),
                workspace.getWorkspaceName(),
                item.getFailureSummary(),
                blankToNull(item.getLogText()),
                attachments,
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }

    private ReportAttachmentResponse toAttachmentResponse(ReportEntity report, ReportAttachmentEntity attachment) {
        return new ReportAttachmentResponse(
                attachment.getId(),
                attachment.getFileName(),
                attachment.getContentType(),
                attachment.getFileSize(),
                "/api/reports/" + report.getId() + "/attachments/" + attachment.getId() + "/download",
                attachment.getCreatedAt()
        );
    }

    private void deleteAttachmentEntities(Long reportId) {
        List<ReportAttachmentEntity> attachments = reportAttachmentMapper.selectList(new LambdaQueryWrapper<ReportAttachmentEntity>()
                .eq(ReportAttachmentEntity::getReportId, reportId));
        for (ReportAttachmentEntity attachment : attachments) {
            reportAttachmentStorageService.delete(attachment.getStoredPath());
        }
        reportAttachmentMapper.delete(new LambdaQueryWrapper<ReportAttachmentEntity>()
                .eq(ReportAttachmentEntity::getReportId, reportId));
    }

    private void validateTaskTransition(String currentStatus, String targetStatus) {
        if (currentStatus.equals(targetStatus)) {
            throw new BadRequestException("任务已经处于该状态");
        }
        boolean allowed = switch (currentStatus) {
            case "READY" -> "RUNNING".equals(targetStatus) || "CANCELED".equals(targetStatus);
            case "RUNNING" -> "SUCCESS".equals(targetStatus) || "FAILED".equals(targetStatus) || "CANCELED".equals(targetStatus);
            default -> false;
        };
        if (!allowed) {
            throw new BadRequestException("当前任务状态不允许这样流转");
        }
    }

    private String normalizeTaskStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (!TASK_STATUSES.contains(normalized)) {
            throw new BadRequestException("无效的任务状态: " + status);
        }
        return normalized;
    }

    private String normalizeReportResult(String result) {
        String normalized = result == null ? "" : result.trim().toUpperCase();
        if (!REPORT_RESULTS.contains(normalized)) {
            throw new BadRequestException("无效的报告结果: " + result);
        }
        return normalized;
    }

    private String normalizeLogSource(String logSource) {
        String normalized = logSource == null ? DEFAULT_LOG_SOURCE : logSource.trim().toUpperCase();
        if (normalized.isEmpty()) {
            normalized = DEFAULT_LOG_SOURCE;
        }
        if (!REPORT_LOG_SOURCES.contains(normalized)) {
            throw new BadRequestException("无效的日志来源: " + logSource);
        }
        return normalized;
    }

    private String normalizeEngineType(String engineType) {
        String normalized = engineType == null ? "" : engineType.trim().toUpperCase();
        if (normalized.isEmpty()) {
            throw new BadRequestException("执行引擎不能为空");
        }
        return normalized;
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
