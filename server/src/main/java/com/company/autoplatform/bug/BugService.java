package com.company.autoplatform.bug;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.autoplatform.casecenter.CaseDetailResponse;
import com.company.autoplatform.casecenter.CaseEntity;
import com.company.autoplatform.casecenter.CaseService;
import com.company.autoplatform.common.JsonUtils;
import com.company.autoplatform.common.NotFoundException;
import com.company.autoplatform.common.PageResponse;
import com.company.autoplatform.execution.ExecutionService;
import com.company.autoplatform.execution.ReportDetailResponse;
import com.company.autoplatform.execution.ReportEntity;
import com.company.autoplatform.execution.TaskDetailResponse;
import com.company.autoplatform.user.UserEntity;
import com.company.autoplatform.user.UserService;
import com.company.autoplatform.workspace.WorkspaceEntity;
import com.company.autoplatform.workspace.WorkspaceService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class BugService {
    private final BugDomainService bugDomainService;
    private final BugAttachmentSupport bugAttachmentSupport;
    private final BugWorkflowDomainService bugWorkflowDomainService;
    private final BugCommentDomainService bugCommentDomainService;
    private final BugFlowMapper bugFlowMapper;
    private final BugCommentMapper bugCommentMapper;
    private final BugAttachmentMapper bugAttachmentMapper;
    private final UserService userService;
    private final WorkspaceService workspaceService;
    private final CaseService caseService;
    private final ExecutionService executionService;

    public BugService(BugDomainService bugDomainService, BugAttachmentSupport bugAttachmentSupport,
                      BugWorkflowDomainService bugWorkflowDomainService,
                      BugCommentDomainService bugCommentDomainService,
                      BugFlowMapper bugFlowMapper, BugCommentMapper bugCommentMapper,
                      BugAttachmentMapper bugAttachmentMapper,
                      UserService userService, WorkspaceService workspaceService, CaseService caseService,
                      ExecutionService executionService) {
        this.bugDomainService = bugDomainService;
        this.bugAttachmentSupport = bugAttachmentSupport;
        this.bugWorkflowDomainService = bugWorkflowDomainService;
        this.bugCommentDomainService = bugCommentDomainService;
        this.bugFlowMapper = bugFlowMapper;
        this.bugCommentMapper = bugCommentMapper;
        this.bugAttachmentMapper = bugAttachmentMapper;
        this.userService = userService;
        this.workspaceService = workspaceService;
        this.caseService = caseService;
        this.executionService = executionService;
    }

    public PageResponse<BugSummaryResponse> listBugs(
            String workspaceCode,
            String keyword,
            String status,
            String severity,
            String priority,
            Integer pageNo,
            Integer pageSize
    ) {
        PageResponse<BugEntity> page = bugDomainService.listBugs(workspaceCode, keyword, status, severity, priority, pageNo, pageSize);
        return new PageResponse<>(
                page.items().stream().map(this::toSummary).toList(),
                page.total(),
                page.pageNo(),
                page.pageSize(),
                page.totalPages()
        );
    }

    public BugDetailResponse getBug(Long id, String workspaceCode) {
        return toDetail(bugDomainService.getBug(id, workspaceCode));
    }

    public BugDetailResponse createBug(String headerWorkspaceCode, CreateBugRequest request, BugSourceType sourceType) {
        return toDetail(bugDomainService.createBug(headerWorkspaceCode, request, sourceType));
    }

    public BugDetailResponse updateBug(Long id, String headerWorkspaceCode, UpdateBugRequest request) {
        return toDetail(bugDomainService.updateBug(id, headerWorkspaceCode, request));
    }

    public BugDetailResponse assignBug(Long id, String headerWorkspaceCode, AssignBugRequest request) {
        return toDetail(bugWorkflowDomainService.assignBug(id, headerWorkspaceCode, request));
    }

    public BugDetailResponse transitionBug(Long id, String headerWorkspaceCode, TransitionBugRequest request) {
        return toDetail(bugWorkflowDomainService.transitionBug(id, headerWorkspaceCode, request));
    }

    public List<BugCommentResponse> listComments(Long id, String workspaceCode) {
        return bugCommentDomainService.listComments(id, workspaceCode).stream().map(this::toComment).toList();
    }

    public BugCommentResponse addComment(Long id, String workspaceCode, CreateBugCommentRequest request) {
        return toComment(bugCommentDomainService.addComment(id, workspaceCode, request));
    }

    public List<BugAttachmentResponse> uploadBugAttachments(Long bugId, String workspaceCode, List<MultipartFile> files) {
        return bugAttachmentSupport.uploadBugAttachments(bugId, workspaceCode, files);
    }

    public void deleteBugAttachment(Long bugId, Long attachmentId, String workspaceCode) {
        bugAttachmentSupport.deleteBugAttachment(bugId, attachmentId, workspaceCode);
    }

    public BugFileDownload downloadBugAttachment(Long bugId, Long attachmentId, String workspaceCode) {
        return bugAttachmentSupport.downloadBugAttachment(bugId, attachmentId, workspaceCode);
    }

    public BugStatisticsResponse statistics(String workspaceCode) {
        return bugDomainService.statistics(workspaceCode);
    }

    public BugDetailResponse createBugFromCase(Long caseId, String workspaceCode, CreateBugRequest request) {
        CaseEntity caseEntity = caseService.requireCase(caseId);
        CreateBugRequest merged = new CreateBugRequest(
                request.workspaceCode(),
                request.title(),
                request.description(),
                request.priority(),
                request.severity(),
                request.assigneeId(),
                caseEntity.getId(),
                null,
                null,
                request.tags()
        );
        return createBug(workspaceCode, merged, BugSourceType.CASE);
    }

    public BugDetailResponse createBugFromReport(Long reportId, String workspaceCode, CreateBugRequest request) {
        ReportEntity report = executionService.requireReport(reportId);
        CreateBugRequest merged = new CreateBugRequest(
                request.workspaceCode(),
                request.title(),
                request.description(),
                request.priority(),
                request.severity(),
                request.assigneeId(),
                null,
                report.getId(),
                report.getTaskId(),
                request.tags()
        );
        return createBug(workspaceCode, merged, BugSourceType.REPORT);
    }

    public BugEntity requireBug(Long id) {
        return bugDomainService.requireBug(id);
    }

    private List<BugFlowEntity> listFlowEntities(Long bugId) {
        return bugFlowMapper.selectList(new LambdaQueryWrapper<BugFlowEntity>()
                .eq(BugFlowEntity::getBugId, bugId)
                .orderByAsc(BugFlowEntity::getId));
    }

    private List<BugCommentEntity> listCommentEntities(Long bugId) {
        return bugCommentMapper.selectList(new LambdaQueryWrapper<BugCommentEntity>()
                .eq(BugCommentEntity::getBugId, bugId)
                .orderByAsc(BugCommentEntity::getId));
    }

    private List<BugAttachmentEntity> listAttachmentEntities(Long bugId) {
        return bugAttachmentMapper.selectList(new LambdaQueryWrapper<BugAttachmentEntity>()
                .eq(BugAttachmentEntity::getBugId, bugId)
                .orderByAsc(BugAttachmentEntity::getId));
    }

    private BugSummaryResponse toSummary(BugEntity entity) {
        WorkspaceEntity workspace = workspaceService.requireWorkspaceById(entity.getWorkspaceId());
        UserEntity assignee = entity.getAssigneeId() == null ? null : userService.requireUser(entity.getAssigneeId());
        UserEntity reporter = userService.requireUser(entity.getReporterId());
        String updatedByName = resolveUpdatedByName(entity, reporter);
        return new BugSummaryResponse(
                entity.getId(),
                entity.getBugNo(),
                entity.getTitle(),
                JsonUtils.toStringList(entity.getTagsJson()),
                BugPriority.valueOf(entity.getPriority()),
                BugSeverity.valueOf(entity.getSeverity()),
                BugStatus.valueOf(entity.getStatus()),
                assignee == null ? "-" : assignee.getDisplayName(),
                reporter.getDisplayName(),
                entity.getCreatedAt(),
                updatedByName,
                entity.getUpdatedAt(),
                entity.getRelatedCaseId(),
                entity.getRelatedCaseId() == null ? 0 : 1,
                workspace.getWorkspaceCode(),
                workspace.getWorkspaceName()
        );
    }

    private String resolveUpdatedByName(BugEntity entity, UserEntity reporter) {
        LocalDateTime latestTime = entity.getUpdatedAt() == null ? entity.getCreatedAt() : entity.getUpdatedAt();
        String updatedByName = reporter.getDisplayName();

        BugFlowEntity latestFlow = bugFlowMapper.selectOne(new LambdaQueryWrapper<BugFlowEntity>()
                .eq(BugFlowEntity::getBugId, entity.getId())
                .orderByDesc(BugFlowEntity::getCreatedAt)
                .last("limit 1"));
        if (latestFlow != null && latestFlow.getCreatedAt() != null
                && (latestTime == null || !latestFlow.getCreatedAt().isBefore(latestTime))) {
            latestTime = latestFlow.getCreatedAt();
            updatedByName = userService.requireUser(latestFlow.getOperatorId()).getDisplayName();
        }

        BugCommentEntity latestComment = bugCommentMapper.selectOne(new LambdaQueryWrapper<BugCommentEntity>()
                .eq(BugCommentEntity::getBugId, entity.getId())
                .orderByDesc(BugCommentEntity::getCreatedAt)
                .last("limit 1"));
        if (latestComment != null && latestComment.getCreatedAt() != null
                && (latestTime == null || latestComment.getCreatedAt().isAfter(latestTime))) {
            updatedByName = userService.requireUser(latestComment.getCommenterId()).getDisplayName();
        }

        return updatedByName;
    }

    private BugDetailResponse toDetail(BugEntity entity) {
        WorkspaceEntity workspace = workspaceService.requireWorkspaceById(entity.getWorkspaceId());
        UserEntity assignee = entity.getAssigneeId() == null ? null : userService.requireUser(entity.getAssigneeId());
        UserEntity reporter = userService.requireUser(entity.getReporterId());
        String updatedByName = resolveUpdatedByName(entity, reporter);
        List<BugAttachmentEntity> attachmentEntities = listAttachmentEntities(entity.getId());
        List<BugCommentEntity> commentEntities = listCommentEntities(entity.getId());
        List<BugFlowEntity> flowEntities = listFlowEntities(entity.getId());
        return new BugDetailResponse(
                entity.getId(),
                entity.getBugNo(),
                entity.getTitle(),
                entity.getDescription(),
                BugPriority.valueOf(entity.getPriority()),
                BugSeverity.valueOf(entity.getSeverity()),
                BugStatus.valueOf(entity.getStatus()),
                BugSourceType.valueOf(entity.getSourceType()),
                entity.getAssigneeId(),
                assignee == null ? "-" : assignee.getDisplayName(),
                entity.getReporterId(),
                reporter.getDisplayName(),
                entity.getRelatedCaseId(),
                entity.getRelatedReportId(),
                entity.getRelatedTaskId(),
                JsonUtils.toStringList(entity.getTagsJson()),
                workspace.getWorkspaceCode(),
                workspace.getWorkspaceName(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                updatedByName,
                attachmentEntities.stream().map(item -> bugAttachmentSupport.toAttachmentResponse(entity, item)).toList(),
                buildSourceContext(entity, workspace),
                buildActivities(entity, reporter, flowEntities, commentEntities, attachmentEntities),
                flowEntities.stream().map(this::toFlow).toList(),
                commentEntities.stream().map(this::toComment).toList()
        );
    }

    private BugSourceContextResponse buildSourceContext(BugEntity entity, WorkspaceEntity workspace) {
        String workspaceCode = workspace.getWorkspaceCode();
        BugCaseSummaryResponse caseSummary = entity.getRelatedCaseId() == null
                ? null
                : safeCaseSummary(entity.getRelatedCaseId(), workspaceCode);
        BugReportSummaryResponse reportSummary = entity.getRelatedReportId() == null
                ? null
                : safeReportSummary(entity.getRelatedReportId(), workspaceCode);

        Long taskId = entity.getRelatedTaskId();
        if (taskId == null && reportSummary != null) {
            taskId = reportSummary.taskId();
        }
        BugTaskSummaryResponse taskSummary = taskId == null
                ? null
                : safeTaskSummary(taskId, workspaceCode);

        return new BugSourceContextResponse(
                BugSourceType.valueOf(entity.getSourceType()),
                caseSummary,
                reportSummary,
                taskSummary
        );
    }

    private BugCaseSummaryResponse safeCaseSummary(Long caseId, String workspaceCode) {
        try {
            return toCaseSummary(caseService.getCase(caseId, workspaceCode));
        } catch (NotFoundException exception) {
            return null;
        }
    }

    private BugReportSummaryResponse safeReportSummary(Long reportId, String workspaceCode) {
        try {
            return toReportSummary(executionService.getReport(reportId, workspaceCode));
        } catch (NotFoundException exception) {
            return null;
        }
    }

    private BugTaskSummaryResponse safeTaskSummary(Long taskId, String workspaceCode) {
        try {
            return toTaskSummary(executionService.getTask(taskId, workspaceCode));
        } catch (NotFoundException exception) {
            return null;
        }
    }

    private List<BugActivityResponse> buildActivities(
            BugEntity entity,
            UserEntity reporter,
            List<BugFlowEntity> flowEntities,
            List<BugCommentEntity> commentEntities,
            List<BugAttachmentEntity> attachmentEntities
    ) {
        List<BugActivityResponse> activities = new ArrayList<>();
        BugStatus initialStatus = resolveInitialStatus(entity, flowEntities);
        activities.add(new BugActivityResponse(
                "created-" + entity.getId(),
                BugActivityType.CREATED,
                entity.getReporterId(),
                reporter.getDisplayName(),
                entity.getCreatedAt(),
                reporter.getDisplayName() + " 创建了缺陷",
                entity.getBugNo(),
                null,
                initialStatus,
                null,
                null,
                null
        ));

        for (BugFlowEntity flow : flowEntities) {
            UserEntity operator = userService.requireUser(flow.getOperatorId());
            BugStatus fromStatus = BugStatus.valueOf(flow.getFromStatus());
            BugStatus toStatus = BugStatus.valueOf(flow.getToStatus());
            BugActivityType type = toStatus == BugStatus.ASSIGNED ? BugActivityType.ASSIGNED : BugActivityType.STATUS_CHANGED;
            String title = type == BugActivityType.ASSIGNED
                    ? operator.getDisplayName() + " 更新了处理人"
                    : operator.getDisplayName() + " 更新了缺陷状态";
            activities.add(new BugActivityResponse(
                    "flow-" + flow.getId(),
                    type,
                    flow.getOperatorId(),
                    operator.getDisplayName(),
                    flow.getCreatedAt(),
                    title,
                    flow.getActionComment(),
                    fromStatus,
                    toStatus,
                    null,
                    null,
                    null
            ));
        }

        for (BugCommentEntity comment : commentEntities) {
            UserEntity commenter = userService.requireUser(comment.getCommenterId());
            activities.add(new BugActivityResponse(
                    "comment-" + comment.getId(),
                    BugActivityType.COMMENT_ADDED,
                    comment.getCommenterId(),
                    commenter.getDisplayName(),
                    comment.getCreatedAt(),
                    commenter.getDisplayName() + " 添加了评论",
                    comment.getContent(),
                    null,
                    null,
                    null,
                    null,
                    comment.getId()
            ));
        }

        for (BugAttachmentEntity attachment : attachmentEntities) {
            activities.add(new BugActivityResponse(
                    "attachment-" + attachment.getId(),
                    BugActivityType.ATTACHMENT_ADDED,
                    null,
                    null,
                    attachment.getCreatedAt(),
                    "上传了附件",
                    attachment.getFileName(),
                    null,
                    null,
                    attachment.getId(),
                    attachment.getFileName(),
                    null
            ));
        }

        return activities.stream()
                .sorted(Comparator
                        .comparing(BugActivityResponse::occurredAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed()
                        .thenComparing(BugActivityResponse::id, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    private BugStatus resolveInitialStatus(BugEntity entity, List<BugFlowEntity> flowEntities) {
        if (flowEntities.isEmpty()) {
            return BugStatus.valueOf(entity.getStatus());
        }
        BugFlowEntity firstFlow = flowEntities.getFirst();
        if (entity.getAssigneeId() != null
                && BugStatus.TODO.name().equals(firstFlow.getFromStatus())
                && BugStatus.ASSIGNED.name().equals(firstFlow.getToStatus())) {
            return BugStatus.ASSIGNED;
        }
        return BugStatus.valueOf(firstFlow.getFromStatus());
    }

    private BugCaseSummaryResponse toCaseSummary(CaseDetailResponse response) {
        String modulePath = response.directoryName() == null || response.directoryName().isBlank()
                ? response.workspaceName()
                : response.workspaceName() + " / " + response.directoryName();
        return new BugCaseSummaryResponse(
                response.id(),
                response.caseNo(),
                response.title(),
                response.workspaceCode(),
                response.workspaceName(),
                response.directoryId(),
                response.directoryName(),
                modulePath,
                response.executionStatus(),
                response.executionComment() == null || response.executionComment().isBlank() ? response.executionNote() : response.executionComment(),
                response.executedAt()
        );
    }

    private BugReportSummaryResponse toReportSummary(ReportDetailResponse response) {
        return new BugReportSummaryResponse(
                response.id(),
                response.reportName(),
                response.result(),
                response.failureSummary(),
                response.taskId(),
                response.taskName(),
                response.workspaceCode(),
                response.workspaceName()
        );
    }

    private BugTaskSummaryResponse toTaskSummary(TaskDetailResponse response) {
        return new BugTaskSummaryResponse(
                response.id(),
                response.taskName(),
                response.engineType(),
                response.status(),
                response.workspaceCode(),
                response.workspaceName()
        );
    }

    private BugFlowResponse toFlow(BugFlowEntity entity) {
        UserEntity operator = userService.requireUser(entity.getOperatorId());
        return new BugFlowResponse(
                entity.getId(),
                BugStatus.valueOf(entity.getFromStatus()),
                BugStatus.valueOf(entity.getToStatus()),
                entity.getOperatorId(),
                operator.getDisplayName(),
                entity.getActionComment(),
                entity.getCreatedAt()
        );
    }

    private BugCommentResponse toComment(BugCommentEntity entity) {
        UserEntity commenter = userService.requireUser(entity.getCommenterId());
        return new BugCommentResponse(
                entity.getId(),
                entity.getContent(),
                entity.getCommenterId(),
                commenter.getDisplayName(),
                entity.getCreatedAt()
        );
    }

}
