package com.company.autoplatform.apiautomation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.company.autoplatform.common.BadRequestException;
import com.company.autoplatform.workspace.WorkspaceEntity;
import com.company.autoplatform.workspace.WorkspaceScope;
import com.company.autoplatform.workspace.WorkspaceService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ApiWorkspaceScopeSupport {

    private final WorkspaceService workspaceService;

    public ApiWorkspaceScopeSupport(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    public <T> void applyWorkspaceScope(LambdaQueryWrapper<T> query, SFunction<T, Long> column, String workspaceCode) {
        WorkspaceEntity workspace = resolveScopedWorkspace(workspaceCode);
        if (workspace != null) {
            query.eq(column, workspace.getId());
        } else if (!workspaceService.isPlatformAdmin()) {
            List<Long> workspaceIds = workspaceService.listReadableWorkspaceIds();
            if (workspaceIds.isEmpty()) {
                query.eq(column, -1L);
            } else {
                query.in(column, workspaceIds);
            }
        }
    }

    public WorkspaceEntity resolveScopedWorkspace(String workspaceCode) {
        String normalized = WorkspaceScope.normalize(workspaceCode);
        return WorkspaceScope.isAll(normalized) ? null : workspaceService.requireReadableWorkspace(normalized);
    }

    public void validateReadable(Long workspaceId, String workspaceCode, String message) {
        WorkspaceEntity workspace = resolveScopedWorkspace(workspaceCode);
        if (workspace != null && !workspace.getId().equals(workspaceId)) {
            throw new BadRequestException(message);
        }
        if (workspace == null && !workspaceService.isPlatformAdmin()
                && !workspaceService.listReadableWorkspaceIds().contains(workspaceId)) {
            throw new BadRequestException(message);
        }
    }
}
