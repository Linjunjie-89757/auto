package com.company.autoplatform.workspace;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.autoplatform.auth.CurrentUserContext;
import com.company.autoplatform.auth.CurrentUserPrincipal;
import com.company.autoplatform.auth.PlatformRole;
import com.company.autoplatform.common.BadRequestException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class WorkspaceService {

    private final WorkspaceMapper workspaceMapper;
    private final WorkspaceMemberMapper workspaceMemberMapper;
    private final WorkspaceMemberDomainService workspaceMemberDomainService;
    private final WorkspaceDomainService workspaceDomainService;

    public WorkspaceService(
            WorkspaceMapper workspaceMapper,
            WorkspaceMemberMapper workspaceMemberMapper,
            WorkspaceDomainService workspaceDomainService,
            WorkspaceMemberDomainService workspaceMemberDomainService
    ) {
        this.workspaceMapper = workspaceMapper;
        this.workspaceMemberMapper = workspaceMemberMapper;
        this.workspaceDomainService = workspaceDomainService;
        this.workspaceMemberDomainService = workspaceMemberDomainService;
    }

    public List<WorkspaceItem> listAll() {
        return workspaceDomainService.listAll();
    }

    public List<WorkspaceItem> listSwitchable() {
        return workspaceDomainService.listSwitchable();
    }

    public WorkspaceEntity requireWorkspace(String workspaceCode) {
        return workspaceDomainService.requireWorkspace(workspaceCode);
    }


    public WorkspaceEntity requireReadableWorkspace(String workspaceCode) {
        WorkspaceEntity workspace = requireWorkspace(workspaceCode);
        if (!isPlatformAdmin() && !listReadableWorkspaceIds().contains(workspace.getId())) {
            throw new BadRequestException("当前账号无权访问该工作空间");
        }
        return workspace;
    }

    public WorkspaceEntity requireWritableWorkspace(String workspaceCode) {
        return requireReadableWorkspace(workspaceCode);
    }

    public WorkspaceEntity requireWorkspaceById(Long workspaceId) {
        return workspaceDomainService.requireWorkspaceById(workspaceId);
    }

    public String resolveTargetWorkspace(String headerWorkspaceCode, String bodyWorkspaceCode) {
        String normalized = WorkspaceScope.normalize(headerWorkspaceCode);
        if (WorkspaceScope.isAll(normalized)) {
            if (bodyWorkspaceCode == null || bodyWorkspaceCode.isBlank()) {
                throw new BadRequestException("全部视角下必须明确选择目标空间");
            }
            requireWritableWorkspace(bodyWorkspaceCode);
            return bodyWorkspaceCode;
        }
        requireWritableWorkspace(normalized);
        return normalized;
    }

    public WorkspaceItem createWorkspace(CreateWorkspaceRequest request) {
        return workspaceDomainService.createWorkspace(request);
    }

    public WorkspaceItem updateWorkspace(String workspaceCode, CreateWorkspaceRequest request) {
        return workspaceDomainService.updateWorkspace(workspaceCode, request);
    }

    public void deleteWorkspace(String workspaceCode) {
        workspaceDomainService.deleteWorkspace(workspaceCode);
    }

    public List<WorkspaceMemberItem> listMembers(String workspaceCode) {
        return workspaceMemberDomainService.listMembers(workspaceCode);
    }


    public WorkspaceMemberItem createMember(String workspaceCode, CreateWorkspaceMemberRequest request) {
        return workspaceMemberDomainService.createMember(workspaceCode, request);
    }


    public List<WorkspaceMemberItem> createMembers(String workspaceCode, BatchWorkspaceMemberRequest request) {
        return workspaceMemberDomainService.createMembers(workspaceCode, request);
    }


    public WorkspaceMemberItem updateMember(String workspaceCode, Long memberId, UpdateWorkspaceMemberRequest request) {
        return workspaceMemberDomainService.updateMember(workspaceCode, memberId, request);
    }


    public void deleteMember(String workspaceCode, Long memberId) {
        workspaceMemberDomainService.deleteMember(workspaceCode, memberId);
    }


    public List<WorkspaceEntity> listReadableWorkspaceEntities() {
        if (isPlatformAdmin()) {
            return workspaceMapper.selectList(new LambdaQueryWrapper<WorkspaceEntity>()
                    .eq(WorkspaceEntity::getStatus, 1)
                    .orderByAsc(WorkspaceEntity::getId));
        }
        Set<Long> workspaceIds = new LinkedHashSet<>(listReadableWorkspaceIds());
        if (workspaceIds.isEmpty()) {
            return List.of();
        }
        return workspaceMapper.selectList(new LambdaQueryWrapper<WorkspaceEntity>()
                .eq(WorkspaceEntity::getStatus, 1)
                .in(WorkspaceEntity::getId, workspaceIds)
                .orderByAsc(WorkspaceEntity::getId));
    }

    public List<Long> listReadableWorkspaceIds() {
        CurrentUserPrincipal currentUser = CurrentUserContext.require();
        if (PlatformRole.isAdminRole(currentUser.platformRole())) {
            return workspaceMapper.selectList(new LambdaQueryWrapper<WorkspaceEntity>()
                            .eq(WorkspaceEntity::getStatus, 1)
                            .orderByAsc(WorkspaceEntity::getId))
                    .stream()
                    .map(WorkspaceEntity::getId)
                    .toList();
        }
        return workspaceMemberMapper.selectList(new LambdaQueryWrapper<WorkspaceMemberEntity>()
                        .eq(WorkspaceMemberEntity::getUserId, currentUser.userId())
                        .eq(WorkspaceMemberEntity::getStatus, 1)
                        .orderByAsc(WorkspaceMemberEntity::getId))
                .stream()
                .map(WorkspaceMemberEntity::getWorkspaceId)
                .distinct()
                .toList();
    }

    public List<String> listReadableWorkspaceCodes() {
        return listReadableWorkspaceEntities().stream()
                .map(WorkspaceEntity::getWorkspaceCode)
                .toList();
    }

    public boolean isSuperAdmin() {
        return PlatformRole.isSuperAdmin(CurrentUserContext.require().platformRole());
    }

    public boolean isPlatformAdmin() {
        return PlatformRole.isAdminRole(CurrentUserContext.require().platformRole());
    }

    public void requirePlatformAdmin() {
        if (!isPlatformAdmin()) {
            throw new BadRequestException("只有管理员可执行该操作");
        }
    }

}
