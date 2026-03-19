package com.internship.platform.constant;

public enum FormStatus {
    DRAFT("草稿"),
    TEACHER_REVIEWING("教师审核中"),
    TEACHER_RETURNED("教师退回"),
    COLLEGE_REVIEWING("学院审核中"),
    COLLEGE_RETURNED("学院退回"),
    ARCHIVED("已归档");

    private final String label;

    FormStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
