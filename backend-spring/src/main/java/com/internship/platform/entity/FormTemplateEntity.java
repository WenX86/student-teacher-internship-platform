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
    private String applicableTypesJson;
}
