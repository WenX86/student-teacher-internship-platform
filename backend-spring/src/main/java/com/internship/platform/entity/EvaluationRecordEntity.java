package com.internship.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("evaluation_record")
public class EvaluationRecordEntity {
    @TableId(type = IdType.INPUT)
    private String id;
    private String teacherId;
    private String studentId;
    private String stageComment;
    private String summaryComment;
    private Integer finalScore;
    @TableField("submitted_to_college")
    private Boolean submittedToCollege;
    @TableField("confirmed_by_college")
    private Boolean confirmedByCollege;
}
