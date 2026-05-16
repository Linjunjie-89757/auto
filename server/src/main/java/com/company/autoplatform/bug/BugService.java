package com.company.autoplatform.bug;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.company.autoplatform.auth.CurrentUserContext;
import com.company.autoplatform.casecenter.CaseDetailResponse;
import com.company.autoplatform.casecenter.CaseEntity;
import com.company.autoplatform.casecenter.CaseService;
import com.company.autoplatform.common.BadRequestException;
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
import com.company.autoplatform.workspace.WorkspaceScope;
import com.company.autoplatform.workspace.WorkspaceService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class BugService {

    private final BugMapper bugMapper;
    private final BugFlowMapper bugFlowMapper;
    private final BugCommentMapper bugCommentMapper;
    private final BugAttachmentMapper bugAttachmentMapper;
    private final BugAttachmentStorageService bugAttachmentStorageService;
    private final UserService userService;
    private final WorkspaceService workspaceService;
    private final CaseService caseService;
    private final ExecutionService executionService;

    public BugService(BugMapper bugMapper, BugFlowMapper bugFlowMapper, BugCommentMapper bugCommentMapper,
                      BugAttachmentMapper bugAttachmentMapper, BugAttachmentStorageService bugAttachmentStorageService,
                      UserService userService, WorkspaceService workspaceService, CaseService caseService,
                      ExecutionService executionService) {
        this.bugMapper = bugMapper;
        this.bugFlowMapper = bugFlowMapper;
        this.bugCommentMapper = bugCommentMapper;
        this.bugAttachmentMapper = bugAttachmentMapper;
        this.bugAttachmentStorageService = bugAttachmentStorageService;
        this.userService = userService;
        this.workspaceService = workspaceService;
        this.caseService = caseService;
        this.executionService = executionService;
    }

    public PageResponse<BugSummaryResponse> listBugs(String workspaceCode) {
        WorkspaceEntity workspace = resolveScopedWorkspace(workspaceCode);
        LambdaQueryWrapper<BugEntity> query = new LambdaQueryWrapper<>();
        if (workspace != null) {
            query.eq(BugEntity::getWorkspaceId, workspace.getId());
        } else if (!workspaceService.isPlatformAdmin()) {
            List<Long> workspaceIds = workspaceService.listReadableWorkspaceIds();
            if (workspaceIds.isEmpty()) {
                return new PageResponse<>(List.of(), 0);
            }
            query.in(BugEntity::getWorkspaceId, workspaceIds);
        }
        var items = bugMapper.selectList(query.orderByDesc(BugEntity::getId)).stream()
                .map(this::toSummary)
                .toList();
        return new PageResponse<>(items, items.size());
    }

    public BugDetailResponse getBug(Long id, String workspaceCode) {
        BugEntity entity = requireBug(id);
        validateReadable(entity, workspaceCode);
        return toDetail(entity);
    }

    public BugDetailResponse createBug(String headerWorkspaceCode, CreateBugRequest request, BugSourceType sourceType) {
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(
                workspaceService.resolveTargetWorkspace(headerWorkspaceCode, request.workspaceCode()));
        if (request.assigneeId() != null) {
            userService.requireUser(request.assigneeId());
        }
        if (request.relatedCaseId() != null) {
            caseService.requireCase(request.relatedCaseId());
        }
        if (request.relatedReportId() != null) {
            executionService.requireReport(request.relatedReportId());
        }

        BugEntity entity = new BugEntity();
        entity.setWorkspaceId(workspace.getId());
        entity.setBugNo(generateBugNo());
        entity.setTitle(request.title());
        entity.setDescription(request.description());
        entity.setPriority(request.priority().name());
        entity.setSeverity(request.severity().name());
        entity.setStatus((request.assigneeId() == null ? BugStatus.TODO : BugStatus.ASSIGNED).name());
        entity.setSourceType(sourceType.name());
        entity.setAssigneeId(request.assigneeId());
        entity.setReporterId(CurrentUserContext.get());
        entity.setRelatedCaseId(request.relatedCaseId());
        entity.setRelatedReportId(request.relatedReportId());
        entity.setRelatedTaskId(request.relatedTaskId());
        entity.setTagsJson(JsonUtils.toJson(request.tags()));
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        bugMapper.insert(entity);

        if (request.assigneeId() != null) {
            appendFlow(entity.getId(), BugStatus.TODO, BugStatus.ASSIGNED, "创建时直接指派");
        }
        return toDetail(requireBug(entity.getId()));
    }

    public BugDetailResponse updateBug(Long id, String headerWorkspaceCode, UpdateBugRequest request) {
        BugEntity entity = requireBug(id);
        validateReadable(entity, headerWorkspaceCode);
        WorkspaceEntity workspace = workspaceService.requireWritableWorkspace(
                workspaceService.resolveTargetWorkspace(headerWorkspaceCode, request.workspaceCode()));
        if (!entity.getWorkspaceId().equals(workspace.getId())) {
            throw new BadRequestException("不允许修改缺陷归属空间");
        }
        if (request.assigneeId() != null) {
            userService.requireUser(request.assigneeId());
        }
        if (request.relatedCaseId() != null) {
            caseService.requireCase(request.relatedCaseId());
        }
        entity.setTitle(request.title());
        entity.setDescription(request.description());
        entity.setPriority(request.priority().name());
        entity.setSeverity(request.severity().name());
        entity.setAssigneeId(request.assigneeId());
        entity.setRelatedCaseId(request.relatedCaseId());
        entity.setTagsJson(JsonUtils.toJson(request.tags()));
        entity.setUpdatedAt(LocalDateTime.now());
        bugMapper.update(
                null,
                new LambdaUpdateWrapper<BugEntity>()
                        .eq(BugEntity::getId, entity.getId())
                        .set(BugEntity::getTitle, entity.getTitle())
                        .set(BugEntity::getDescription, entity.getDescription())
                        .set(BugEntity::getPriority, entity.getPriority())
                        .set(BugEntity::getSeverity, entity.getSeverity())
                        .set(BugEntity::getAssigneeId, entity.getAssigneeId())
                        .set(BugEntity::getRelatedCaseId, entity.getRelatedCaseId())
                        .set(BugEntity::getTagsJson, entity.getTagsJson())
                        .set(BugEntity::getUpdatedAt, entity.getUpdatedAt())
        );
        return toDetail(entity);
    }

    public BugDetailResponse assignBug(Long id, String headerWorkspaceCode, AssignBugRequest request) {
        BugEntity entity = requireBug(id);
        validateReadable(entity, headerWorkspaceCode);
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode());
        UserEntity assignee = userService.requireUser(request.assigneeId());
        BugStatus fromStatus = BugStatus.valueOf(entity.getStatus());
        entity.setAssigneeId(assignee.getId());
        entity.setStatus(BugStatus.ASSIGNED.name());
        entity.setUpdatedAt(LocalDateTime.now());
        bugMapper.updateById(entity);
        appendFlow(id, fromStatus, BugStatus.ASSIGNED, "重新指派给 " + assignee.getDisplayName());
        return toDetail(entity);
    }

    public BugDetailResponse transitionBug(Long id, String headerWorkspaceCode, TransitionBugRequest request) {
        BugEntity entity = requireBug(id);
        validateReadable(entity, headerWorkspaceCode);
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode());
        BugStatus fromStatus = BugStatus.valueOf(entity.getStatus());
        if (fromStatus == request.toStatus()) {
            throw new BadRequestException("目标状态不能与当前状态相同");
        }
        entity.setStatus(request.toStatus().name());
        entity.setUpdatedAt(LocalDateTime.now());
        bugMapper.updateById(entity);
        appendFlow(id, fromStatus, request.toStatus(), request.actionComment());
        return toDetail(entity);
    }

    public List<BugCommentResponse> listComments(Long id, String workspaceCode) {
        BugEntity entity = requireBug(id);
        validateReadable(entity, workspaceCode);
        return listCommentEntities(id).stream().map(this::toComment).toList();
    }

    public BugCommentResponse addComment(Long id, String workspaceCode, CreateBugCommentRequest request) {
        BugEntity entity = requireBug(id);
        validateReadable(entity, workspaceCode);
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode());
        BugCommentEntity comment = new BugCommentEntity();
        comment.setBugId(id);
        comment.setContent(request.content());
        comment.setCommenterId(CurrentUserContext.get());
        comment.setCreatedAt(LocalDateTime.now());
        comment.setUpdatedAt(LocalDateTime.now());
        bugCommentMapper.insert(comment);
        return toComment(comment);
    }

    public List<BugAttachmentResponse> uploadBugAttachments(Long bugId, String workspaceCode, List<MultipartFile> files) {
        BugEntity bug = requireBug(bugId);
        validateReadable(bug, workspaceCode);
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(bug.getWorkspaceId()).getWorkspaceCode());

        List<StoredBugFile> storedFiles = bugAttachmentStorageService.storeAll(bug.getWorkspaceId(), bugId, files);
        List<BugAttachmentEntity> createdAttachments = new ArrayList<>();
        try {
            for (int i = 0; i < storedFiles.size(); i++) {
                MultipartFile file = files.get(i);
                StoredBugFile storedFile = storedFiles.get(i);
                BugAttachmentEntity attachment = new BugAttachmentEntity();
                attachment.setBugId(bugId);
                attachment.setWorkspaceId(bug.getWorkspaceId());
                attachment.setFileName(file.getOriginalFilename());
                attachment.setStoredPath(storedFile.storedPath());
                attachment.setContentType(storedFile.contentType());
                attachment.setFileSize(storedFile.fileSize());
                attachment.setCreatedAt(LocalDateTime.now());
                attachment.setUpdatedAt(LocalDateTime.now());
                bugAttachmentMapper.insert(attachment);
                createdAttachments.add(attachment);
            }
        } catch (RuntimeException exception) {
            for (BugAttachmentEntity attachment : createdAttachments) {
                if (attachment.getId() != null) {
                    bugAttachmentMapper.deleteById(attachment.getId());
                }
                bugAttachmentStorageService.delete(attachment.getStoredPath());
            }
            for (StoredBugFile storedFile : storedFiles) {
                bugAttachmentStorageService.delete(storedFile.storedPath());
            }
            throw exception;
        }

        bug.setUpdatedAt(LocalDateTime.now());
        bugMapper.updateById(bug);
        return createdAttachments.stream().map(attachment -> toAttachmentResponse(bug, attachment)).toList();
    }

    public void deleteBugAttachment(Long bugId, Long attachmentId, String workspaceCode) {
        BugEntity bug = requireBug(bugId);
        validateReadable(bug, workspaceCode);
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(bug.getWorkspaceId()).getWorkspaceCode());
        BugAttachmentEntity attachment = requireAttachment(attachmentId);
        if (!attachment.getBugId().equals(bugId)) {
            throw new BadRequestException("附件不属于当前缺陷");
        }
        bugAttachmentMapper.deleteById(attachmentId);
        bugAttachmentStorageService.delete(attachment.getStoredPath());
        bug.setUpdatedAt(LocalDateTime.now());
        bugMapper.updateById(bug);
    }

    public BugFileDownload downloadBugAttachment(Long bugId, Long attachmentId, String workspaceCode) {
        BugEntity bug = requireBug(bugId);
        validateReadable(bug, workspaceCode);
        BugAttachmentEntity attachment = requireAttachment(attachmentId);
        if (!attachment.getBugId().equals(bugId)) {
            throw new BadRequestException("附件不属于当前缺陷");
        }
        return bugAttachmentStorageService.load(attachment);
    }

    public BugStatisticsResponse statistics(String workspaceCode) {
        WorkspaceEntity workspace = resolveScopedWorkspace(workspaceCode);
        LambdaQueryWrapper<BugEntity> query = new LambdaQueryWrapper<>();
        if (workspace != null) {
            query.eq(BugEntity::getWorkspaceId, workspace.getId());
        } else if (!workspaceService.isPlatformAdmin()) {
            List<Long> workspaceIds = workspaceService.listReadableWorkspaceIds();
            if (workspaceIds.isEmpty()) {
                return new BugStatisticsResponse(0, 0, 0, 0, 0, 0, 0);
            }
            query.in(BugEntity::getWorkspaceId, workspaceIds);
        }
        var scoped = bugMapper.selectList(query);
        return new BugStatisticsResponse(
                scoped.size(),
                scoped.stream().filter(item -> BugStatus.valueOf(item.getStatus()) == BugStatus.TODO).count(),
                scoped.stream().filter(item -> BugStatus.valueOf(item.getStatus()) == BugStatus.ASSIGNED).count(),
                scoped.stream().filter(item -> BugStatus.valueOf(item.getStatus()) == BugStatus.IN_PROGRESS).count(),
                scoped.stream().filter(item -> BugStatus.valueOf(item.getStatus()) == BugStatus.PENDING_VERIFY).count(),
                scoped.stream().filter(item -> BugStatus.valueOf(item.getStatus()) == BugStatus.CLOSED).count(),
                scoped.stream().filter(item -> BugStatus.valueOf(item.getStatus()) == BugStatus.REJECTED).count()
        );
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
        BugEntity entity = bugMapper.selectById(id);
        if (entity == null) {
            throw new NotFoundException("缺陷不存在");
        }
        return entity;
    }

    private BugAttachmentEntity requireAttachment(Long attachmentId) {
        BugAttachmentEntity attachment = bugAttachmentMapper.selectById(attachmentId);
        if (attachment == null) {
            throw new NotFoundException("附件不存在");
        }
        return attachment;
    }

    private WorkspaceEntity resolveScopedWorkspace(String workspaceCode) {
        String normalized = WorkspaceScope.normalize(workspaceCode);
        return WorkspaceScope.isAll(normalized) ? null : workspaceService.requireReadableWorkspace(normalized);
    }

    private void validateReadable(BugEntity entity, String workspaceCode) {
        WorkspaceEntity workspace = resolveScopedWorkspace(workspaceCode);
        if (workspace != null && !workspace.getId().equals(entity.getWorkspaceId())) {
            throw new BadRequestException("当前空间上下文不可访问该缺陷");
        }
        if (workspace == null && !workspaceService.isPlatformAdmin()
                && !workspaceService.listReadableWorkspaceIds().contains(entity.getWorkspaceId())) {
            throw new BadRequestException("当前空间上下文不可访问该缺陷");
        }
    }

    private void appendFlow(Long bugId, BugStatus fromStatus, BugStatus toStatus, String comment) {
        BugFlowEntity flow = new BugFlowEntity();
        flow.setBugId(bugId);
        flow.setFromStatus(fromStatus.name());
        flow.setToStatus(toStatus.name());
        flow.setOperatorId(CurrentUserContext.get());
        flow.setActionComment(comment == null || comment.isBlank() ? "状态流转" : comment);
        flow.setCreatedAt(LocalDateTime.now());
        flow.setUpdatedAt(LocalDateTime.now());
        bugFlowMapper.insert(flow);
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
                attachmentEntities.stream().map(item -> toAttachmentResponse(entity, item)).toList(),
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
                : toCaseSummary(caseService.getCase(entity.getRelatedCaseId(), workspaceCode));
        BugReportSummaryResponse reportSummary = entity.getRelatedReportId() == null
                ? null
                : toReportSummary(executionService.getReport(entity.getRelatedReportId(), workspaceCode));

        Long taskId = entity.getRelatedTaskId();
        if (taskId == null && reportSummary != null) {
            taskId = reportSummary.taskId();
        }
        BugTaskSummaryResponse taskSummary = taskId == null
                ? null
                : toTaskSummary(executionService.getTask(taskId, workspaceCode));

        return new BugSourceContextResponse(
                BugSourceType.valueOf(entity.getSourceType()),
                caseSummary,
                reportSummary,
                taskSummary
        );
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
                    ? operator.getDisplayName() + " 更新了负责人"
                    : operator.getDisplayName() + " 更新了状态";
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

    private BugAttachmentResponse toAttachmentResponse(BugEntity bug, BugAttachmentEntity attachment) {
        return new BugAttachmentResponse(
                attachment.getId(),
                attachment.getFileName(),
                attachment.getContentType(),
                attachment.getFileSize(),
                "/api/bugs/" + bug.getId() + "/attachments/" + attachment.getId() + "/download",
                attachment.getCreatedAt()
        );
    }

    private String generateBugNo() {
        int nextNumber = bugMapper.selectList(new LambdaQueryWrapper<BugEntity>()).stream()
                .map(BugEntity::getBugNo)
                .map(this::parseBugSequence)
                .max(Integer::compareTo)
                .orElse(0) + 1;
        return "BUG-" + String.format("%03d", nextNumber);
    }

    private int parseBugSequence(String bugNo) {
        if (bugNo == null || !bugNo.startsWith("BUG-")) {
            return 0;
        }
        String suffix = bugNo.substring(4);
        for (int i = 0; i < suffix.length(); i++) {
            if (!Character.isDigit(suffix.charAt(i))) {
                return 0;
            }
        }
        try {
            return Integer.parseInt(suffix);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }
}
