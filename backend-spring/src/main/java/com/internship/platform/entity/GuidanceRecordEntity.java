package com.internship.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("guidance_record")
public class GuidanceRecordEntity {
    @TableId(type = IdType.INPUT)
    private String id;
    private String teacherId;
    private String studentId;
    private LocalDateTime guidanceAt;
    private String mode;
    private String problem;
    private String advice;
    private String followUp;
}
