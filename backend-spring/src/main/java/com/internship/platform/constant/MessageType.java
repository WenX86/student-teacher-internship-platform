package com.internship.platform.constant;

public enum MessageType {
    TODO("待办提醒"),
    RESULT("审核结果"),
    RETURN_NOTICE("退回通知"),
    SYSTEM("系统公告");

    private final String label;

    MessageType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
