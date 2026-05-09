package com.company.autoplatform.casecenter;

import java.util.List;

public record CaseDirectoryWorkspaceResponse(
        String workspaceCode,
        String workspaceName,
        List<CaseDirectoryNodeResponse> children
) {
}
