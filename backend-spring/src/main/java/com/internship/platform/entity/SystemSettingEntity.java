package com.internship.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("system_setting")
public class SystemSettingEntity {
    @TableId(type = IdType.INPUT)
    private String settingKey;
    private String settingValue;
    private String category;
    private String name;
    private String description;
    private LocalDateTime updatedAt;
}
