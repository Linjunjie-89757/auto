package com.company.autoplatform.auth;

import com.company.autoplatform.workspace.WorkspaceService;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final WorkspaceService workspaceService;

    public AuthService(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    public CurrentUserResponse currentUser() {
        CurrentUserPrincipal currentUser = CurrentUserContext.require();
        String roleCode = PlatformRole.MEMBER;
        if (workspaceService.isSuperAdmin()) {
            roleCode = "SUPER_ADMIN";
        } else if (workspaceService.isPlatformAdmin()) {
            roleCode = "ADMIN";
        }
        return new CurrentUserResponse(
                currentUser.userId(),
                currentUser.username(),
                currentUser.displayName(),
                roleCode,
                workspaceService.listReadableWorkspaceCodes()
        );
    }
}
