package com.internship.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public final class Requests {

    private Requests() {
    }

    public record LoginRequest(
            @NotBlank(message = "账号不能为空")
            String account,
            @NotBlank(message = "密码不能为空")
            String password
    ) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "新密码不能为空")
            String newPassword
    ) {
    }

    public record StudentCreateRequest(
            @NotBlank(message = "姓名不能为空")
            String name,
            @NotBlank(message = "学号不能为空")
            String studentNo,
            @NotBlank(message = "专业不能为空")
            String major,
            @NotBlank(message = "班级不能为空")
            String className,
            @NotBlank(message = "电话不能为空")
            String phone,
            @NotBlank(message = "实习类型不能为空")
            String internshipType
    ) {
    }

    public record TeacherCreateRequest(
            @NotBlank(message = "姓名不能为空")
            String name,
            @NotBlank(message = "工号不能为空")
            String employeeNo,
            @NotBlank(message = "教研室不能为空")
            String department,
            @NotBlank(message = "电话不能为空")
            String phone
    ) {
    }

    public record OrganizationCreateRequest(
            @NotBlank(message = "单位名称不能为空")
            String name,
            @NotBlank(message = "地址不能为空")
            String address,
            @NotBlank(message = "联系人不能为空")
            String contactName,
            @NotBlank(message = "联系电话不能为空")
            String contactPhone,
            @NotBlank(message = "单位性质不能为空")
            String nature,
            @NotBlank(message = "合作状态不能为空")
            String cooperationStatus
    ) {
    }

    public record MentorApplicationCreateRequest(
            @NotBlank(message = "指导教师不能为空")
            String teacherId,
            String studentRemark
    ) {
    }

    public record DecisionRequest(
            @NotNull(message = "审核结论不能为空")
            Boolean approved,
            String comment
    ) {
    }

    public record InternshipApplicationCreateRequest(
            @NotBlank(message = "实习单位不能为空")
            String organizationId,
            @NotBlank(message = "实习批次不能为空")
            String batchName,
            @NotBlank(message = "岗位不能为空")
            String position,
            @NotBlank(message = "对象不能为空")
            String gradeTarget,
            @NotBlank(message = "开始日期不能为空")
            String startDate,
            @NotBlank(message = "结束日期不能为空")
            String endDate,
            String remark,
            List<Map<String, Object>> attachments
    ) {
    }

    public record InternshipReviewRequest(
            @NotNull(message = "审核结论不能为空")
            Boolean approved,
            String organizationConfirmation,
            String organizationFeedback,
            String receivedAt,
            String comment
    ) {
    }

    public record FormSaveRequest(
            @NotBlank(message = "模板不能为空")
            String templateCode,
            @NotNull(message = "表单内容不能为空")
            Map<String, Object> content,
            Boolean submit,
            List<Map<String, Object>> attachments
    ) {
    }

    public record FormReviewRequest(
            @NotNull(message = "审核结论不能为空")
            Boolean approved,
            Integer score,
            String comment
    ) {
    }

    public record BatchFormReviewRequest(
            @NotNull(message = "归档表单列表不能为空")
            List<String> formIds,
            @NotNull(message = "审核结论不能为空")
            Boolean approved,
            Integer score,
            String comment
    ) {
    }

    public record FormTemplateCreateRequest(
            @NotBlank(message = "模板编码不能为空")
            String code,
            @NotBlank(message = "模板名称不能为空")
            String name,
            @NotBlank(message = "模板分类不能为空")
            String category,
            String description,
            @NotNull(message = "适用实习类型不能为空")
            List<String> applicableTypes,
            @NotNull(message = "字段配置不能为空")
            List<Map<String, Object>> fieldSchema,
            Boolean enabled,
            Integer sortNo
    ) {
    }

    public record FormTemplateUpdateRequest(
            @NotBlank(message = "模板名称不能为空")
            String name,
            @NotBlank(message = "模板分类不能为空")
            String category,
            String description,
            @NotNull(message = "适用实习类型不能为空")
            List<String> applicableTypes,
            @NotNull(message = "字段配置不能为空")
            List<Map<String, Object>> fieldSchema,
            Boolean enabled,
            Integer sortNo
    ) {
    }

    public record StatusToggleRequest(
            @NotNull(message = "启停状态不能为空")
            Boolean enabled
    ) {
    }

    public record SystemSettingUpdateItem(
            @NotBlank(message = "参数键不能为空")
            String key,
            @NotBlank(message = "参数值不能为空")
            String value
    ) {
    }

    public record SystemSettingSaveRequest(
            @NotNull(message = "参数列表不能为空")
            List<SystemSettingUpdateItem> items
    ) {
    }

    public record GuidanceRecordCreateRequest(
            @NotBlank(message = "学生不能为空")
            String studentId,
            @NotBlank(message = "指导时间不能为空")
            String guidanceAt,
            @NotBlank(message = "指导方式不能为空")
            String mode,
            String problem,
            String advice,
            String followUp
    ) {
    }

    public record EvaluationSaveRequest(
            @NotBlank(message = "学生不能为空")
            String studentId,
            String stageComment,
            String summaryComment,
            Integer finalScore,
            List<Map<String, Object>> dimensionScores,
            String strengthsComment,
            String improvementComment
    ) {
    }

    public record EvaluationCollegeConfirmRequest(
            String collegeComment
    ) {
    }

    public record EvaluationCollegeReturnRequest(
            String collegeComment
    ) {
    }

    public record BatchEvaluationCollegeConfirmRequest(
            @NotNull(message = "评价列表不能为空")
            List<String> evaluationIds,
            String collegeComment
    ) {
    }

    public record BatchEvaluationCollegeReturnRequest(
            @NotNull(message = "评价列表不能为空")
            List<String> evaluationIds,
            String collegeComment
    ) {
    }

    public record CollegeCreateRequest(
            @NotBlank(message = "学院名称不能为空")
            String name,
            String contactName,
            String contactPhone,
            String description
    ) {
    }

    public record CollegeAdminCreateRequest(
            @NotBlank(message = "学院不能为空")
            String collegeId,
            @NotBlank(message = "管理员姓名不能为空")
            String name,
            String account
    ) {
    }
}
