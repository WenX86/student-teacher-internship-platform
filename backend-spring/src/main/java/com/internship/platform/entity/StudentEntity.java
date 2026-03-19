package com.internship.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("student")
public class StudentEntity {
    @TableId(type = IdType.INPUT)
    private String id;
    private String userId;
    private String name;
    private String studentNo;
    private String collegeId;
    private String major;
    private String className;
    private String phone;
    private String internshipType;
    private String internshipStatus;
    private Boolean profileCompleted;
}
