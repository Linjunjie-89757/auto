package com.company.autoplatform.auth;

public final class PlatformRole {

    public static final String SUPER_ADMIN = "SUPER_ADMIN";
    public static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";
    public static final String MEMBER = "MEMBER";
    public static final String VIEWER = "VIEWER";

    private PlatformRole() {
    }

    public static boolean isSuperAdmin(String roleCode) {
        return SUPER_ADMIN.equalsIgnoreCase(roleCode);
    }

    public static boolean isAdminRole(String roleCode) {
        return isSuperAdmin(roleCode) || PLATFORM_ADMIN.equalsIgnoreCase(roleCode);
    }
}
