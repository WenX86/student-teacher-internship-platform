package com.internship.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("message_notice")
public class MessageNoticeEntity {
    @TableId(type = IdType.INPUT)
    private String id;
    private String userId;
    private String type;
    private String title;
    private String content;
    private String link;
    @TableField("read_flag")
    private Boolean readFlag;
    private LocalDateTime createdAt;
}
