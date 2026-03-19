package com.internship.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_account")
public class UserAccountEntity {
    @TableId(type = IdType.INPUT)
    private String id;
    private String account;
    private String name;
    private String role;
    private String password;
    private Boolean mustChangePassword;
    private String status;
    private String collegeId;
    private LocalDateTime lastLoginAt;
}
