package com.internship.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("form_instance")
public class FormInstanceEntity {
    @TableId(type = IdType.INPUT)
    private String id;
    private String studentId;
    private String templateCode;
    private String templateName;
    private String category;
    private String status;
    private Integer versionNo;
    private String contentJson;
    private String attachmentsJson;
    private String teacherComment;
    private String collegeComment;
    private Integer score;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime submittedAt;
    private LocalDateTime teacherReviewedAt;
    private LocalDateTime collegeReviewedAt;
    private String historyJson;
}
