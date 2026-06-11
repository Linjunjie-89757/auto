package com.company.autoplatform.bug;

import com.company.autoplatform.auth.CurrentUserContext;
import com.company.autoplatform.common.BadRequestException;
import com.company.autoplatform.user.UserEntity;
import com.company.autoplatform.user.UserService;
import com.company.autoplatform.workspace.WorkspaceService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class BugWorkflowDomainService {

    private final BugDomainService bugDomainService;
    private final BugMapper bugMapper;
    private final BugFlowMapper bugFlowMapper;
    private final UserService userService;
    private final WorkspaceService workspaceService;

    public BugWorkflowDomainService(
            BugDomainService bugDomainService,
            BugMapper bugMapper,
            BugFlowMapper bugFlowMapper,
            UserService userService,
            WorkspaceService workspaceService
    ) {
        this.bugDomainService = bugDomainService;
        this.bugMapper = bugMapper;
        this.bugFlowMapper = bugFlowMapper;
        this.userService = userService;
        this.workspaceService = workspaceService;
    }

    public BugEntity assignBug(Long id, String headerWorkspaceCode, AssignBugRequest request) {
        BugEntity entity = bugDomainService.requireBug(id);
        bugDomainService.validateReadable(entity, headerWorkspaceCode);
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode());
        UserEntity assignee = userService.requireUser(request.assigneeId());

        BugStatus fromStatus = BugStatus.valueOf(entity.getStatus());
        entity.setAssigneeId(assignee.getId());
        entity.setStatus(BugStatus.ASSIGNED.name());
        entity.setUpdatedAt(LocalDateTime.now());
        bugMapper.updateById(entity);
        appendFlow(id, fromStatus, BugStatus.ASSIGNED, "分配处理人: " + assignee.getDisplayName());
        return entity;
    }

    public BugEntity transitionBug(Long id, String headerWorkspaceCode, TransitionBugRequest request) {
        BugEntity entity = bugDomainService.requireBug(id);
        bugDomainService.validateReadable(entity, headerWorkspaceCode);
        workspaceService.requireWritableWorkspace(workspaceService.requireWorkspaceById(entity.getWorkspaceId()).getWorkspaceCode());

        BugStatus fromStatus = BugStatus.valueOf(entity.getStatus());
        if (fromStatus == request.toStatus()) {
            throw new BadRequestException("目标状态与当前状态一致，无需流转");
        }
        entity.setStatus(request.toStatus().name());
        entity.setUpdatedAt(LocalDateTime.now());
        bugMapper.updateById(entity);
        appendFlow(id, fromStatus, request.toStatus(), request.actionComment());
        return entity;
    }

    private void appendFlow(Long bugId, BugStatus fromStatus, BugStatus toStatus, String comment) {
        BugFlowEntity flow = new BugFlowEntity();
        flow.setBugId(bugId);
        flow.setFromStatus(fromStatus.name());
        flow.setToStatus(toStatus.name());
        flow.setOperatorId(CurrentUserContext.get());
        flow.setActionComment(comment == null || comment.isBlank() ? "状态变更" : comment);
        flow.setCreatedAt(LocalDateTime.now());
        flow.setUpdatedAt(LocalDateTime.now());
        bugFlowMapper.insert(flow);
    }
}
