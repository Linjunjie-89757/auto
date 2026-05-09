package com.company.autoplatform.workspace;

public final class WorkspaceScope {

    public static final String HEADER = "X-Workspace-Code";
    public static final String ALL = "ALL";
    public static final String DEFAULT = "account-open";

    private WorkspaceScope() {
    }

    public static boolean isAll(String workspaceCode) {
        return ALL.equalsIgnoreCase(workspaceCode);
    }

    public static String normalize(String workspaceCode) {
        if (workspaceCode == null || workspaceCode.isBlank()) {
            return DEFAULT;
        }
        return workspaceCode.trim();
    }
}
