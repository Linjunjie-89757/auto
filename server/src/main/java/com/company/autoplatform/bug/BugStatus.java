package com.company.autoplatform.bug;

public enum BugStatus {
    TODO("待指派"),
    ASSIGNED("已指派"),
    IN_PROGRESS("处理中"),
    PENDING_VERIFY("待验证"),
    CLOSED("已关闭"),
    REJECTED("已拒绝");

    private final String label;

    BugStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
