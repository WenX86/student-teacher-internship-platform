package com.internship.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audit_log")
public class AuditLogEntity {
    @TableId(type = IdType.INPUT)
    private String id;
    private String type;
    private String operatorId;
    private String action;
    private String detail;
    private LocalDateTime createdAt;
}
