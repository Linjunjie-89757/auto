package com.company.autoplatform.auth;

public final class WorkspaceRole {

    public static final String ADMIN = "ADMIN";
    public static final String MEMBER = "MEMBER";
    public static final String VIEWER = "VIEWER";

    private WorkspaceRole() {
    }

    public static boolean canWrite(String roleCode) {
        return ADMIN.equalsIgnoreCase(roleCode) || MEMBER.equalsIgnoreCase(roleCode);
    }
}
