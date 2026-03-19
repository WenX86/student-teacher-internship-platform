package com.internship.platform.constant;

public enum InternshipApplicationStatus {
    PENDING_COLLEGE("待学院审批"),
    REJECTED("已退回"),
    APPROVED("已通过");

    private final String label;

    InternshipApplicationStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
