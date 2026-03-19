package com.internship.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("internship_application")
public class InternshipApplicationEntity {
    @TableId(type = IdType.INPUT)
    private String id;
    private String studentId;
    private String organizationId;
    private String status;
    private String batchName;
    private String position;
    private String gradeTarget;
    private LocalDate startDate;
    private LocalDate endDate;
    private String remark;
    private String attachmentsJson;
    private String organizationConfirmation;
    private String organizationFeedback;
    private LocalDate receivedAt;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
    private String reviewComment;
}
