package com.internship.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mentor_application")
public class MentorApplicationEntity {
    @TableId(type = IdType.INPUT)
    private String id;
    private String studentId;
    private String teacherId;
    private String status;
    private String studentRemark;
    private String teacherRemark;
    private String collegeRemark;
    private LocalDateTime createdAt;
    private LocalDateTime teacherReviewedAt;
    private LocalDateTime collegeReviewedAt;
    private LocalDateTime effectiveAt;
}
