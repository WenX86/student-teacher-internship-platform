package com.internship.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("college_application")
public class CollegeApplicationEntity {
    @TableId(type = IdType.INPUT)
    private String id;
    private String schoolName;
    private String collegeName;
    private String contactName;
    private String contactPhone;
    private String description;
    private String status;
    private String reviewComment;
    private LocalDateTime createdAt;
}
