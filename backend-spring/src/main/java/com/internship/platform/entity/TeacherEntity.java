package com.internship.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("teacher")
public class TeacherEntity {
    @TableId(type = IdType.INPUT)
    private String id;
    private String userId;
    private String name;
    private String employeeNo;
    private String collegeId;
    private String department;
    private String phone;
    private String status;
}
