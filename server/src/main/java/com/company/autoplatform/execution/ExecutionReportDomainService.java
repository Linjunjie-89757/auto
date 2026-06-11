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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

@Service
public class ExecutionReportDomainService {

    private static final Set<String> REPORT_RESULTS = Set.of("SUCCESS", "FAILED");
    private static final Set<String> REPORT_LOG_SOURCES = Set.of("MANUAL", "API", "WEB", "APP", "SYSTEM");
    private static final String DEFAULT_LOG_SOURCE = "MANUAL";

    private final ExecutionTaskDomainService taskDomainService;
    private final ReportMapper reportMapper;
    private final ReportAttachmentMapper reportAttachmentMapper;
    private final WorkspaceService workspaceService;
    private final ReportAttachmentStorageService reportAttachmentStorageService;

    public ExecutionReportDomainService(
            ExecutionTaskDomainService taskDomainService,
            ReportMapper reportMapper,
            ReportAttachmentMapper reportAttachmentMapper,
            WorkspaceService workspaceService,
            ReportAttachmentStorageService reportAttachmentStorageService
    ) {
        this.taskDomainService = taskDomainService;
        this.reportMapper = reportMapper;
        this.reportAttachmentMapper = reportAttachmentMapper;
        this.workspaceService = workspaceService;
        this.reportAttachmentStorageService = reportAttachmentStorageService;
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
        validateReadableReportWorkspace(entity, workspaceCode);
        return toReportDetail(entity);
    }

    public ReportSummaryResponse createReport(String headerWorkspaceCode, CreateReportRequest request) {
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(
                workspaceService.resolveTargetWorkspace(headerWorkspaceCode, request.workspaceCode()));
        TaskEntity task = taskDomainService.requireTask(request.taskId());
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
        validateReadableReportWorkspace(entity, headerWorkspaceCode);
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(
                workspaceService.resolveTargetWorkspace(headerWorkspaceCode, request.workspaceCode()));
        if (!entity.getWorkspaceId().equals(workspace.getId())) {
            throw new BadRequestException("不允许修改报告归属空间");
        }
        TaskEntity task = taskDomainService.requireTask(request.taskId());
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
        validateReadableReportWorkspace(entity, workspaceCode);
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode());
        deleteAttachmentEntities(entity.getId());
        reportMapper.deleteById(id);
    }

    public ReportDetailResponse updateReportContent(Long id, String workspaceCode, UpdateReportContentRequest request) {
        ReportEntity entity = requireReport(id);
        validateReadableReportWorkspace(entity, workspaceCode);
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode());
        entity.setFailureSummary(blankToNull(request.failureSummary()));
        entity.setLogText(blankToNull(request.logText()));
        entity.setLogSource(normalizeLogSource(request.logSource()));
        entity.setUpdatedAt(LocalDateTime.now());
        reportMapper.updateById(entity);
        return toReportDetail(entity);
    }

    ReportEntity requireReport(Long reportId) {
        ReportEntity report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new NotFoundException("报告不存在");
        }
        return report;
    }

    void validateReadableReportWorkspace(ReportEntity report, String workspaceCode) {
        validateReadableWorkspace(report.getWorkspaceId(), workspaceCode, "该报告");
    }

    ReportAttachmentResponse toAttachmentResponse(ReportEntity report, ReportAttachmentEntity attachment) {
        return new ReportAttachmentResponse(
                attachment.getId(),
                attachment.getFileName(),
                attachment.getContentType(),
                attachment.getFileSize(),
                "/api/reports/" + report.getId() + "/attachments/" + attachment.getId() + "/download",
                attachment.getCreatedAt()
        );
    }

    private ReportSummaryResponse getReportSummary(Long id) {
        return toReportSummary(requireReport(id));
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
        TaskEntity task = taskDomainService.requireTask(item.getTaskId());
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

    private void deleteAttachmentEntities(Long reportId) {
        List<ReportAttachmentEntity> attachments = reportAttachmentMapper.selectList(new LambdaQueryWrapper<ReportAttachmentEntity>()
                .eq(ReportAttachmentEntity::getReportId, reportId));
        for (ReportAttachmentEntity attachment : attachments) {
            reportAttachmentStorageService.delete(attachment.getStoredPath());
        }
        reportAttachmentMapper.delete(new LambdaQueryWrapper<ReportAttachmentEntity>()
                .eq(ReportAttachmentEntity::getReportId, reportId));
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
        if ("INLINE".equals(normalized)) {
            normalized = DEFAULT_LOG_SOURCE;
        }
        if (!REPORT_LOG_SOURCES.contains(normalized)) {
            throw new BadRequestException("无效的日志来源: " + logSource);
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
