package com.internship.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("college")
public class CollegeEntity {
    @TableId(type = IdType.INPUT)
    private String id;
    private String schoolName;
    private String name;
    private String contactName;
    private String contactPhone;
    private String description;
}
