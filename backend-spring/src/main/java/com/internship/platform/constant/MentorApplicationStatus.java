package com.internship.platform.constant;

public enum MentorApplicationStatus {
    PENDING_TEACHER("待教师确认"),
    TEACHER_REJECTED("教师驳回"),
    PENDING_COLLEGE("待学院复核"),
    COLLEGE_REJECTED("学院驳回"),
    EFFECTIVE("已生效");

    private final String label;

    MentorApplicationStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
