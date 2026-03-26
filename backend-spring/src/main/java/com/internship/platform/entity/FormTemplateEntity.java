package com.internship.platform.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("form_template")
public class FormTemplateEntity {
    @TableId
    private String code;
    private String name;
    private String category;
    private String description;
    private String applicableTypesJson;
    private String fieldSchemaJson;
    private Boolean enabled;
    private Integer sortNo;
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime updatedAt;
}
