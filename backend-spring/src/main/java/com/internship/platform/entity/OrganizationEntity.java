package com.internship.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("organization")
public class OrganizationEntity {
    @TableId(type = IdType.INPUT)
    private String id;
    private String collegeId;
    private String name;
    private String address;
    private String contactName;
    private String contactPhone;
    private String nature;
    private String cooperationStatus;
}
