package com.internship.platform.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.platform.common.BizException;
import com.internship.platform.constant.FormStatus;
import com.internship.platform.constant.InternshipApplicationStatus;
import com.internship.platform.constant.MentorApplicationStatus;
import com.internship.platform.constant.RoleType;
import com.internship.platform.dto.Requests;
import com.internship.platform.entity.*;
import com.internship.platform.mapper.*;
import com.internship.platform.security.LoginUser;
import com.internship.platform.security.TokenSessionService;
import com.internship.platform.util.IdGenerator;
import com.internship.platform.util.PasswordUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PhaseOneService {

    private record SettingOption(String label, String value) {
    }

    private record SystemSettingSpec(
            String key,
            String category,
            String name,
            String description,
            String defaultValue,
            String valueType,
            int sortNo,
            List<SettingOption> options
    ) {
    }

    private static final List<SettingOption> ALERT_LEVEL_OPTIONS = List.of(
            new SettingOption("提示", "info"),
            new SettingOption("预警", "warning"),
            new SettingOption("高危", "danger")
    );

    private static final List<SystemSettingSpec> SYSTEM_SETTING_SPECS = List.of(
            new SystemSettingSpec("teacher_review_timeout_days", "REMINDER", "教师审核超时天数", "学生提交表单后，教师超过该天数未处理则产生预警。", "2", "INTEGER", 10, List.of()),
            new SystemSettingSpec("teacher_review_alert_level", "REMINDER", "教师审核预警级别", "控制教师审核超时预警的展示级别。", "warning", "SELECT", 11, ALERT_LEVEL_OPTIONS),
            new SystemSettingSpec("teacher_review_remind_enabled", "REMINDER", "教师审核允许催办", "控制教师审核超时预警是否允许一键催办。", "1", "BOOLEAN", 12, List.of()),
            new SystemSettingSpec("teacher_review_reminder_title_template", "REMINDER", "教师审核催办标题模板", "可使用 {title}、{targetName}、{overdueDays} 等变量。", "{title}", "TEXT", 13, List.of()),
            new SystemSettingSpec("teacher_review_reminder_content_template", "REMINDER", "教师审核催办内容模板", "可使用 {title}、{targetName}、{overdueDays} 等变量。", "请尽快处理 {title}，当前已超时 {overdueDays} 天。", "TEXT", 14, List.of()),
            new SystemSettingSpec("college_review_timeout_days", "REMINDER", "学院处理超时天数", "教师审核通过后的学院审批、归档与复核超过该天数则产生预警。", "2", "INTEGER", 20, List.of()),
            new SystemSettingSpec("college_review_alert_level", "REMINDER", "学院处理预警级别", "控制学院审批、归档与复核超时预警的展示级别。", "warning", "SELECT", 21, ALERT_LEVEL_OPTIONS),
            new SystemSettingSpec("college_review_remind_enabled", "REMINDER", "学院处理允许催办", "控制学院审批、归档与复核超时预警是否允许一键催办。", "1", "BOOLEAN", 22, List.of()),
            new SystemSettingSpec("college_review_reminder_title_template", "REMINDER", "学院处理催办标题模板", "可使用 {title}、{targetName}、{overdueDays} 等变量。", "{title}", "TEXT", 23, List.of()),
            new SystemSettingSpec("college_review_reminder_content_template", "REMINDER", "学院处理催办内容模板", "可使用 {title}、{targetName}、{overdueDays} 等变量。", "请尽快处理 {title}，当前已超时 {overdueDays} 天。", "TEXT", 24, List.of()),
            new SystemSettingSpec("student_resubmit_timeout_days", "REMINDER", "学生整改超时天数", "学生被退回后超过该天数未重新提交则产生预警。", "2", "INTEGER", 30, List.of()),
            new SystemSettingSpec("student_resubmit_alert_level", "REMINDER", "学生整改预警级别", "控制学生退回未整改预警的展示级别。", "danger", "SELECT", 31, ALERT_LEVEL_OPTIONS),
            new SystemSettingSpec("student_resubmit_remind_enabled", "REMINDER", "学生整改允许催办", "控制学生退回未整改预警是否允许一键催办。", "1", "BOOLEAN", 32, List.of()),
            new SystemSettingSpec("student_resubmit_reminder_title_template", "REMINDER", "学生整改催办标题模板", "可使用 {title}、{targetName}、{overdueDays} 等变量。", "{title}", "TEXT", 33, List.of()),
            new SystemSettingSpec("student_resubmit_reminder_content_template", "REMINDER", "学生整改催办内容模板", "可使用 {title}、{targetName}、{overdueDays} 等变量。", "请尽快修改并重新提交相关材料，当前已超时 {overdueDays} 天。", "TEXT", 34, List.of()),
            new SystemSettingSpec("evaluation_confirm_timeout_days", "REMINDER", "评价确认超时天数", "教师提交评价后学院超过该天数未确认则产生预警。", "3", "INTEGER", 40, List.of()),
            new SystemSettingSpec("evaluation_confirm_alert_level", "REMINDER", "评价确认预警级别", "控制评价确认超时预警的展示级别。", "warning", "SELECT", 41, ALERT_LEVEL_OPTIONS),
            new SystemSettingSpec("evaluation_confirm_remind_enabled", "REMINDER", "评价确认允许催办", "控制评价确认超时预警是否允许一键催办。", "1", "BOOLEAN", 42, List.of()),
            new SystemSettingSpec("evaluation_confirm_reminder_title_template", "REMINDER", "评价确认催办标题模板", "可使用 {title}、{targetName}、{overdueDays} 等变量。", "{title}", "TEXT", 43, List.of()),
            new SystemSettingSpec("evaluation_confirm_reminder_content_template", "REMINDER", "评价确认催办内容模板", "可使用 {title}、{targetName}、{overdueDays} 等变量。", "请尽快确认 {title}，当前已超时 {overdueDays} 天。", "TEXT", 44, List.of())
    );

    private static final Map<String, SystemSettingSpec> SYSTEM_SETTING_SPEC_MAP = SYSTEM_SETTING_SPECS.stream()
            .collect(Collectors.toMap(SystemSettingSpec::key, item -> item, (left, right) -> left, LinkedHashMap::new));

    private final ObjectMapper objectMapper;
    private final TokenSessionService tokenSessionService;
    private final CollegeMapper collegeMapper;
    private final UserAccountMapper userAccountMapper;
    private final TeacherMapper teacherMapper;
    private final StudentMapper studentMapper;
    private final OrganizationMapper organizationMapper;
    private final MentorApplicationMapper mentorApplicationMapper;
    private final InternshipApplicationMapper internshipApplicationMapper;
    private final FormTemplateMapper formTemplateMapper;
    private final FormInstanceMapper formInstanceMapper;
    private final MessageNoticeMapper messageNoticeMapper;
    private final GuidanceRecordMapper guidanceRecordMapper;
    private final EvaluationRecordMapper evaluationRecordMapper;
    private final CollegeApplicationMapper collegeApplicationMapper;
    private final AuditLogMapper auditLogMapper;
    private final SystemSettingMapper systemSettingMapper;

    public Map<String, Object> login(Requests.LoginRequest request) {
        UserAccountEntity user = userAccountMapper.selectOne(
                Wrappers.<UserAccountEntity>lambdaQuery().eq(UserAccountEntity::getAccount, request.account())
        );
        if (user == null || !"ACTIVE".equals(user.getStatus()) || !Objects.equals(user.getPassword(), PasswordUtils.sha256(request.password()))) {
            throw new BizException("账号或密码错误");
        }

        user.setLastLoginAt(LocalDateTime.now());
        userAccountMapper.updateById(user);
        insertAudit("LOGIN", user.getId(), "用户登录", user.getName() + " 登录系统");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("token", tokenSessionService.createToken(user.getId()));
        payload.put("user", toUserPayload(user));
        return payload;
    }

    public Map<String, Object> currentUserPayload(LoginUser loginUser) {
        return toUserPayload(requireUser(loginUser.id()));
    }

    @Transactional
    public void changePassword(LoginUser loginUser, Requests.ChangePasswordRequest request) {
        UserAccountEntity user = requireUser(loginUser.id());
        user.setPassword(PasswordUtils.sha256(request.newPassword()));
        user.setMustChangePassword(false);
        userAccountMapper.updateById(user);
        insertAudit("SECURITY", user.getId(), "修改密码", user.getName() + " 修改密码");
    }

    public List<Map<String, Object>> loginRecords(LoginUser loginUser) {
        return auditLogMapper.selectList(
                        Wrappers.<AuditLogEntity>lambdaQuery()
                                .eq(AuditLogEntity::getType, "LOGIN")
                                .eq(AuditLogEntity::getOperatorId, loginUser.id())
                                .orderByDesc(AuditLogEntity::getCreatedAt)
                ).stream()
                .map(this::toAuditPayload)
                .toList();
    }

    public Map<String, Object> dashboard(LoginUser loginUser) {
        return switch (RoleType.valueOf(loginUser.role())) {
            case STUDENT -> studentDashboard(loginUser);
            case TEACHER -> teacherDashboard(loginUser);
            case COLLEGE_ADMIN -> collegeDashboard(loginUser);
            case SUPER_ADMIN -> superDashboard();
        };
    }

    public List<Map<String, Object>> messages(LoginUser loginUser) {
        return messageNoticeMapper.selectList(
                        Wrappers.<MessageNoticeEntity>lambdaQuery()
                                .eq(MessageNoticeEntity::getUserId, loginUser.id())
                                .orderByDesc(MessageNoticeEntity::getCreatedAt)
                ).stream()
                .map(this::toMessagePayload)
                .toList();
    }

    @Transactional
    public void markMessageRead(LoginUser loginUser, String messageId) {
        MessageNoticeEntity message = messageNoticeMapper.selectOne(
                Wrappers.<MessageNoticeEntity>lambdaQuery()
                        .eq(MessageNoticeEntity::getId, messageId)
                        .eq(MessageNoticeEntity::getUserId, loginUser.id())
        );
        if (message == null) {
            throw new BizException("消息不存在");
        }
        message.setReadFlag(true);
        messageNoticeMapper.updateById(message);
    }

    public List<Map<String, Object>> riskAlerts(LoginUser loginUser) {
        return switch (RoleType.valueOf(loginUser.role())) {
            case STUDENT -> studentRiskAlerts(loginUser);
            case TEACHER -> teacherRiskAlerts(loginUser);
            case COLLEGE_ADMIN -> collegeRiskAlerts(loginUser);
            case SUPER_ADMIN -> List.of();
        };
    }

    @Transactional
    public void sendRiskReminder(LoginUser loginUser, String alertId) {
        Map<String, Object> alert = riskAlerts(loginUser).stream()
                .filter(item -> Objects.equals(item.get("id"), alertId))
                .findFirst()
                .orElseThrow(() -> new BizException("预警提醒不存在或已失效"));
        if (!Boolean.TRUE.equals(alert.get("remindable"))) {
            throw new BizException("当前预警不支持发送催办提醒");
        }
        String targetUserId = Optional.ofNullable(alert.get("targetUserId")).map(String::valueOf).orElse("");
        if (targetUserId.isBlank()) {
            throw new BizException("当前预警未配置提醒对象");
        }

        createMessage(
                targetUserId,
                "催办提醒",
                Optional.ofNullable(alert.get("reminderTitle")).map(String::valueOf).orElse(Optional.ofNullable(alert.get("title")).map(String::valueOf).orElse("流程催办提醒")),
                Optional.ofNullable(alert.get("reminderContent")).map(String::valueOf).orElse("请尽快处理相关任务。"),
                Optional.ofNullable(alert.get("reminderLink")).map(String::valueOf).orElse("/messages")
        );
        insertAudit("REMINDER", loginUser.id(), "发送催办提醒", Optional.ofNullable(alert.get("title")).map(String::valueOf).orElse(alertId));
    }

    public List<Map<String, Object>> students(LoginUser loginUser) {
        requireRole(loginUser, RoleType.COLLEGE_ADMIN);
        List<StudentEntity> students = studentMapper.selectList(
                Wrappers.<StudentEntity>lambdaQuery().eq(StudentEntity::getCollegeId, loginUser.collegeId())
        );
        return students.stream().map(student -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            UserAccountEntity user = requireUser(student.getUserId());
            MentorApplicationEntity mentor = mentorApplicationMapper.selectOne(
                    Wrappers.<MentorApplicationEntity>lambdaQuery()
                            .eq(MentorApplicationEntity::getStudentId, student.getId())
                            .orderByDesc(MentorApplicationEntity::getCreatedAt)
                            .last("limit 1")
            );
            InternshipApplicationEntity internship = internshipApplicationMapper.selectOne(
                    Wrappers.<InternshipApplicationEntity>lambdaQuery()
                            .eq(InternshipApplicationEntity::getStudentId, student.getId())
                            .orderByDesc(InternshipApplicationEntity::getCreatedAt)
                            .last("limit 1")
            );
            payload.put("id", student.getId());
            payload.put("name", student.getName());
            payload.put("studentNo", student.getStudentNo());
            payload.put("major", student.getMajor());
            payload.put("className", student.getClassName());
            payload.put("phone", student.getPhone());
            payload.put("internshipType", student.getInternshipType());
            payload.put("internshipStatus", student.getInternshipStatus());
            payload.put("account", user.getAccount());
            payload.put("accountStatus", user.getStatus());
            payload.put("mentorStatus", mentor == null ? "未申请" : mentor.getStatus());
            payload.put("internshipApplicationStatus", internship == null ? "未申请" : internship.getStatus());
            return payload;
        }).toList();
    }

    @Transactional
    public void createStudent(LoginUser loginUser, Requests.StudentCreateRequest request) {
        requireRole(loginUser, RoleType.COLLEGE_ADMIN);
        long exists = studentMapper.selectCount(Wrappers.<StudentEntity>lambdaQuery().eq(StudentEntity::getStudentNo, request.studentNo()));
        if (exists > 0) {
            throw new BizException("学号已存在");
        }

        String userId = IdGenerator.nextId("user");
        String studentId = IdGenerator.nextId("student");

        UserAccountEntity user = new UserAccountEntity();
        user.setId(userId);
        user.setAccount(request.studentNo());
        user.setName(request.name());
        user.setRole(RoleType.STUDENT.name());
        user.setPassword(PasswordUtils.sha256("123456"));
        user.setMustChangePassword(true);
        user.setStatus("ACTIVE");
        user.setCollegeId(loginUser.collegeId());
        userAccountMapper.insert(user);

        StudentEntity student = new StudentEntity();
        student.setId(studentId);
        student.setUserId(userId);
        student.setName(request.name());
        student.setStudentNo(request.studentNo());
        student.setCollegeId(loginUser.collegeId());
        student.setMajor(request.major());
        student.setClassName(request.className());
        student.setPhone(request.phone());
        student.setInternshipType(request.internshipType());
        student.setInternshipStatus("待申请");
        student.setProfileCompleted(true);
        studentMapper.insert(student);
        insertAudit("OPERATION", loginUser.id(), "新增学生", "新增学生 " + request.name());
    }

    @Transactional
    public void resetStudentPassword(LoginUser loginUser, String studentId) {
        requireRole(loginUser, RoleType.COLLEGE_ADMIN);
        StudentEntity student = requireStudent(studentId);
        UserAccountEntity user = requireUser(student.getUserId());
        user.setPassword(PasswordUtils.sha256("123456"));
        user.setMustChangePassword(true);
        userAccountMapper.updateById(user);
    }

    @Transactional
    public void changeStudentStatus(LoginUser loginUser, String studentId, String status) {
        requireRole(loginUser, RoleType.COLLEGE_ADMIN);
        StudentEntity student = requireStudent(studentId);
        UserAccountEntity user = requireUser(student.getUserId());
        user.setStatus(status);
        userAccountMapper.updateById(user);
    }

    public List<Map<String, Object>> teachers(LoginUser loginUser) {
        if (!Set.of(RoleType.COLLEGE_ADMIN.name(), RoleType.STUDENT.name()).contains(loginUser.role())) {
            throw new BizException("当前角色无权访问教师数据");
        }
        String collegeId = RoleType.STUDENT.name().equals(loginUser.role()) ? requireStudentByUser(loginUser.id()).getCollegeId() : loginUser.collegeId();
        return teacherMapper.selectList(Wrappers.<TeacherEntity>lambdaQuery().eq(TeacherEntity::getCollegeId, collegeId))
                .stream()
                .map(teacher -> {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    UserAccountEntity user = requireUser(teacher.getUserId());
                    long studentCount = mentorApplicationMapper.selectCount(
                            Wrappers.<MentorApplicationEntity>lambdaQuery()
                                    .eq(MentorApplicationEntity::getTeacherId, teacher.getId())
                                    .eq(MentorApplicationEntity::getStatus, MentorApplicationStatus.EFFECTIVE.getLabel())
                    );
                    payload.put("id", teacher.getId());
                    payload.put("name", teacher.getName());
                    payload.put("employeeNo", teacher.getEmployeeNo());
                    payload.put("department", teacher.getDepartment());
                    payload.put("phone", teacher.getPhone());
                    payload.put("account", user.getAccount());
                    payload.put("accountStatus", user.getStatus());
                    payload.put("studentCount", studentCount);
                    return payload;
                })
                .toList();
    }

    @Transactional
    public void createTeacher(LoginUser loginUser, Requests.TeacherCreateRequest request) {
        requireRole(loginUser, RoleType.COLLEGE_ADMIN);
        long exists = teacherMapper.selectCount(Wrappers.<TeacherEntity>lambdaQuery().eq(TeacherEntity::getEmployeeNo, request.employeeNo()));
        if (exists > 0) {
            throw new BizException("工号已存在");
        }

        String userId = IdGenerator.nextId("user");
        String teacherId = IdGenerator.nextId("teacher");

        UserAccountEntity user = new UserAccountEntity();
        user.setId(userId);
        user.setAccount(request.employeeNo());
        user.setName(request.name());
        user.setRole(RoleType.TEACHER.name());
        user.setPassword(PasswordUtils.sha256("123456"));
        user.setMustChangePassword(true);
        user.setStatus("ACTIVE");
        user.setCollegeId(loginUser.collegeId());
        userAccountMapper.insert(user);

        TeacherEntity teacher = new TeacherEntity();
        teacher.setId(teacherId);
        teacher.setUserId(userId);
        teacher.setName(request.name());
        teacher.setEmployeeNo(request.employeeNo());
        teacher.setCollegeId(loginUser.collegeId());
        teacher.setDepartment(request.department());
        teacher.setPhone(request.phone());
        teacher.setStatus("ACTIVE");
        teacherMapper.insert(teacher);
    }

    @Transactional
    public void resetTeacherPassword(LoginUser loginUser, String teacherId) {
        requireRole(loginUser, RoleType.COLLEGE_ADMIN);
        TeacherEntity teacher = requireTeacher(teacherId);
        UserAccountEntity user = requireUser(teacher.getUserId());
        user.setPassword(PasswordUtils.sha256("123456"));
        user.setMustChangePassword(true);
        userAccountMapper.updateById(user);
        insertAudit("OPERATION", loginUser.id(), "\u91cd\u7f6e\u6559\u5e08\u5bc6\u7801", teacher.getName() + " / " + teacher.getEmployeeNo());
    }

    @Transactional
    public void changeTeacherStatus(LoginUser loginUser, String teacherId, String status) {
        requireRole(loginUser, RoleType.COLLEGE_ADMIN);
        TeacherEntity teacher = requireTeacher(teacherId);
        UserAccountEntity user = requireUser(teacher.getUserId());
        user.setStatus(status);
        userAccountMapper.updateById(user);
        teacher.setStatus(status);
        teacherMapper.updateById(teacher);
        insertAudit("OPERATION", loginUser.id(), "\u66f4\u65b0\u6559\u5e08\u8d26\u53f7\u72b6\u6001", teacher.getName() + " -> " + status);
    }

    public List<OrganizationEntity> organizations(LoginUser loginUser) {
        if (!Set.of(RoleType.COLLEGE_ADMIN.name(), RoleType.STUDENT.name()).contains(loginUser.role())) {
            throw new BizException("当前角色无权访问实习单位");
        }
        String collegeId = RoleType.STUDENT.name().equals(loginUser.role()) ? requireStudentByUser(loginUser.id()).getCollegeId() : loginUser.collegeId();
        return organizationMapper.selectList(Wrappers.<OrganizationEntity>lambdaQuery().eq(OrganizationEntity::getCollegeId, collegeId));
    }

    @Transactional
    public void createOrganization(LoginUser loginUser, Requests.OrganizationCreateRequest request) {
        requireRole(loginUser, RoleType.COLLEGE_ADMIN);
        OrganizationEntity organization = new OrganizationEntity();
        organization.setId(IdGenerator.nextId("org"));
        organization.setCollegeId(loginUser.collegeId());
        organization.setName(request.name());
        organization.setAddress(request.address());
        organization.setContactName(request.contactName());
        organization.setContactPhone(request.contactPhone());
        organization.setNature(request.nature());
        organization.setCooperationStatus(request.cooperationStatus());
        organizationMapper.insert(organization);
    }

    @Transactional
    public void updateOrganization(LoginUser loginUser, String organizationId, Requests.OrganizationCreateRequest request) {
        requireRole(loginUser, RoleType.COLLEGE_ADMIN);
        OrganizationEntity organization = requireOrganization(organizationId);
        organization.setName(request.name());
        organization.setAddress(request.address());
        organization.setContactName(request.contactName());
        organization.setContactPhone(request.contactPhone());
        organization.setNature(request.nature());
        organization.setCooperationStatus(request.cooperationStatus());
        organizationMapper.updateById(organization);
    }

    public List<Map<String, Object>> mentorApplications(LoginUser loginUser) {
        List<MentorApplicationEntity> list;
        if (RoleType.STUDENT.name().equals(loginUser.role())) {
            list = mentorApplicationMapper.selectList(
                    Wrappers.<MentorApplicationEntity>lambdaQuery()
                            .eq(MentorApplicationEntity::getStudentId, requireStudentByUser(loginUser.id()).getId())
                            .orderByDesc(MentorApplicationEntity::getCreatedAt)
            );
        } else if (RoleType.TEACHER.name().equals(loginUser.role())) {
            list = mentorApplicationMapper.selectList(
                    Wrappers.<MentorApplicationEntity>lambdaQuery()
                            .eq(MentorApplicationEntity::getTeacherId, requireTeacherByUser(loginUser.id()).getId())
                            .orderByDesc(MentorApplicationEntity::getCreatedAt)
            );
        } else if (RoleType.COLLEGE_ADMIN.name().equals(loginUser.role())) {
            Set<String> studentIds = studentMapper.selectList(
                    Wrappers.<StudentEntity>lambdaQuery().eq(StudentEntity::getCollegeId, loginUser.collegeId())
            ).stream().map(StudentEntity::getId).collect(Collectors.toSet());
            list = studentIds.isEmpty() ? List.of() : mentorApplicationMapper.selectList(
                    Wrappers.<MentorApplicationEntity>lambdaQuery()
                            .in(MentorApplicationEntity::getStudentId, studentIds)
                            .orderByDesc(MentorApplicationEntity::getCreatedAt)
            );
        } else {
            throw new BizException("当前角色无权访问指导申请");
        }
        return list.stream().map(this::toMentorApplicationPayload).toList();
    }

    @Transactional
    public void createMentorApplication(LoginUser loginUser, Requests.MentorApplicationCreateRequest request) {
        requireRole(loginUser, RoleType.STUDENT);
        StudentEntity student = requireStudentByUser(loginUser.id());
        TeacherEntity teacher = requireTeacher(request.teacherId());

        MentorApplicationEntity entity = new MentorApplicationEntity();
        entity.setId(IdGenerator.nextId("mentor-app"));
        entity.setStudentId(student.getId());
        entity.setTeacherId(teacher.getId());
        entity.setStatus(MentorApplicationStatus.PENDING_TEACHER.getLabel());
        entity.setStudentRemark(Optional.ofNullable(request.studentRemark()).orElse(""));
        entity.setCreatedAt(LocalDateTime.now());
        mentorApplicationMapper.insert(entity);

        createMessage(requireUser(teacher.getUserId()).getId(), "待办提醒", student.getName() + " 发起了指导教师申请", "请在教师端确认是否接收该学生。", "/teacher/mentor-requests");
    }

    @Transactional
    public void teacherReviewMentor(LoginUser loginUser, String applicationId, Requests.DecisionRequest request) {
        requireRole(loginUser, RoleType.TEACHER);
        MentorApplicationEntity entity = requireMentorApplication(applicationId);
        TeacherEntity teacher = requireTeacherByUser(loginUser.id());
        if (!teacher.getId().equals(entity.getTeacherId())) {
            throw new BizException("无权处理该指导申请");
        }
        ensureMentorTeacherReviewable(entity);
        entity.setTeacherRemark(Optional.ofNullable(request.comment()).orElse(""));
        entity.setTeacherReviewedAt(LocalDateTime.now());
        entity.setStatus(Boolean.TRUE.equals(request.approved()) ? MentorApplicationStatus.PENDING_COLLEGE.getLabel() : MentorApplicationStatus.TEACHER_REJECTED.getLabel());
        mentorApplicationMapper.updateById(entity);

        StudentEntity student = requireStudent(entity.getStudentId());
        createMessage(requireUser(student.getUserId()).getId(), Boolean.TRUE.equals(request.approved()) ? "审核结果" : "退回通知", "指导教师申请" + (Boolean.TRUE.equals(request.approved()) ? "已确认" : "被驳回"), Optional.ofNullable(request.comment()).orElse("请查看处理结果。"), "/student/mentor-applications");
        if (Boolean.TRUE.equals(request.approved())) {
            UserAccountEntity collegeAdmin = userAccountMapper.selectOne(
                    Wrappers.<UserAccountEntity>lambdaQuery()
                            .eq(UserAccountEntity::getRole, RoleType.COLLEGE_ADMIN.name())
                            .eq(UserAccountEntity::getCollegeId, teacher.getCollegeId())
                            .last("limit 1")
            );
            if (collegeAdmin != null) {
                createMessage(collegeAdmin.getId(), "待办提醒", student.getName() + " 的指导关系待复核", "教师已确认，请学院管理员复核。", "/college/mentor-relations");
            }
        }
    }

    @Transactional
    public void collegeReviewMentor(LoginUser loginUser, String applicationId, Requests.DecisionRequest request) {
        requireRole(loginUser, RoleType.COLLEGE_ADMIN);
        MentorApplicationEntity entity = requireMentorApplication(applicationId);
        ensureMentorCollegeReviewable(loginUser, entity);
        entity.setCollegeRemark(Optional.ofNullable(request.comment()).orElse(""));
        entity.setCollegeReviewedAt(LocalDateTime.now());
        entity.setStatus(Boolean.TRUE.equals(request.approved()) ? MentorApplicationStatus.EFFECTIVE.getLabel() : MentorApplicationStatus.COLLEGE_REJECTED.getLabel());
        entity.setEffectiveAt(Boolean.TRUE.equals(request.approved()) ? LocalDateTime.now() : null);
        mentorApplicationMapper.updateById(entity);

        StudentEntity student = requireStudent(entity.getStudentId());
        createMessage(requireUser(student.getUserId()).getId(), Boolean.TRUE.equals(request.approved()) ? "审核结果" : "退回通知", "指导关系" + (Boolean.TRUE.equals(request.approved()) ? "已正式生效" : "学院复核未通过"), Optional.ofNullable(request.comment()).orElse("请查看学院复核结果。"), "/student/mentor-applications");
    }

    public List<Map<String, Object>> internshipApplications(LoginUser loginUser) {
        List<InternshipApplicationEntity> list;
        if (RoleType.STUDENT.name().equals(loginUser.role())) {
            list = internshipApplicationMapper.selectList(
                    Wrappers.<InternshipApplicationEntity>lambdaQuery()
                            .eq(InternshipApplicationEntity::getStudentId, requireStudentByUser(loginUser.id()).getId())
                            .orderByDesc(InternshipApplicationEntity::getCreatedAt)
            );
        } else if (RoleType.COLLEGE_ADMIN.name().equals(loginUser.role())) {
            Set<String> studentIds = studentMapper.selectList(
                    Wrappers.<StudentEntity>lambdaQuery().eq(StudentEntity::getCollegeId, loginUser.collegeId())
            ).stream().map(StudentEntity::getId).collect(Collectors.toSet());
            list = studentIds.isEmpty() ? List.of() : internshipApplicationMapper.selectList(
                    Wrappers.<InternshipApplicationEntity>lambdaQuery()
                            .in(InternshipApplicationEntity::getStudentId, studentIds)
                            .orderByDesc(InternshipApplicationEntity::getCreatedAt)
            );
        } else {
            throw new BizException("当前角色无权访问实习申请");
        }
        return list.stream().map(this::toInternshipPayload).toList();
    }

    @Transactional
    public void createInternshipApplication(LoginUser loginUser, Requests.InternshipApplicationCreateRequest request) {
        requireRole(loginUser, RoleType.STUDENT);
        StudentEntity student = requireStudentByUser(loginUser.id());
        long effectiveMentorCount = mentorApplicationMapper.selectCount(
                Wrappers.<MentorApplicationEntity>lambdaQuery()
                        .eq(MentorApplicationEntity::getStudentId, student.getId())
                        .eq(MentorApplicationEntity::getStatus, MentorApplicationStatus.EFFECTIVE.getLabel())
        );
        if (effectiveMentorCount == 0) {
            throw new BizException("指导关系尚未正式生效，暂不能提交实习申请");
        }

        InternshipApplicationEntity entity = new InternshipApplicationEntity();
        entity.setId(IdGenerator.nextId("internship-app"));
        entity.setStudentId(student.getId());
        entity.setOrganizationId(request.organizationId());
        entity.setStatus(InternshipApplicationStatus.PENDING_COLLEGE.getLabel());
        entity.setBatchName(request.batchName());
        entity.setPosition(request.position());
        entity.setGradeTarget(request.gradeTarget());
        entity.setStartDate(LocalDate.parse(request.startDate()));
        entity.setEndDate(LocalDate.parse(request.endDate()));
        entity.setRemark(Optional.ofNullable(request.remark()).orElse(""));
        entity.setAttachmentsJson(writeJson(Optional.ofNullable(request.attachments()).orElse(List.of())));
        entity.setOrganizationConfirmation("待登记");
        entity.setOrganizationFeedback("");
        entity.setCreatedAt(LocalDateTime.now());
        internshipApplicationMapper.insert(entity);

        UserAccountEntity collegeAdmin = userAccountMapper.selectOne(
                Wrappers.<UserAccountEntity>lambdaQuery()
                        .eq(UserAccountEntity::getRole, RoleType.COLLEGE_ADMIN.name())
                        .eq(UserAccountEntity::getCollegeId, student.getCollegeId())
                        .last("limit 1")
        );
        if (collegeAdmin != null) {
            createMessage(collegeAdmin.getId(), "待办提醒", student.getName() + " 提交了实习申请", "请核验指导关系并完成学院审批。", "/college/internship-applications");
        }
    }

    @Transactional
    public void reviewInternshipApplication(LoginUser loginUser, String applicationId, Requests.InternshipReviewRequest request) {
        requireRole(loginUser, RoleType.COLLEGE_ADMIN);
        InternshipApplicationEntity entity = requireInternshipApplication(applicationId);
        ensureInternshipReviewable(loginUser, entity);
        entity.setStatus(Boolean.TRUE.equals(request.approved()) ? InternshipApplicationStatus.APPROVED.getLabel() : InternshipApplicationStatus.REJECTED.getLabel());
        entity.setOrganizationConfirmation(Optional.ofNullable(request.organizationConfirmation()).orElse(entity.getOrganizationConfirmation()));
        entity.setOrganizationFeedback(Optional.ofNullable(request.organizationFeedback()).orElse(""));
        entity.setReceivedAt(request.receivedAt() == null || request.receivedAt().isBlank() ? null : LocalDate.parse(request.receivedAt()));
        entity.setReviewComment(Optional.ofNullable(request.comment()).orElse(""));
        entity.setReviewedAt(LocalDateTime.now());
        internshipApplicationMapper.updateById(entity);

        StudentEntity student = requireStudent(entity.getStudentId());
        if (Boolean.TRUE.equals(request.approved())) {
            student.setInternshipStatus("实习中");
            studentMapper.updateById(student);
        }
        createMessage(requireUser(student.getUserId()).getId(), Boolean.TRUE.equals(request.approved()) ? "审核结果" : "退回通知", "实习申请" + (Boolean.TRUE.equals(request.approved()) ? "已通过" : "被退回"), Optional.ofNullable(request.comment()).orElse("请查看学院审批意见。"), "/student/internship-application");
    }

    public List<Map<String, Object>> formTemplates(LoginUser loginUser) {
        List<FormTemplateEntity> templates = listAllTemplates();
        if (RoleType.STUDENT.name().equals(loginUser.role())) {
            String internshipType = requireStudentByUser(loginUser.id()).getInternshipType();
            return templates.stream()
                    .filter(this::isTemplateEnabled)
                    .filter(template -> readList(template.getApplicableTypesJson()).contains(internshipType))
                    .map(this::toFormTemplatePayload)
                    .toList();
        }
        return templates.stream().map(this::toFormTemplatePayload).toList();
    }

    public List<Map<String, Object>> forms(LoginUser loginUser, String category) {
        List<FormInstanceEntity> entities;
        if (RoleType.STUDENT.name().equals(loginUser.role())) {
            entities = formInstanceMapper.selectList(
                    Wrappers.<FormInstanceEntity>lambdaQuery()
                            .eq(FormInstanceEntity::getStudentId, requireStudentByUser(loginUser.id()).getId())
                            .orderByDesc(FormInstanceEntity::getUpdatedAt)
            );
        } else if (RoleType.TEACHER.name().equals(loginUser.role())) {
            TeacherEntity teacher = requireTeacherByUser(loginUser.id());
            Set<String> studentIds = mentorApplicationMapper.selectList(
                    Wrappers.<MentorApplicationEntity>lambdaQuery()
                            .eq(MentorApplicationEntity::getTeacherId, teacher.getId())
                            .eq(MentorApplicationEntity::getStatus, MentorApplicationStatus.EFFECTIVE.getLabel())
            ).stream().map(MentorApplicationEntity::getStudentId).collect(Collectors.toSet());
            entities = studentIds.isEmpty() ? List.of() : formInstanceMapper.selectList(
                    Wrappers.<FormInstanceEntity>lambdaQuery()
                            .in(FormInstanceEntity::getStudentId, studentIds)
                            .orderByDesc(FormInstanceEntity::getUpdatedAt)
            );
        } else if (RoleType.COLLEGE_ADMIN.name().equals(loginUser.role())) {
            Set<String> studentIds = studentMapper.selectList(
                    Wrappers.<StudentEntity>lambdaQuery().eq(StudentEntity::getCollegeId, loginUser.collegeId())
            ).stream().map(StudentEntity::getId).collect(Collectors.toSet());
            entities = studentIds.isEmpty() ? List.of() : formInstanceMapper.selectList(
                    Wrappers.<FormInstanceEntity>lambdaQuery()
                            .in(FormInstanceEntity::getStudentId, studentIds)
                            .orderByDesc(FormInstanceEntity::getUpdatedAt)
            );
        } else {
            throw new BizException("当前角色无权访问表单");
        }

        return entities.stream()
                .filter(item -> category == null || category.isBlank() || category.equals(item.getCategory()))
                .map(this::toFormPayload)
                .toList();
    }

    @Transactional
    public void createForm(LoginUser loginUser, Requests.FormSaveRequest request) {
        requireRole(loginUser, RoleType.STUDENT);
        StudentEntity student = requireStudentByUser(loginUser.id());
        FormTemplateEntity template = requireTemplate(request.templateCode());
        if (!isTemplateEnabled(template)) {
            throw new BizException("当前表单模板已停用");
        }

        FormInstanceEntity entity = new FormInstanceEntity();
        entity.setId(IdGenerator.nextId("form"));
        entity.setStudentId(student.getId());
        entity.setTemplateCode(template.getCode());
        entity.setTemplateName(template.getName());
        entity.setCategory(template.getCategory());
        entity.setStatus(Boolean.TRUE.equals(request.submit()) ? FormStatus.TEACHER_REVIEWING.getLabel() : FormStatus.DRAFT.getLabel());
        entity.setVersionNo(1);
        entity.setContentJson(writeJson(request.content()));
        entity.setAttachmentsJson(writeJson(Optional.ofNullable(request.attachments()).orElse(List.of())));
        entity.setTeacherComment("");
        entity.setCollegeComment("");
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setSubmittedAt(Boolean.TRUE.equals(request.submit()) ? LocalDateTime.now() : null);
        entity.setHistoryJson(writeJson(List.of()));
        formInstanceMapper.insert(entity);

        if (Boolean.TRUE.equals(request.submit())) {
            TeacherEntity teacher = currentEffectiveTeacher(student.getId());
            if (teacher != null) {
                createMessage(requireUser(teacher.getUserId()).getId(), "待办提醒", student.getName() + " 提交了 " + template.getName(), "请在教师端完成审核。", "/teacher/reviews");
            }
        }
    }

    @Transactional
    public void updateForm(LoginUser loginUser, String formId, Requests.FormSaveRequest request) {
        requireRole(loginUser, RoleType.STUDENT);
        StudentEntity student = requireStudentByUser(loginUser.id());
        FormInstanceEntity entity = formInstanceMapper.selectOne(
                Wrappers.<FormInstanceEntity>lambdaQuery()
                        .eq(FormInstanceEntity::getId, formId)
                        .eq(FormInstanceEntity::getStudentId, student.getId())
        );
        if (entity == null) {
            throw new BizException("表单不存在");
        }
        ensureStudentCanEditForm(entity);

        List<Map<String, Object>> history = readMapList(entity.getHistoryJson());
        history.add(Map.of(
                "version", entity.getVersionNo(),
                "status", entity.getStatus(),
                "content", readMap(entity.getContentJson()),
                "updatedAt", Optional.ofNullable(entity.getUpdatedAt()).map(LocalDateTime::toString).orElse("")
        ));

        entity.setVersionNo(entity.getVersionNo() + 1);
        if (!isTemplateEnabled(requireTemplate(entity.getTemplateCode()))) {
            throw new BizException("当前表单模板已停用");
        }
        entity.setContentJson(writeJson(request.content()));
        entity.setAttachmentsJson(writeJson(Optional.ofNullable(request.attachments()).orElse(List.of())));
        entity.setStatus(Boolean.TRUE.equals(request.submit()) ? FormStatus.TEACHER_REVIEWING.getLabel() : FormStatus.DRAFT.getLabel());
        entity.setTeacherComment("");
        entity.setCollegeComment("");
        entity.setScore(null);
        entity.setUpdatedAt(LocalDateTime.now());
        if (Boolean.TRUE.equals(request.submit())) {
            entity.setSubmittedAt(LocalDateTime.now());
        }
        entity.setHistoryJson(writeJson(history));
        formInstanceMapper.updateById(entity);

        if (Boolean.TRUE.equals(request.submit())) {
            TeacherEntity teacher = currentEffectiveTeacher(student.getId());
            if (teacher != null) {
                createMessage(requireUser(teacher.getUserId()).getId(), "待办提醒", student.getName() + " 重新提交了 " + entity.getTemplateName(), "请重新审核该材料。", "/teacher/reviews");
            }
        }
    }

    @Transactional
    public void teacherReviewForm(LoginUser loginUser, String formId, Requests.FormReviewRequest request) {
        requireRole(loginUser, RoleType.TEACHER);
        TeacherEntity teacher = requireTeacherByUser(loginUser.id());
        FormInstanceEntity entity = requireForm(formId);
        TeacherEntity effectiveTeacher = currentEffectiveTeacher(entity.getStudentId());
        if (effectiveTeacher == null || !effectiveTeacher.getId().equals(teacher.getId())) {
            throw new BizException("无权审核该表单");
        }
        ensureTeacherCanReviewForm(entity);

        entity.setTeacherComment(Optional.ofNullable(request.comment()).orElse(""));
        entity.setScore(request.score());
        entity.setTeacherReviewedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setStatus(Boolean.TRUE.equals(request.approved()) ? FormStatus.COLLEGE_REVIEWING.getLabel() : FormStatus.TEACHER_RETURNED.getLabel());
        formInstanceMapper.updateById(entity);

        StudentEntity student = requireStudent(entity.getStudentId());
        createMessage(requireUser(student.getUserId()).getId(), Boolean.TRUE.equals(request.approved()) ? "审核结果" : "退回通知", entity.getTemplateName() + (Boolean.TRUE.equals(request.approved()) ? "已通过教师审核" : "被教师退回"), Optional.ofNullable(request.comment()).orElse("请查看教师审核结果。"), "/student/tasks");
        if (Boolean.TRUE.equals(request.approved())) {
            UserAccountEntity collegeAdmin = userAccountMapper.selectOne(
                    Wrappers.<UserAccountEntity>lambdaQuery()
                            .eq(UserAccountEntity::getRole, RoleType.COLLEGE_ADMIN.name())
                            .eq(UserAccountEntity::getCollegeId, student.getCollegeId())
                            .last("limit 1")
            );
            if (collegeAdmin != null) {
                createMessage(collegeAdmin.getId(), "待办提醒", student.getName() + " 的 " + entity.getTemplateName() + " 待学院归档", "教师已审核通过，请进行学院终审。", "/college/archive");
            }
        }
    }

    @Transactional
    public void collegeReviewForm(LoginUser loginUser, String formId, Requests.FormReviewRequest request) {
        requireRole(loginUser, RoleType.COLLEGE_ADMIN);
        FormInstanceEntity entity = requireForm(formId);
        ensureCollegeAdminFormAccess(loginUser, entity);
        ensureCollegeCanReviewForm(entity);
        applyCollegeReview(entity, request.approved(), request.score(), request.comment());
        formInstanceMapper.updateById(entity);
        notifyStudentAfterCollegeReview(entity, request.approved(), request.comment());
    }

    @Transactional
    public Map<String, Object> batchCollegeReviewForms(LoginUser loginUser, Requests.BatchFormReviewRequest request) {
        requireRole(loginUser, RoleType.COLLEGE_ADMIN);
        if (request.formIds() == null || request.formIds().isEmpty()) {
            throw new BizException("请至少选择一条待处理表单");
        }
        validateReviewScore(request.score());

        LinkedHashSet<String> formIds = request.formIds().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (formIds.isEmpty()) {
            throw new BizException("请至少选择一条待处理表单");
        }

        Map<String, FormInstanceEntity> formMap = formInstanceMapper.selectBatchIds(formIds).stream()
                .collect(Collectors.toMap(FormInstanceEntity::getId, item -> item));

        int processedCount = 0;
        int archivedCount = 0;
        int returnedCount = 0;
        List<Map<String, Object>> skipped = new ArrayList<>();

        for (String formId : formIds) {
            FormInstanceEntity entity = formMap.get(formId);
            if (entity == null) {
                skipped.add(Map.of("id", formId, "reason", "表单不存在"));
                continue;
            }
            try {
                ensureCollegeAdminFormAccess(loginUser, entity);
                if (!FormStatus.COLLEGE_REVIEWING.getLabel().equals(entity.getStatus())) {
                    skipped.add(Map.of("id", formId, "reason", "当前状态不可批量归档"));
                    continue;
                }

                applyCollegeReview(entity, request.approved(), request.score(), request.comment());
                formInstanceMapper.updateById(entity);
                notifyStudentAfterCollegeReview(entity, request.approved(), request.comment());
                processedCount++;
                if (Boolean.TRUE.equals(request.approved())) {
                    archivedCount++;
                } else {
                    returnedCount++;
                }
            } catch (BizException exception) {
                skipped.add(Map.of("id", formId, "reason", exception.getMessage()));
            }
        }

        insertAudit(
                "FORM",
                loginUser.id(),
                Boolean.TRUE.equals(request.approved()) ? "批量归档表单" : "批量退回表单",
                "处理 " + processedCount + " 条，跳过 " + skipped.size() + " 条"
        );

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("processedCount", processedCount);
        payload.put("archivedCount", archivedCount);
        payload.put("returnedCount", returnedCount);
        payload.put("skipped", skipped);
        return payload;
    }

    public List<Map<String, Object>> guidanceRecords(LoginUser loginUser) {
        requireRole(loginUser, RoleType.TEACHER);
        TeacherEntity teacher = requireTeacherByUser(loginUser.id());
        return guidanceRecordMapper.selectList(
                        Wrappers.<GuidanceRecordEntity>lambdaQuery()
                                .eq(GuidanceRecordEntity::getTeacherId, teacher.getId())
                                .orderByDesc(GuidanceRecordEntity::getGuidanceAt)
                ).stream()
                .map(item -> {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("id", item.getId());
                    payload.put("studentId", item.getStudentId());
                    payload.put("guidanceAt", item.getGuidanceAt());
                    payload.put("mode", item.getMode());
                    payload.put("problem", item.getProblem());
                    payload.put("advice", item.getAdvice());
                    payload.put("followUp", item.getFollowUp());
                    payload.put("student", toStudentSimple(requireStudent(item.getStudentId())));
                    return payload;
                })
                .toList();
    }

    @Transactional
    public void createGuidanceRecord(LoginUser loginUser, Requests.GuidanceRecordCreateRequest request) {
        requireRole(loginUser, RoleType.TEACHER);
        TeacherEntity teacher = requireTeacherByUser(loginUser.id());
        GuidanceRecordEntity entity = new GuidanceRecordEntity();
        entity.setId(IdGenerator.nextId("guide"));
        entity.setTeacherId(teacher.getId());
        entity.setStudentId(request.studentId());
        entity.setGuidanceAt(parseDateTime(request.guidanceAt()));
        entity.setMode(request.mode());
        entity.setProblem(request.problem());
        entity.setAdvice(request.advice());
        entity.setFollowUp(request.followUp());
        guidanceRecordMapper.insert(entity);
    }

    public List<Map<String, Object>> evaluations(LoginUser loginUser) {
        List<EvaluationRecordEntity> list;
        if (RoleType.STUDENT.name().equals(loginUser.role())) {
            list = evaluationRecordMapper.selectList(Wrappers.<EvaluationRecordEntity>lambdaQuery().eq(EvaluationRecordEntity::getStudentId, requireStudentByUser(loginUser.id()).getId()));
        } else if (RoleType.TEACHER.name().equals(loginUser.role())) {
            list = evaluationRecordMapper.selectList(Wrappers.<EvaluationRecordEntity>lambdaQuery().eq(EvaluationRecordEntity::getTeacherId, requireTeacherByUser(loginUser.id()).getId()));
        } else if (RoleType.COLLEGE_ADMIN.name().equals(loginUser.role())) {
            Set<String> studentIds = studentMapper.selectList(
                    Wrappers.<StudentEntity>lambdaQuery().eq(StudentEntity::getCollegeId, loginUser.collegeId())
            ).stream().map(StudentEntity::getId).collect(Collectors.toSet());
            list = studentIds.isEmpty() ? List.of() : evaluationRecordMapper.selectList(
                    Wrappers.<EvaluationRecordEntity>lambdaQuery().in(EvaluationRecordEntity::getStudentId, studentIds)
            );
        } else {
            throw new BizException("当前角色无权访问评价");
        }

        return list.stream().map(item -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("id", item.getId());
            payload.put("teacherId", item.getTeacherId());
            payload.put("studentId", item.getStudentId());
            payload.put("stageComment", item.getStageComment());
            payload.put("summaryComment", item.getSummaryComment());
            payload.put("recommendedScore", item.getFinalScore());
            payload.put("finalScore", item.getCollegeScore() == null ? item.getFinalScore() : item.getCollegeScore());
            payload.put("collegeScore", item.getCollegeScore());
            payload.put("dimensionScores", readMapList(item.getDimensionScoresJson()));
            payload.put("strengthsComment", item.getStrengthsComment());
            payload.put("improvementComment", item.getImprovementComment());
            payload.put("collegeComment", item.getCollegeComment());
            payload.put("submittedToCollege", item.getSubmittedToCollege());
            payload.put("confirmedByCollege", item.getConfirmedByCollege());
            payload.put("evaluatedAt", item.getEvaluatedAt());
            payload.put("collegeConfirmedAt", item.getCollegeConfirmedAt());
            payload.put("teacher", toTeacherSimple(requireTeacher(item.getTeacherId())));
            payload.put("student", toStudentSimple(requireStudent(item.getStudentId())));
            return payload;
        }).toList();
    }

    @Transactional
    public void saveEvaluation(LoginUser loginUser, Requests.EvaluationSaveRequest request) {
        requireRole(loginUser, RoleType.TEACHER);
        TeacherEntity teacher = requireTeacherByUser(loginUser.id());
        EvaluationRecordEntity entity = evaluationRecordMapper.selectOne(
                Wrappers.<EvaluationRecordEntity>lambdaQuery()
                        .eq(EvaluationRecordEntity::getTeacherId, teacher.getId())
                        .eq(EvaluationRecordEntity::getStudentId, request.studentId())
        );
        if (entity == null) {
            entity = new EvaluationRecordEntity();
            entity.setId(IdGenerator.nextId("eval"));
            entity.setTeacherId(teacher.getId());
            entity.setStudentId(request.studentId());
            applyTeacherEvaluation(entity, request);
            evaluationRecordMapper.insert(entity);
            notifyCollegeForEvaluation(entity);
            return;
        }
        if (Boolean.TRUE.equals(entity.getConfirmedByCollege())) {
            throw new BizException("学院已确认该评价，当前不可修改");
        }
        applyTeacherEvaluation(entity, request);
        evaluationRecordMapper.updateById(entity);
        notifyCollegeForEvaluation(entity);
    }

    @Transactional
    public void collegeConfirmEvaluation(LoginUser loginUser, String evaluationId, Requests.EvaluationCollegeConfirmRequest request) {
        requireRole(loginUser, RoleType.COLLEGE_ADMIN);
        EvaluationRecordEntity entity = evaluationRecordMapper.selectById(evaluationId);
        if (entity == null) {
            throw new BizException("评价记录不存在");
        }
        StudentEntity student = requireStudent(entity.getStudentId());
        if (!Objects.equals(student.getCollegeId(), loginUser.collegeId())) {
            throw new BizException("无权确认该评价");
        }
        ensureEvaluationConfirmable(entity);
        if (request.collegeScore() < 0 || request.collegeScore() > 100) {
            throw new BizException("学院最终成绩需在 0 到 100 之间");
        }
        entity.setCollegeScore(request.collegeScore());
        entity.setCollegeComment(Optional.ofNullable(request.collegeComment()).orElse(""));
        entity.setConfirmedByCollege(true);
        entity.setCollegeConfirmedAt(LocalDateTime.now());
        evaluationRecordMapper.updateById(entity);

        createMessage(
                requireUser(student.getUserId()).getId(),
                "评价结果",
                "学院已确认实习评价",
                Optional.ofNullable(request.collegeComment()).filter(comment -> !comment.isBlank()).orElse("请查看最终成绩与评价维度结果。"),
                "/student/results"
        );
        insertAudit("EVALUATION", loginUser.id(), "学院确认评价", student.getName() + " 的评价已确认");
    }

    public Map<String, Object> reportSummary(LoginUser loginUser) {
        requireRole(loginUser, RoleType.COLLEGE_ADMIN, RoleType.SUPER_ADMIN);
        List<StudentEntity> students = scopedStudents(loginUser);
        List<TeacherEntity> teachers = scopedTeachers(loginUser);
        Set<String> studentIds = students.stream().map(StudentEntity::getId).collect(Collectors.toSet());
        List<FormInstanceEntity> forms = listFormsByStudentIds(studentIds);
        return buildSummaryPayload(students, teachers, studentIds, forms);
    }

    public Map<String, Object> reportCenter(LoginUser loginUser) {
        requireRole(loginUser, RoleType.COLLEGE_ADMIN, RoleType.SUPER_ADMIN);
        List<StudentEntity> students = scopedStudents(loginUser);
        List<TeacherEntity> teachers = scopedTeachers(loginUser);
        List<OrganizationEntity> organizations = scopedOrganizations(loginUser);
        Set<String> studentIds = students.stream().map(StudentEntity::getId).collect(Collectors.toSet());
        List<FormInstanceEntity> forms = listFormsByStudentIds(studentIds);
        List<InternshipApplicationEntity> internships = listInternshipsByStudentIds(studentIds);
        List<EvaluationRecordEntity> evaluations = listEvaluationsByStudentIds(studentIds);
        List<MentorApplicationEntity> effectiveGuidance = listEffectiveGuidanceByStudentIds(studentIds);

        long archivedCount = forms.stream().filter(item -> FormStatus.ARCHIVED.getLabel().equals(item.getStatus())).count();
        long pendingArchiveCount = forms.stream().filter(item -> FormStatus.COLLEGE_REVIEWING.getLabel().equals(item.getStatus())).count();
        long confirmedEvaluationCount = evaluations.stream().filter(item -> Boolean.TRUE.equals(item.getConfirmedByCollege())).count();
        double averageScore = forms.stream()
                .map(FormInstanceEntity::getScore)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0);

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("studentCount", students.size());
        overview.put("teacherCount", teachers.size());
        overview.put("organizationCount", organizations.size());
        overview.put("internshipCount", internships.size());
        overview.put("formCount", forms.size());
        overview.put("archivedCount", archivedCount);
        overview.put("pendingArchiveCount", pendingArchiveCount);
        overview.put("archiveRate", forms.isEmpty() ? 0 : Math.round((archivedCount * 100.0) / forms.size()));
        overview.put("confirmedEvaluationCount", confirmedEvaluationCount);
        overview.put("averageScore", Math.round(averageScore * 10.0) / 10.0);

        Map<String, Object> studentsPayload = new LinkedHashMap<>();
        studentsPayload.put("statusDistribution", toCountList(students.stream().collect(Collectors.groupingBy(item -> Optional.ofNullable(item.getInternshipStatus()).orElse("未开始"), LinkedHashMap::new, Collectors.counting()))));
        studentsPayload.put("typeDistribution", toCountList(students.stream().collect(Collectors.groupingBy(item -> Optional.ofNullable(item.getInternshipType()).orElse("未分类"), LinkedHashMap::new, Collectors.counting()))));

        Map<String, Object> formsPayload = new LinkedHashMap<>();
        formsPayload.put("statusDistribution", toCountList(forms.stream().collect(Collectors.groupingBy(item -> Optional.ofNullable(item.getStatus()).orElse("未知"), LinkedHashMap::new, Collectors.counting()))));
        formsPayload.put("templateRanking", buildTemplateRanking(forms));

        Map<String, Object> teachersPayload = new LinkedHashMap<>();
        teachersPayload.put("workload", buildTeacherWorkload(teachers, effectiveGuidance, forms, evaluations));

        Map<String, Object> evaluationsPayload = new LinkedHashMap<>();
        evaluationsPayload.put("summary", buildEvaluationSummary(evaluations));
        evaluationsPayload.put("scoreDistribution", buildScoreDistribution(evaluations));

        Map<String, Object> organizationsPayload = new LinkedHashMap<>();
        organizationsPayload.put("cooperationDistribution", toCountList(organizations.stream().collect(Collectors.groupingBy(item -> Optional.ofNullable(item.getCooperationStatus()).orElse("未设置"), LinkedHashMap::new, Collectors.counting()))));
        organizationsPayload.put("usageRanking", buildOrganizationUsageRanking(organizations, internships));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("overview", overview);
        payload.put("summary", buildSummaryPayload(students, teachers, studentIds, forms));
        payload.put("students", studentsPayload);
        payload.put("forms", formsPayload);
        payload.put("teachers", teachersPayload);
        payload.put("evaluations", evaluationsPayload);
        payload.put("organizations", organizationsPayload);
        payload.put("trends", buildMonthlyTrend(forms, evaluations));
        return payload;
    }

    private Map<String, Object> buildSummaryPayload(List<StudentEntity> students,
                                                    List<TeacherEntity> teachers,
                                                    Set<String> studentIds,
                                                    List<FormInstanceEntity> forms) {
        Map<String, Object> studentsPayload = new LinkedHashMap<>();
        studentsPayload.put("total", students.size());
        studentsPayload.put("applied", studentIds.isEmpty() ? 0 : internshipApplicationMapper.selectCount(
                Wrappers.<InternshipApplicationEntity>lambdaQuery().in(InternshipApplicationEntity::getStudentId, studentIds)
        ));
        studentsPayload.put("active", students.stream().filter(item -> "实习中".equals(item.getInternshipStatus())).count());

        Map<String, Object> teacherPayload = new LinkedHashMap<>();
        teacherPayload.put("total", teachers.size());
        teacherPayload.put("activeGuidanceCount", studentIds.isEmpty() ? 0 : mentorApplicationMapper.selectCount(
                Wrappers.<MentorApplicationEntity>lambdaQuery()
                        .in(MentorApplicationEntity::getStudentId, studentIds)
                        .eq(MentorApplicationEntity::getStatus, MentorApplicationStatus.EFFECTIVE.getLabel())
        ));

        long archivedCount = forms.stream().filter(item -> FormStatus.ARCHIVED.getLabel().equals(item.getStatus())).count();
        long rejectedCount = forms.stream().filter(item -> Set.of(FormStatus.TEACHER_RETURNED.getLabel(), FormStatus.COLLEGE_RETURNED.getLabel()).contains(item.getStatus())).count();
        Map<String, Object> formPayload = new LinkedHashMap<>();
        formPayload.put("total", forms.size());
        formPayload.put("archived", archivedCount);
        formPayload.put("archiveRate", forms.isEmpty() ? 0 : Math.round((archivedCount * 100.0) / forms.size()));
        formPayload.put("rejected", rejectedCount);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("students", studentsPayload);
        payload.put("teachers", teacherPayload);
        payload.put("forms", formPayload);
        return payload;
    }

    private List<StudentEntity> scopedStudents(LoginUser loginUser) {
        return RoleType.COLLEGE_ADMIN.name().equals(loginUser.role())
                ? studentMapper.selectList(Wrappers.<StudentEntity>lambdaQuery().eq(StudentEntity::getCollegeId, loginUser.collegeId()))
                : studentMapper.selectList(Wrappers.<StudentEntity>lambdaQuery());
    }

    private List<TeacherEntity> scopedTeachers(LoginUser loginUser) {
        return RoleType.COLLEGE_ADMIN.name().equals(loginUser.role())
                ? teacherMapper.selectList(Wrappers.<TeacherEntity>lambdaQuery().eq(TeacherEntity::getCollegeId, loginUser.collegeId()))
                : teacherMapper.selectList(Wrappers.<TeacherEntity>lambdaQuery());
    }

    private List<OrganizationEntity> scopedOrganizations(LoginUser loginUser) {
        return RoleType.COLLEGE_ADMIN.name().equals(loginUser.role())
                ? organizationMapper.selectList(Wrappers.<OrganizationEntity>lambdaQuery().eq(OrganizationEntity::getCollegeId, loginUser.collegeId()))
                : organizationMapper.selectList(Wrappers.<OrganizationEntity>lambdaQuery());
    }

    private List<FormInstanceEntity> listFormsByStudentIds(Set<String> studentIds) {
        return studentIds.isEmpty() ? List.of() : formInstanceMapper.selectList(
                Wrappers.<FormInstanceEntity>lambdaQuery()
                        .in(FormInstanceEntity::getStudentId, studentIds)
                        .orderByDesc(FormInstanceEntity::getUpdatedAt)
        );
    }

    private List<InternshipApplicationEntity> listInternshipsByStudentIds(Set<String> studentIds) {
        return studentIds.isEmpty() ? List.of() : internshipApplicationMapper.selectList(
                Wrappers.<InternshipApplicationEntity>lambdaQuery()
                        .in(InternshipApplicationEntity::getStudentId, studentIds)
                        .orderByDesc(InternshipApplicationEntity::getCreatedAt)
        );
    }

    private List<MentorApplicationEntity> listMentorApplicationsByStudentIds(Set<String> studentIds) {
        return studentIds.isEmpty() ? List.of() : mentorApplicationMapper.selectList(
                Wrappers.<MentorApplicationEntity>lambdaQuery()
                        .in(MentorApplicationEntity::getStudentId, studentIds)
                        .orderByDesc(MentorApplicationEntity::getCreatedAt)
        );
    }

    private List<EvaluationRecordEntity> listEvaluationsByStudentIds(Set<String> studentIds) {
        return studentIds.isEmpty() ? List.of() : evaluationRecordMapper.selectList(
                Wrappers.<EvaluationRecordEntity>lambdaQuery()
                        .in(EvaluationRecordEntity::getStudentId, studentIds)
                        .orderByDesc(EvaluationRecordEntity::getEvaluatedAt)
        );
    }

    private List<MentorApplicationEntity> listEffectiveGuidanceByStudentIds(Set<String> studentIds) {
        return studentIds.isEmpty() ? List.of() : mentorApplicationMapper.selectList(
                Wrappers.<MentorApplicationEntity>lambdaQuery()
                        .in(MentorApplicationEntity::getStudentId, studentIds)
                        .eq(MentorApplicationEntity::getStatus, MentorApplicationStatus.EFFECTIVE.getLabel())
        );
    }

    private UserAccountEntity firstCollegeAdminUser(String collegeId) {
        if (collegeId == null || collegeId.isBlank()) {
            return null;
        }
        return userAccountMapper.selectOne(
                Wrappers.<UserAccountEntity>lambdaQuery()
                        .eq(UserAccountEntity::getRole, RoleType.COLLEGE_ADMIN.name())
                        .eq(UserAccountEntity::getCollegeId, collegeId)
                        .last("limit 1")
        );
    }

    private long overdueDays(LocalDateTime baseline, long thresholdDays) {
        if (baseline == null) {
            return -1;
        }
        long days = ChronoUnit.DAYS.between(baseline, LocalDateTime.now());
        return days >= thresholdDays ? days - thresholdDays : -1;
    }

    private int teacherReviewTimeoutDays() {
        return getSettingInt("teacher_review_timeout_days", 2);
    }

    private int collegeReviewTimeoutDays() {
        return getSettingInt("college_review_timeout_days", 2);
    }

    private int studentResubmitTimeoutDays() {
        return getSettingInt("student_resubmit_timeout_days", 2);
    }

    private int evaluationConfirmTimeoutDays() {
        return getSettingInt("evaluation_confirm_timeout_days", 3);
    }

    private int getSettingInt(String key, int defaultValue) {
        String value = getSettingString(key, String.valueOf(defaultValue));
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    private String getSettingString(String key, String defaultValue) {
        SystemSettingEntity entity = systemSettingMapper.selectById(key);
        if (entity == null || entity.getSettingValue() == null || entity.getSettingValue().isBlank()) {
            return defaultValue;
        }
        return entity.getSettingValue().trim();
    }

    private boolean getSettingBoolean(String key, boolean defaultValue) {
        String value = getSettingString(key, defaultValue ? "1" : "0").toLowerCase(Locale.ROOT);
        if (Set.of("1", "true", "yes").contains(value)) {
            return true;
        }
        if (Set.of("0", "false", "no").contains(value)) {
            return false;
        }
        return defaultValue;
    }

    private String getSettingLevel(String key, String defaultValue) {
        String value = getSettingString(key, defaultValue);
        SystemSettingSpec spec = SYSTEM_SETTING_SPEC_MAP.get(key);
        Set<String> allowed = (spec == null ? ALERT_LEVEL_OPTIONS : spec.options()).stream()
                .map(SettingOption::value)
                .collect(Collectors.toSet());
        return allowed.contains(value) ? value : defaultValue;
    }

    private String resolveAlertProfile(String alertId) {
        if (alertId == null || alertId.isBlank()) {
            return "teacher_review";
        }
        if (alertId.contains("evaluation") || alertId.contains("eval")) {
            return "evaluation_confirm";
        }
        if (alertId.contains("returned") || alertId.contains("remind-student")) {
            return "student_resubmit";
        }
        if (alertId.contains("form-college") || alertId.contains("form-pending") || alertId.contains("mentor-pending") || alertId.contains("internship-pending")) {
            return "college_review";
        }
        return "teacher_review";
    }

    private String configuredAlertLevel(String profile, String defaultValue) {
        return switch (profile) {
            case "college_review" -> getSettingLevel("college_review_alert_level", defaultValue);
            case "student_resubmit" -> getSettingLevel("student_resubmit_alert_level", defaultValue);
            case "evaluation_confirm" -> getSettingLevel("evaluation_confirm_alert_level", defaultValue);
            default -> getSettingLevel("teacher_review_alert_level", defaultValue);
        };
    }

    private boolean reminderEnabled(String profile, boolean defaultValue) {
        return switch (profile) {
            case "college_review" -> getSettingBoolean("college_review_remind_enabled", defaultValue);
            case "student_resubmit" -> getSettingBoolean("student_resubmit_remind_enabled", defaultValue);
            case "evaluation_confirm" -> getSettingBoolean("evaluation_confirm_remind_enabled", defaultValue);
            default -> getSettingBoolean("teacher_review_remind_enabled", defaultValue);
        };
    }

    private String reminderTitleTemplate(String profile, String defaultValue) {
        return switch (profile) {
            case "college_review" -> getSettingString("college_review_reminder_title_template", defaultValue);
            case "student_resubmit" -> getSettingString("student_resubmit_reminder_title_template", defaultValue);
            case "evaluation_confirm" -> getSettingString("evaluation_confirm_reminder_title_template", defaultValue);
            default -> getSettingString("teacher_review_reminder_title_template", defaultValue);
        };
    }

    private String reminderContentTemplate(String profile, String defaultValue) {
        return switch (profile) {
            case "college_review" -> getSettingString("college_review_reminder_content_template", defaultValue);
            case "student_resubmit" -> getSettingString("student_resubmit_reminder_content_template", defaultValue);
            case "evaluation_confirm" -> getSettingString("evaluation_confirm_reminder_content_template", defaultValue);
            default -> getSettingString("teacher_review_reminder_content_template", defaultValue);
        };
    }

    private Map<String, Object> buildRiskAlert(String id,
                                               String level,
                                               String category,
                                               String title,
                                               String content,
                                               long overdueDays,
                                               boolean remindable,
                                               String remindActionLabel,
                                               String targetUserId,
                                               String targetName,
                                               String link,
                                               String reminderTitle,
                                               String reminderContent,
                                               String reminderLink) {
        String profile = resolveAlertProfile(id);
        long displayOverdueDays = overdueDays + 1;
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("title", title);
        context.put("category", category);
        context.put("content", content);
        context.put("targetName", Optional.ofNullable(targetName).orElse(""));
        context.put("overdueDays", displayOverdueDays);
        context.put("remindActionLabel", Optional.ofNullable(remindActionLabel).orElse(""));

        boolean finalRemindable = remindable && reminderEnabled(profile, true);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", id);
        payload.put("level", configuredAlertLevel(profile, level));
        payload.put("category", category);
        payload.put("title", title);
        payload.put("content", content);
        payload.put("overdueDays", displayOverdueDays);
        payload.put("remindable", finalRemindable);
        payload.put("remindActionLabel", finalRemindable ? remindActionLabel : "");
        payload.put("targetUserId", finalRemindable ? targetUserId : null);
        payload.put("targetName", finalRemindable ? targetName : "");
        payload.put("link", link);
        payload.put("reminderTitle", renderTemplate(reminderTitleTemplate(profile, reminderTitle), context));
        payload.put("reminderContent", renderTemplate(reminderContentTemplate(profile, reminderContent), context));
        payload.put("reminderLink", reminderLink);
        return payload;
    }

    private String renderTemplate(String template, Map<String, Object> context) {
        String result = Optional.ofNullable(template).orElse("");
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", Optional.ofNullable(entry.getValue()).map(String::valueOf).orElse(""));
        }
        return result;
    }

    private List<Map<String, Object>> sortAlerts(List<Map<String, Object>> alerts) {
        return alerts.stream()
                .sorted((left, right) -> {
                    long rightDays = Optional.ofNullable(right.get("overdueDays")).map(Number.class::cast).map(Number::longValue).orElse(0L);
                    long leftDays = Optional.ofNullable(left.get("overdueDays")).map(Number.class::cast).map(Number::longValue).orElse(0L);
                    return Long.compare(rightDays, leftDays);
                })
                .toList();
    }

    private List<Map<String, Object>> toCountList(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("label", entry.getKey());
                    item.put("count", entry.getValue());
                    return item;
                })
                .sorted((left, right) -> Long.compare(((Number) right.get("count")).longValue(), ((Number) left.get("count")).longValue()))
                .toList();
    }

    private List<SystemSettingSpec> listSystemSettings() {
        return SYSTEM_SETTING_SPECS.stream()
                .sorted(Comparator.comparingInt(SystemSettingSpec::sortNo))
                .toList();
    }

    private Map<String, Object> toSystemSettingPayload(SystemSettingSpec spec, SystemSettingEntity entity) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("key", spec.key());
        payload.put("value", entity == null || entity.getSettingValue() == null || entity.getSettingValue().isBlank() ? spec.defaultValue() : entity.getSettingValue());
        payload.put("category", spec.category());
        payload.put("name", spec.name());
        payload.put("description", spec.description());
        payload.put("valueType", spec.valueType());
        payload.put("sortNo", spec.sortNo());
        payload.put("options", toSettingOptionPayload(spec.options()));
        payload.put("updatedAt", entity == null ? null : entity.getUpdatedAt());
        return payload;
    }

    private List<Map<String, Object>> toSettingOptionPayload(List<SettingOption> options) {
        return options.stream().map(option -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("label", option.label());
            payload.put("value", option.value());
            return payload;
        }).toList();
    }

    private String normalizeSettingValue(SystemSettingSpec spec, String rawValue) {
        String value = Optional.ofNullable(rawValue).map(String::trim).orElse("");
        return switch (spec.valueType()) {
            case "BOOLEAN" -> Set.of("1", "true", "yes").contains(value.toLowerCase(Locale.ROOT)) ? "1" : "0";
            case "INTEGER" -> value;
            case "SELECT" -> value;
            default -> value;
        };
    }

    private boolean isValidSettingValue(SystemSettingSpec spec, String value) {
        if (spec == null) {
            return false;
        }
        String trimmed = Optional.ofNullable(value).map(String::trim).orElse("");
        return switch (spec.valueType()) {
            case "INTEGER" -> isNonNegativeInteger(trimmed);
            case "BOOLEAN" -> Set.of("1", "0", "true", "false", "yes", "no").contains(trimmed.toLowerCase(Locale.ROOT));
            case "SELECT" -> spec.options().stream().map(SettingOption::value).anyMatch(option -> option.equals(trimmed));
            case "TEXT" -> !trimmed.isBlank() && trimmed.length() <= 200;
            default -> false;
        };
    }

    private boolean isNonNegativeInteger(String value) {
        return value != null && value.trim().matches("^\\d+$");
    }
    private List<Map<String, Object>> buildTemplateRanking(List<FormInstanceEntity> forms) {
        return forms.stream()
                .collect(Collectors.groupingBy(FormInstanceEntity::getTemplateCode, LinkedHashMap::new, Collectors.toList()))
                .values().stream()
                .map(items -> {
                    FormInstanceEntity sample = items.get(0);
                    long archived = items.stream().filter(item -> FormStatus.ARCHIVED.getLabel().equals(item.getStatus())).count();
                    long pending = items.stream().filter(item -> FormStatus.COLLEGE_REVIEWING.getLabel().equals(item.getStatus())).count();
                    double avgScore = items.stream().map(FormInstanceEntity::getScore).filter(Objects::nonNull).mapToInt(Integer::intValue).average().orElse(0);
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("templateCode", sample.getTemplateCode());
                    item.put("templateName", sample.getTemplateName());
                    item.put("category", sample.getCategory());
                    item.put("total", items.size());
                    item.put("archived", archived);
                    item.put("pending", pending);
                    item.put("averageScore", Math.round(avgScore * 10.0) / 10.0);
                    return item;
                })
                .sorted((left, right) -> Integer.compare(((Number) right.get("total")).intValue(), ((Number) left.get("total")).intValue()))
                .toList();
    }

    private List<Map<String, Object>> buildTeacherWorkload(List<TeacherEntity> teachers,
                                                           List<MentorApplicationEntity> effectiveGuidance,
                                                           List<FormInstanceEntity> forms,
                                                           List<EvaluationRecordEntity> evaluations) {
        Map<String, Set<String>> studentIdsByTeacher = new HashMap<>();
        for (MentorApplicationEntity item : effectiveGuidance) {
            studentIdsByTeacher.computeIfAbsent(item.getTeacherId(), key -> new LinkedHashSet<>()).add(item.getStudentId());
        }
        return teachers.stream().map(teacher -> {
                    Set<String> studentIds = studentIdsByTeacher.getOrDefault(teacher.getId(), Set.of());
                    long archived = forms.stream().filter(item -> studentIds.contains(item.getStudentId()) && FormStatus.ARCHIVED.getLabel().equals(item.getStatus())).count();
                    long pending = forms.stream().filter(item -> studentIds.contains(item.getStudentId()) && FormStatus.COLLEGE_REVIEWING.getLabel().equals(item.getStatus())).count();
                    List<EvaluationRecordEntity> teacherEvaluations = evaluations.stream().filter(item -> teacher.getId().equals(item.getTeacherId())).toList();
                    double averageScore = teacherEvaluations.stream()
                            .map(item -> item.getCollegeScore() == null ? item.getFinalScore() : item.getCollegeScore())
                            .filter(Objects::nonNull)
                            .mapToInt(Integer::intValue)
                            .average()
                            .orElse(0);
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("teacherId", teacher.getId());
                    item.put("teacherName", teacher.getName());
                    item.put("employeeNo", teacher.getEmployeeNo());
                    item.put("department", teacher.getDepartment());
                    item.put("studentCount", studentIds.size());
                    item.put("archivedCount", archived);
                    item.put("pendingArchiveCount", pending);
                    item.put("evaluationCount", teacherEvaluations.size());
                    item.put("averageScore", Math.round(averageScore * 10.0) / 10.0);
                    return item;
                })
                .sorted((left, right) -> Integer.compare(((Number) right.get("studentCount")).intValue(), ((Number) left.get("studentCount")).intValue()))
                .toList();
    }

    private Map<String, Object> buildEvaluationSummary(List<EvaluationRecordEntity> evaluations) {
        long confirmed = evaluations.stream().filter(item -> Boolean.TRUE.equals(item.getConfirmedByCollege())).count();
        double averageScore = evaluations.stream()
                .map(item -> item.getCollegeScore() == null ? item.getFinalScore() : item.getCollegeScore())
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("total", evaluations.size());
        payload.put("confirmed", confirmed);
        payload.put("pending", evaluations.size() - confirmed);
        payload.put("averageScore", Math.round(averageScore * 10.0) / 10.0);
        return payload;
    }

    private List<Map<String, Object>> buildScoreDistribution(List<EvaluationRecordEntity> evaluations) {
        List<int[]> ranges = List.of(
                new int[]{90, 100},
                new int[]{80, 89},
                new int[]{70, 79},
                new int[]{60, 69},
                new int[]{0, 59}
        );
        List<Map<String, Object>> payload = new ArrayList<>();
        for (int[] range : ranges) {
            long count = evaluations.stream()
                    .map(item -> item.getCollegeScore() == null ? item.getFinalScore() : item.getCollegeScore())
                    .filter(Objects::nonNull)
                    .filter(score -> score >= range[0] && score <= range[1])
                    .count();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("label", range[0] == 0 ? "60分以下" : range[0] + "-" + range[1]);
            item.put("count", count);
            payload.add(item);
        }
        return payload;
    }

    private List<Map<String, Object>> buildOrganizationUsageRanking(List<OrganizationEntity> organizations, List<InternshipApplicationEntity> internships) {
        Map<String, Long> usage = internships.stream()
                .collect(Collectors.groupingBy(InternshipApplicationEntity::getOrganizationId, LinkedHashMap::new, Collectors.counting()));
        return organizations.stream()
                .map(item -> {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("organizationId", item.getId());
                    payload.put("organizationName", item.getName());
                    payload.put("cooperationStatus", item.getCooperationStatus());
                    payload.put("count", usage.getOrDefault(item.getId(), 0L));
                    return payload;
                })
                .sorted((left, right) -> Long.compare(((Number) right.get("count")).longValue(), ((Number) left.get("count")).longValue()))
                .toList();
    }

    private List<Map<String, Object>> buildMonthlyTrend(List<FormInstanceEntity> forms, List<EvaluationRecordEntity> evaluations) {
        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("yyyy-MM");
        List<String> months = new ArrayList<>();
        for (int index = 5; index >= 0; index--) {
            months.add(LocalDate.now().minusMonths(index).withDayOfMonth(1).format(monthFormatter));
        }
        List<Map<String, Object>> payload = new ArrayList<>();
        for (String month : months) {
            long submitted = forms.stream().filter(item -> month.equals(toMonthKey(item.getSubmittedAt(), monthFormatter))).count();
            long archived = forms.stream().filter(item -> month.equals(toMonthKey(item.getCollegeReviewedAt(), monthFormatter)) && FormStatus.ARCHIVED.getLabel().equals(item.getStatus())).count();
            long evaluated = evaluations.stream().filter(item -> month.equals(toMonthKey(item.getEvaluatedAt(), monthFormatter))).count();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("month", month);
            item.put("submitted", submitted);
            item.put("archived", archived);
            item.put("evaluated", evaluated);
            payload.add(item);
        }
        return payload;
    }

    public List<CollegeApplicationEntity> collegeApplications(LoginUser loginUser) {
        requireRole(loginUser, RoleType.SUPER_ADMIN);
        return collegeApplicationMapper.selectList(Wrappers.<CollegeApplicationEntity>lambdaQuery().orderByDesc(CollegeApplicationEntity::getCreatedAt));
    }

    @Transactional
    public Map<String, Object> reviewCollegeApplication(LoginUser loginUser, String applicationId, Requests.DecisionRequest request) {
        requireRole(loginUser, RoleType.SUPER_ADMIN);
        CollegeApplicationEntity entity = collegeApplicationMapper.selectById(applicationId);
        if (entity == null) {
            throw new BizException("入驻申请不存在");
        }
        if (!"待审核".equals(entity.getStatus())) {
            throw new BizException("该入驻申请已处理");
        }

        boolean approved = Boolean.TRUE.equals(request.approved());
        String reviewComment = Optional.ofNullable(request.comment()).orElse("").trim();
        entity.setStatus(approved ? "已通过" : "已驳回");
        CollegeEntity college = null;
        UserAccountEntity collegeAdmin = null;
        boolean generatedCollegeAdmin = false;
        if (approved) {
            college = ensureCollegeForApplication(entity);
            UserAccountEntity existingAdmin = firstCollegeAdminUser(college.getId());
            collegeAdmin = existingAdmin != null ? existingAdmin : ensureCollegeAdminForCollege(college, entity);
            generatedCollegeAdmin = existingAdmin == null;
            String accountTip = existingAdmin == null
                    ? "学院管理员账号：" + collegeAdmin.getAccount() + "，初始密码：123456"
                    : "已关联学院管理员账号：" + collegeAdmin.getAccount();
            entity.setReviewComment(mergeReviewComment(reviewComment, accountTip));
            insertAudit("OPERATION", loginUser.id(), "审核学院入驻申请", entity.getCollegeName() + " 已通过，自动创建学院与管理员账号");
        } else {
            entity.setReviewComment(reviewComment);
            insertAudit("OPERATION", loginUser.id(), "审核学院入驻申请", entity.getCollegeName() + " 已驳回");
        }
        collegeApplicationMapper.updateById(entity);
        return buildCollegeApplicationReviewResult(entity, approved, college, collegeAdmin, generatedCollegeAdmin);
    }

    private Map<String, Object> buildCollegeApplicationReviewResult(
            CollegeApplicationEntity entity,
            boolean approved,
            CollegeEntity college,
            UserAccountEntity collegeAdmin,
            boolean generatedCollegeAdmin
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", entity.getId());
        payload.put("approved", approved);
        payload.put("status", entity.getStatus());
        payload.put("reviewComment", entity.getReviewComment());
        payload.put("schoolName", entity.getSchoolName());
        payload.put("collegeName", entity.getCollegeName());
        if (approved && college != null && collegeAdmin != null) {
            payload.put("collegeId", college.getId());
            payload.put("collegeAdminAccount", collegeAdmin.getAccount());
            payload.put("collegeAdminName", collegeAdmin.getName());
            payload.put("generatedCollegeAdmin", generatedCollegeAdmin);
            payload.put("defaultPassword", generatedCollegeAdmin ? "123456" : null);
            payload.put("mustChangePassword", Boolean.TRUE.equals(collegeAdmin.getMustChangePassword()));
        } else {
            payload.put("collegeId", null);
            payload.put("collegeAdminAccount", null);
            payload.put("collegeAdminName", null);
            payload.put("generatedCollegeAdmin", false);
            payload.put("defaultPassword", null);
            payload.put("mustChangePassword", false);
        }
        return payload;
    }
    private CollegeEntity ensureCollegeForApplication(CollegeApplicationEntity application) {
        CollegeEntity existing = collegeMapper.selectOne(
                Wrappers.<CollegeEntity>lambdaQuery()
                        .eq(CollegeEntity::getSchoolName, application.getSchoolName())
                        .eq(CollegeEntity::getName, application.getCollegeName())
                        .last("limit 1")
        );
        if (existing != null) {
            existing.setContactName(Optional.ofNullable(application.getContactName()).filter(item -> !item.isBlank()).orElse(existing.getContactName()));
            existing.setContactPhone(Optional.ofNullable(application.getContactPhone()).filter(item -> !item.isBlank()).orElse(existing.getContactPhone()));
            existing.setDescription(Optional.ofNullable(application.getDescription()).filter(item -> !item.isBlank()).orElse(existing.getDescription()));
            collegeMapper.updateById(existing);
            return existing;
        }

        CollegeEntity college = new CollegeEntity();
        college.setId(IdGenerator.nextId("college"));
        college.setSchoolName(application.getSchoolName());
        college.setName(application.getCollegeName());
        college.setContactName(application.getContactName());
        college.setContactPhone(application.getContactPhone());
        college.setDescription(application.getDescription());
        collegeMapper.insert(college);
        return college;
    }

    private UserAccountEntity ensureCollegeAdminForCollege(CollegeEntity college, CollegeApplicationEntity application) {
        UserAccountEntity existing = firstCollegeAdminUser(college.getId());
        if (existing != null) {
            return existing;
        }

        UserAccountEntity user = new UserAccountEntity();
        user.setId(IdGenerator.nextId("user"));
        user.setAccount(nextCollegeAdminAccount());
        user.setName(resolveCollegeAdminName(application, college));
        user.setRole(RoleType.COLLEGE_ADMIN.name());
        user.setPassword(PasswordUtils.sha256("123456"));
        user.setMustChangePassword(true);
        user.setStatus("ACTIVE");
        user.setCollegeId(college.getId());
        userAccountMapper.insert(user);
        return user;
    }

    private String resolveCollegeAdminName(CollegeApplicationEntity application, CollegeEntity college) {
        String contactName = Optional.ofNullable(application.getContactName()).map(String::trim).orElse("");
        if (!contactName.isBlank()) {
            return contactName;
        }
        return college.getName() + "管理员";
    }

    private String nextCollegeAdminAccount() {
        long sequence = userAccountMapper.selectCount(
                Wrappers.<UserAccountEntity>lambdaQuery().eq(UserAccountEntity::getRole, RoleType.COLLEGE_ADMIN.name())
        ) + 1;
        while (true) {
            String candidate = String.format(Locale.ROOT, "college%02d", sequence);
            long exists = userAccountMapper.selectCount(
                    Wrappers.<UserAccountEntity>lambdaQuery().eq(UserAccountEntity::getAccount, candidate)
            );
            if (exists == 0) {
                return candidate;
            }
            sequence += 1;
        }
    }

    private String mergeReviewComment(String reviewComment, String extraComment) {
        if (extraComment == null || extraComment.isBlank()) {
            return reviewComment;
        }
        if (reviewComment == null || reviewComment.isBlank()) {
            return extraComment;
        }
        return reviewComment + "；" + extraComment;
    }

    public List<Map<String, Object>> collegeAdmins(LoginUser loginUser) {
        requireRole(loginUser, RoleType.SUPER_ADMIN);
        return userAccountMapper.selectList(
                        Wrappers.<UserAccountEntity>lambdaQuery()
                                .eq(UserAccountEntity::getRole, RoleType.COLLEGE_ADMIN.name())
                                .orderByAsc(UserAccountEntity::getCollegeId)
                                .orderByAsc(UserAccountEntity::getAccount)
                ).stream()
                .map(user -> {
                    CollegeEntity college = user.getCollegeId() == null ? null : collegeMapper.selectById(user.getCollegeId());
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("id", user.getId());
                    payload.put("account", user.getAccount());
                    payload.put("name", user.getName());
                    payload.put("status", user.getStatus());
                    payload.put("mustChangePassword", Boolean.TRUE.equals(user.getMustChangePassword()));
                    payload.put("lastLoginAt", user.getLastLoginAt());
                    payload.put("collegeId", user.getCollegeId());
                    payload.put("collegeName", college == null ? "" : college.getName());
                    payload.put("schoolName", college == null ? "" : college.getSchoolName());
                    payload.put("contactName", college == null ? "" : college.getContactName());
                    payload.put("contactPhone", college == null ? "" : college.getContactPhone());
                    return payload;
                })
                .toList();
    }

    @Transactional
    public void resetCollegeAdminPassword(LoginUser loginUser, String userId) {
        requireRole(loginUser, RoleType.SUPER_ADMIN);
        UserAccountEntity user = requireUser(userId);
        if (!RoleType.COLLEGE_ADMIN.name().equals(user.getRole())) {
            throw new BizException("目标账号不是学院管理员");
        }
        user.setPassword(PasswordUtils.sha256("123456"));
        user.setMustChangePassword(true);
        userAccountMapper.updateById(user);
        insertAudit("OPERATION", loginUser.id(), "重置学院管理员密码", user.getName() + " / " + user.getAccount());
    }

    @Transactional
    public void changeCollegeAdminStatus(LoginUser loginUser, String userId, String status) {
        requireRole(loginUser, RoleType.SUPER_ADMIN);
        UserAccountEntity user = requireUser(userId);
        if (!RoleType.COLLEGE_ADMIN.name().equals(user.getRole())) {
            throw new BizException("目标账号不是学院管理员");
        }
        String normalizedStatus = Optional.ofNullable(status).map(String::trim).orElse("");
        if (!Set.of("ACTIVE", "DISABLED").contains(normalizedStatus)) {
            throw new BizException("学院管理员账号状态仅支持 ACTIVE 或 DISABLED");
        }
        user.setStatus(normalizedStatus);
        userAccountMapper.updateById(user);
        insertAudit("OPERATION", loginUser.id(), "更新学院管理员账号状态", user.getName() + " -> " + normalizedStatus);
    }

    public Map<String, Object> basicData(LoginUser loginUser) {
        requireRole(loginUser, RoleType.SUPER_ADMIN);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("colleges", collegeMapper.selectList(Wrappers.<CollegeEntity>lambdaQuery()));
        payload.put("roles", Arrays.stream(RoleType.values()).map(Enum::name).toList());
        payload.put("formStatuses", Arrays.stream(FormStatus.values()).map(FormStatus::getLabel).toList());
        return payload;
    }

    public List<Map<String, Object>> systemSettings(LoginUser loginUser) {
        requireRole(loginUser, RoleType.SUPER_ADMIN);
        Map<String, SystemSettingEntity> current = systemSettingMapper.selectList(Wrappers.<SystemSettingEntity>lambdaQuery())
                .stream()
                .collect(Collectors.toMap(SystemSettingEntity::getSettingKey, item -> item, (left, right) -> left, LinkedHashMap::new));
        return listSystemSettings().stream().map(spec -> toSystemSettingPayload(spec, current.get(spec.key()))).toList();
    }

    @Transactional
    public void saveSystemSettings(LoginUser loginUser, Requests.SystemSettingSaveRequest request) {
        requireRole(loginUser, RoleType.SUPER_ADMIN);
        if (request.items() == null || request.items().isEmpty()) {
            throw new BizException("参数列表不能为空");
        }

        Map<String, SystemSettingEntity> current = systemSettingMapper.selectList(Wrappers.<SystemSettingEntity>lambdaQuery())
                .stream()
                .collect(Collectors.toMap(SystemSettingEntity::getSettingKey, item -> item, (left, right) -> left, LinkedHashMap::new));

        for (Requests.SystemSettingUpdateItem item : request.items()) {
            SystemSettingSpec spec = SYSTEM_SETTING_SPEC_MAP.get(item.key());
            if (spec == null) {
                throw new BizException("系统参数不存在: " + item.key());
            }
            if (!isValidSettingValue(spec, item.value())) {
                throw new BizException(spec.name() + " 配置值不合法");
            }

            String normalizedValue = normalizeSettingValue(spec, item.value());
            SystemSettingEntity entity = current.get(spec.key());
            if (entity == null) {
                entity = new SystemSettingEntity();
                entity.setSettingKey(spec.key());
                current.put(spec.key(), entity);
            }
            entity.setSettingValue(normalizedValue);
            entity.setCategory(spec.category());
            entity.setName(spec.name());
            entity.setDescription(spec.description());
            entity.setUpdatedAt(LocalDateTime.now());
            if (systemSettingMapper.selectById(spec.key()) == null) {
                systemSettingMapper.insert(entity);
            } else {
                systemSettingMapper.updateById(entity);
            }
        }
        insertAudit("SYSTEM_SETTING", loginUser.id(), "更新系统参数", "更新提醒规则、级别和催办模板配置");
    }
    public List<Map<String, Object>> adminFormTemplates(LoginUser loginUser) {
        requireRole(loginUser, RoleType.SUPER_ADMIN);
        return listAllTemplates().stream().map(this::toFormTemplatePayload).toList();
    }

    @Transactional
    public void createFormTemplate(LoginUser loginUser, Requests.FormTemplateCreateRequest request) {
        requireRole(loginUser, RoleType.SUPER_ADMIN);
        if (formTemplateMapper.selectById(request.code()) != null) {
            throw new BizException("模板编码已存在");
        }
        FormTemplateEntity entity = new FormTemplateEntity();
        entity.setCode(request.code().trim());
        entity.setCreatedAt(LocalDateTime.now());
        applyTemplateConfig(entity, request.name(), request.category(), request.description(), request.applicableTypes(), request.fieldSchema(), request.enabled(), request.sortNo());
        formTemplateMapper.insert(entity);
        insertAudit("FORM_TEMPLATE", loginUser.id(), "创建表单模板", entity.getCode() + " / " + entity.getName());
    }

    @Transactional
    public void updateFormTemplate(LoginUser loginUser, String code, Requests.FormTemplateUpdateRequest request) {
        requireRole(loginUser, RoleType.SUPER_ADMIN);
        FormTemplateEntity entity = requireTemplate(code);
        applyTemplateConfig(entity, request.name(), request.category(), request.description(), request.applicableTypes(), request.fieldSchema(), request.enabled(), request.sortNo());
        formTemplateMapper.updateById(entity);
        insertAudit("FORM_TEMPLATE", loginUser.id(), "更新表单模板", entity.getCode() + " / " + entity.getName());
    }

    @Transactional
    public void changeFormTemplateStatus(LoginUser loginUser, String code, Requests.StatusToggleRequest request) {
        requireRole(loginUser, RoleType.SUPER_ADMIN);
        FormTemplateEntity entity = requireTemplate(code);
        entity.setEnabled(Boolean.TRUE.equals(request.enabled()));
        entity.setUpdatedAt(LocalDateTime.now());
        formTemplateMapper.updateById(entity);
        insertAudit("FORM_TEMPLATE", loginUser.id(), Boolean.TRUE.equals(request.enabled()) ? "启用表单模板" : "停用表单模板", entity.getCode() + " / " + entity.getName());
    }

    public List<Map<String, Object>> logs(LoginUser loginUser) {
        requireRole(loginUser, RoleType.SUPER_ADMIN);
        return auditLogMapper.selectList(
                        Wrappers.<AuditLogEntity>lambdaQuery().orderByDesc(AuditLogEntity::getCreatedAt).last("limit 50")
                ).stream()
                .map(this::toAuditPayload)
                .toList();
    }

    public LoginUser currentLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
            throw new BizException("未登录或登录已失效");
        }
        return loginUser;
    }

    private List<Map<String, Object>> studentRiskAlerts(LoginUser loginUser) {
        StudentEntity student = requireStudentByUser(loginUser.id());
        TeacherEntity teacher = currentEffectiveTeacher(student.getId());
        UserAccountEntity collegeAdmin = firstCollegeAdminUser(student.getCollegeId());
        List<FormInstanceEntity> forms = formInstanceMapper.selectList(
                Wrappers.<FormInstanceEntity>lambdaQuery()
                        .eq(FormInstanceEntity::getStudentId, student.getId())
                        .orderByDesc(FormInstanceEntity::getUpdatedAt)
        );

        List<Map<String, Object>> alerts = new ArrayList<>();
        for (FormInstanceEntity form : forms) {
            if (FormStatus.TEACHER_REVIEWING.getLabel().equals(form.getStatus())) {
                long overdueDays = overdueDays(form.getSubmittedAt(), teacherReviewTimeoutDays());
                if (overdueDays >= 0 && teacher != null) {
                    alerts.add(buildRiskAlert(
                            "student-form-teacher-" + form.getId(),
                            "warning",
                            "材料审核",
                            form.getTemplateName() + " 教师审核已超时",
                            "已提交 " + (overdueDays + 2) + " 天，可提醒指导教师尽快处理。",
                            overdueDays,
                            true,
                            "催办教师",
                            teacher.getUserId(),
                            teacher.getName(),
                            "/student/tasks",
                            student.getName() + " 的 " + form.getTemplateName() + " 等待教师审核",
                            "学生发起催办，请尽快处理待审核材料。",
                            "/teacher/reviews"
                    ));
                }
            }
            if (FormStatus.COLLEGE_REVIEWING.getLabel().equals(form.getStatus())) {
                long overdueDays = overdueDays(form.getTeacherReviewedAt(), collegeReviewTimeoutDays());
                if (overdueDays >= 0 && collegeAdmin != null) {
                    alerts.add(buildRiskAlert(
                            "student-form-college-" + form.getId(),
                            "warning",
                            "学院归档",
                            form.getTemplateName() + " 学院归档已超时",
                            "教师审核通过后已等待 " + (overdueDays + 2) + " 天，可提醒学院管理员处理。",
                            overdueDays,
                            true,
                            "催办学院",
                            collegeAdmin.getId(),
                            collegeAdmin.getName(),
                            "/student/tasks",
                            student.getName() + " 的 " + form.getTemplateName() + " 等待学院归档",
                            "学生发起催办，请尽快完成学院终审与归档。",
                            "/college/archive"
                    ));
                }
            }
            if (Set.of(FormStatus.TEACHER_RETURNED.getLabel(), FormStatus.COLLEGE_RETURNED.getLabel()).contains(form.getStatus())) {
                long overdueDays = overdueDays(form.getUpdatedAt(), studentResubmitTimeoutDays());
                if (overdueDays >= 0) {
                    alerts.add(buildRiskAlert(
                            "student-form-returned-" + form.getId(),
                            "danger",
                            "退回未修改",
                            form.getTemplateName() + " 退回后仍未修改",
                            "材料已被退回并超过 " + (overdueDays + 2) + " 天未重新提交，请尽快处理。",
                            overdueDays,
                            false,
                            "",
                            null,
                            "",
                            "/student/tasks",
                            "",
                            "",
                            ""
                    ));
                }
            }
        }
        return sortAlerts(alerts);
    }

    private List<Map<String, Object>> teacherRiskAlerts(LoginUser loginUser) {
        TeacherEntity teacher = requireTeacherByUser(loginUser.id());
        Set<String> studentIds = mentorApplicationMapper.selectList(
                Wrappers.<MentorApplicationEntity>lambdaQuery()
                        .eq(MentorApplicationEntity::getTeacherId, teacher.getId())
                        .eq(MentorApplicationEntity::getStatus, MentorApplicationStatus.EFFECTIVE.getLabel())
        ).stream().map(MentorApplicationEntity::getStudentId).collect(Collectors.toSet());
        List<FormInstanceEntity> forms = listFormsByStudentIds(studentIds);
        List<EvaluationRecordEntity> evaluations = evaluationRecordMapper.selectList(
                Wrappers.<EvaluationRecordEntity>lambdaQuery()
                        .eq(EvaluationRecordEntity::getTeacherId, teacher.getId())
                        .orderByDesc(EvaluationRecordEntity::getEvaluatedAt)
        );

        List<Map<String, Object>> alerts = new ArrayList<>();
        for (FormInstanceEntity form : forms) {
            StudentEntity student = requireStudent(form.getStudentId());
            if (FormStatus.TEACHER_REVIEWING.getLabel().equals(form.getStatus())) {
                long overdueDays = overdueDays(form.getSubmittedAt(), teacherReviewTimeoutDays());
                if (overdueDays >= 0) {
                    alerts.add(buildRiskAlert(
                            "teacher-pending-review-" + form.getId(),
                            "warning",
                            "待审核材料",
                            student.getName() + " 的 " + form.getTemplateName() + " 待审核超时",
                            "学生材料已等待 " + (overdueDays + 2) + " 天，请尽快完成审核。",
                            overdueDays,
                            false,
                            "",
                            null,
                            "",
                            "/teacher/reviews",
                            "",
                            "",
                            ""
                    ));
                }
            }
            if (Set.of(FormStatus.TEACHER_RETURNED.getLabel(), FormStatus.COLLEGE_RETURNED.getLabel()).contains(form.getStatus())) {
                long overdueDays = overdueDays(form.getUpdatedAt(), studentResubmitTimeoutDays());
                if (overdueDays >= 0) {
                    alerts.add(buildRiskAlert(
                            "teacher-remind-student-" + form.getId(),
                            "danger",
                            "退回未修改",
                            student.getName() + " 的 " + form.getTemplateName() + " 退回后未修改",
                            "该材料退回后已超过 " + (overdueDays + 2) + " 天未修改，可提醒学生处理。",
                            overdueDays,
                            true,
                            "催办学生",
                            student.getUserId(),
                            student.getName(),
                            "/teacher/reviews",
                            form.getTemplateName() + " 已被催办修改",
                            "指导教师提醒你尽快修改退回材料并重新提交。",
                            "/student/tasks"
                    ));
                }
            }
        }

        for (EvaluationRecordEntity evaluation : evaluations) {
            if (Boolean.TRUE.equals(evaluation.getSubmittedToCollege()) && !Boolean.TRUE.equals(evaluation.getConfirmedByCollege())) {
                long overdueDays = overdueDays(evaluation.getEvaluatedAt(), evaluationConfirmTimeoutDays());
                if (overdueDays >= 0) {
                    StudentEntity student = requireStudent(evaluation.getStudentId());
                    UserAccountEntity collegeAdmin = firstCollegeAdminUser(student.getCollegeId());
                    if (collegeAdmin != null) {
                        alerts.add(buildRiskAlert(
                                "teacher-remind-college-eval-" + evaluation.getId(),
                                "warning",
                                "评价确认",
                                student.getName() + " 的实习评价待学院确认",
                                "评价提交后已超过 " + (overdueDays + 3) + " 天，可提醒学院确认最终成绩。",
                                overdueDays,
                                true,
                                "催办学院",
                                collegeAdmin.getId(),
                                collegeAdmin.getName(),
                                "/teacher/evaluations",
                                student.getName() + " 的实习评价待学院确认",
                                "指导教师发起催办，请尽快确认评价结果。",
                                "/college/evaluations"
                        ));
                    }
                }
            }
        }
        return sortAlerts(alerts);
    }

    private List<Map<String, Object>> collegeRiskAlerts(LoginUser loginUser) {
        List<StudentEntity> students = studentMapper.selectList(
                Wrappers.<StudentEntity>lambdaQuery().eq(StudentEntity::getCollegeId, loginUser.collegeId())
        );
        Set<String> studentIds = students.stream().map(StudentEntity::getId).collect(Collectors.toSet());
        List<FormInstanceEntity> forms = listFormsByStudentIds(studentIds);
        List<MentorApplicationEntity> mentors = listMentorApplicationsByStudentIds(studentIds);
        List<InternshipApplicationEntity> internships = listInternshipsByStudentIds(studentIds);
        List<EvaluationRecordEntity> evaluations = listEvaluationsByStudentIds(studentIds);

        List<Map<String, Object>> alerts = new ArrayList<>();
        for (MentorApplicationEntity mentor : mentors) {
            if (MentorApplicationStatus.PENDING_COLLEGE.getLabel().equals(mentor.getStatus())) {
                long overdueDays = overdueDays(mentor.getTeacherReviewedAt(), collegeReviewTimeoutDays());
                if (overdueDays >= 0) {
                    StudentEntity student = requireStudent(mentor.getStudentId());
                    alerts.add(buildRiskAlert(
                            "college-mentor-pending-" + mentor.getId(),
                            "warning",
                            "指导复核",
                            student.getName() + " 的指导关系待学院复核",
                            "教师确认后已超过 " + (overdueDays + 2) + " 天，请尽快完成学院复核。",
                            overdueDays,
                            false,
                            "",
                            null,
                            "",
                            "/college/mentor-relations",
                            "",
                            "",
                            ""
                    ));
                }
            }
        }

        for (InternshipApplicationEntity internship : internships) {
            if (InternshipApplicationStatus.PENDING_COLLEGE.getLabel().equals(internship.getStatus())) {
                long overdueDays = overdueDays(internship.getCreatedAt(), collegeReviewTimeoutDays());
                if (overdueDays >= 0) {
                    StudentEntity student = requireStudent(internship.getStudentId());
                    alerts.add(buildRiskAlert(
                            "college-internship-pending-" + internship.getId(),
                            "warning",
                            "实习审批",
                            student.getName() + " 的实习申请待学院审批",
                            "申请已超过 " + (overdueDays + 2) + " 天未处理，请尽快审批。",
                            overdueDays,
                            false,
                            "",
                            null,
                            "",
                            "/college/internship-applications",
                            "",
                            "",
                            ""
                    ));
                }
            }
        }

        for (FormInstanceEntity form : forms) {
            StudentEntity student = requireStudent(form.getStudentId());
            if (FormStatus.TEACHER_REVIEWING.getLabel().equals(form.getStatus())) {
                long overdueDays = overdueDays(form.getSubmittedAt(), teacherReviewTimeoutDays());
                TeacherEntity teacher = currentEffectiveTeacher(form.getStudentId());
                if (overdueDays >= 0 && teacher != null) {
                    alerts.add(buildRiskAlert(
                            "college-remind-teacher-form-" + form.getId(),
                            "warning",
                            "教师审核",
                            student.getName() + " 的 " + form.getTemplateName() + " 待教师审核",
                            "材料提交后已超过 " + (overdueDays + 2) + " 天，可提醒指导教师处理。",
                            overdueDays,
                            true,
                            "催办教师",
                            teacher.getUserId(),
                            teacher.getName(),
                            "/college/archive",
                            student.getName() + " 的 " + form.getTemplateName() + " 待教师审核",
                            "学院管理员发起催办，请尽快完成材料审核。",
                            "/teacher/reviews"
                    ));
                }
            }
            if (FormStatus.COLLEGE_REVIEWING.getLabel().equals(form.getStatus())) {
                long overdueDays = overdueDays(form.getTeacherReviewedAt(), collegeReviewTimeoutDays());
                if (overdueDays >= 0) {
                    alerts.add(buildRiskAlert(
                            "college-form-pending-" + form.getId(),
                            "warning",
                            "学院归档",
                            student.getName() + " 的 " + form.getTemplateName() + " 待学院归档",
                            "教师审核通过后已超过 " + (overdueDays + 2) + " 天，请尽快处理。",
                            overdueDays,
                            false,
                            "",
                            null,
                            "",
                            "/college/archive",
                            "",
                            "",
                            ""
                    ));
                }
            }
            if (Set.of(FormStatus.TEACHER_RETURNED.getLabel(), FormStatus.COLLEGE_RETURNED.getLabel()).contains(form.getStatus())) {
                long overdueDays = overdueDays(form.getUpdatedAt(), studentResubmitTimeoutDays());
                if (overdueDays >= 0) {
                    alerts.add(buildRiskAlert(
                            "college-remind-student-form-" + form.getId(),
                            "danger",
                            "退回未修改",
                            student.getName() + " 的 " + form.getTemplateName() + " 退回后未修改",
                            "材料退回后已超过 " + (overdueDays + 2) + " 天未重新提交，可提醒学生处理。",
                            overdueDays,
                            true,
                            "催办学生",
                            student.getUserId(),
                            student.getName(),
                            "/college/archive",
                            form.getTemplateName() + " 已被学院催办修改",
                            "学院管理员提醒你尽快修改退回材料并重新提交。",
                            "/student/tasks"
                    ));
                }
            }
        }

        for (EvaluationRecordEntity evaluation : evaluations) {
            if (Boolean.TRUE.equals(evaluation.getSubmittedToCollege()) && !Boolean.TRUE.equals(evaluation.getConfirmedByCollege())) {
                long overdueDays = overdueDays(evaluation.getEvaluatedAt(), evaluationConfirmTimeoutDays());
                if (overdueDays >= 0) {
                    StudentEntity student = requireStudent(evaluation.getStudentId());
                    alerts.add(buildRiskAlert(
                            "college-evaluation-pending-" + evaluation.getId(),
                            "warning",
                            "评价确认",
                            student.getName() + " 的实习评价待学院确认",
                            "教师提交评价后已超过 " + (overdueDays + 3) + " 天，请尽快确认最终成绩。",
                            overdueDays,
                            false,
                            "",
                            null,
                            "",
                            "/college/evaluations",
                            "",
                            "",
                            ""
                    ));
                }
            }
        }
        return sortAlerts(alerts);
    }

    private Map<String, Object> studentDashboard(LoginUser loginUser) {
        StudentEntity student = requireStudentByUser(loginUser.id());
        MentorApplicationEntity mentor = mentorApplicationMapper.selectOne(
                Wrappers.<MentorApplicationEntity>lambdaQuery().eq(MentorApplicationEntity::getStudentId, student.getId()).orderByDesc(MentorApplicationEntity::getCreatedAt).last("limit 1")
        );
        InternshipApplicationEntity internship = internshipApplicationMapper.selectOne(
                Wrappers.<InternshipApplicationEntity>lambdaQuery().eq(InternshipApplicationEntity::getStudentId, student.getId()).orderByDesc(InternshipApplicationEntity::getCreatedAt).last("limit 1")
        );
        List<FormInstanceEntity> forms = formInstanceMapper.selectList(Wrappers.<FormInstanceEntity>lambdaQuery().eq(FormInstanceEntity::getStudentId, student.getId()));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("internshipStatus", student.getInternshipStatus());
        payload.put("mentorStatus", mentor == null ? "未申请" : mentor.getStatus());
        payload.put("organizationStatus", internship == null ? "未申请" : internship.getStatus());
        payload.put("totalForms", forms.size());
        payload.put("todoCount", forms.stream().filter(item -> Set.of(FormStatus.DRAFT.getLabel(), FormStatus.TEACHER_RETURNED.getLabel(), FormStatus.COLLEGE_RETURNED.getLabel()).contains(item.getStatus())).count());
        payload.put("returnedCount", forms.stream().filter(item -> Set.of(FormStatus.TEACHER_RETURNED.getLabel(), FormStatus.COLLEGE_RETURNED.getLabel()).contains(item.getStatus())).count());
        payload.put("archivedCount", forms.stream().filter(item -> FormStatus.ARCHIVED.getLabel().equals(item.getStatus())).count());
        return payload;
    }

    private Map<String, Object> teacherDashboard(LoginUser loginUser) {
        TeacherEntity teacher = requireTeacherByUser(loginUser.id());
        List<MentorApplicationEntity> applications = mentorApplicationMapper.selectList(
                Wrappers.<MentorApplicationEntity>lambdaQuery().eq(MentorApplicationEntity::getTeacherId, teacher.getId())
        );
        Set<String> studentIds = applications.stream().filter(item -> MentorApplicationStatus.EFFECTIVE.getLabel().equals(item.getStatus())).map(MentorApplicationEntity::getStudentId).collect(Collectors.toSet());
        List<FormInstanceEntity> forms = studentIds.isEmpty() ? List.of() : formInstanceMapper.selectList(
                Wrappers.<FormInstanceEntity>lambdaQuery().in(FormInstanceEntity::getStudentId, studentIds)
        );

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("studentCount", studentIds.size());
        payload.put("pendingMentorRequests", applications.stream().filter(item -> MentorApplicationStatus.PENDING_TEACHER.getLabel().equals(item.getStatus())).count());
        payload.put("pendingReviewCount", forms.stream().filter(item -> FormStatus.TEACHER_REVIEWING.getLabel().equals(item.getStatus())).count());
        long archivedCount = forms.stream().filter(item -> FormStatus.ARCHIVED.getLabel().equals(item.getStatus())).count();
        payload.put("archivedCount", archivedCount);
        payload.put("completionRate", forms.isEmpty() ? 0 : Math.round((archivedCount * 100.0) / forms.size()));
        return payload;
    }

    private Map<String, Object> collegeDashboard(LoginUser loginUser) {
        List<StudentEntity> students = studentMapper.selectList(
                Wrappers.<StudentEntity>lambdaQuery().eq(StudentEntity::getCollegeId, loginUser.collegeId())
        );
        Set<String> studentIds = students.stream().map(StudentEntity::getId).collect(Collectors.toSet());
        List<FormInstanceEntity> forms = studentIds.isEmpty() ? List.of() : formInstanceMapper.selectList(
                Wrappers.<FormInstanceEntity>lambdaQuery().in(FormInstanceEntity::getStudentId, studentIds)
        );
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("studentCount", students.size());
        payload.put("teacherCount", teacherMapper.selectCount(Wrappers.<TeacherEntity>lambdaQuery().eq(TeacherEntity::getCollegeId, loginUser.collegeId())));
        payload.put("pendingMentorReviewCount", studentIds.isEmpty() ? 0 : mentorApplicationMapper.selectCount(
                Wrappers.<MentorApplicationEntity>lambdaQuery().in(MentorApplicationEntity::getStudentId, studentIds).eq(MentorApplicationEntity::getStatus, MentorApplicationStatus.PENDING_COLLEGE.getLabel())
        ));
        payload.put("pendingInternshipReviewCount", studentIds.isEmpty() ? 0 : internshipApplicationMapper.selectCount(
                Wrappers.<InternshipApplicationEntity>lambdaQuery().in(InternshipApplicationEntity::getStudentId, studentIds).eq(InternshipApplicationEntity::getStatus, InternshipApplicationStatus.PENDING_COLLEGE.getLabel())
        ));
        payload.put("pendingArchiveCount", forms.stream().filter(item -> FormStatus.COLLEGE_REVIEWING.getLabel().equals(item.getStatus())).count());
        payload.put("archivedCount", forms.stream().filter(item -> FormStatus.ARCHIVED.getLabel().equals(item.getStatus())).count());
        payload.put("riskStudentCount", forms.stream().filter(item -> Set.of(FormStatus.TEACHER_RETURNED.getLabel(), FormStatus.COLLEGE_RETURNED.getLabel()).contains(item.getStatus())).count());
        return payload;
    }

    private Map<String, Object> superDashboard() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("activeUsers", userAccountMapper.selectCount(Wrappers.<UserAccountEntity>lambdaQuery().eq(UserAccountEntity::getStatus, "ACTIVE")));
        payload.put("collegeApplicationCount", collegeApplicationMapper.selectCount(Wrappers.<CollegeApplicationEntity>lambdaQuery()));
        payload.put("pendingCollegeApplicationCount", collegeApplicationMapper.selectCount(Wrappers.<CollegeApplicationEntity>lambdaQuery().eq(CollegeApplicationEntity::getStatus, "待审核")));
        payload.put("totalForms", formInstanceMapper.selectCount(Wrappers.<FormInstanceEntity>lambdaQuery()));
        payload.put("unreadMessages", messageNoticeMapper.selectCount(Wrappers.<MessageNoticeEntity>lambdaQuery().eq(MessageNoticeEntity::getReadFlag, false)));
        return payload;
    }

    private Map<String, Object> toUserPayload(UserAccountEntity user) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", user.getId());
        payload.put("account", user.getAccount());
        payload.put("name", user.getName());
        payload.put("role", user.getRole());
        payload.put("collegeId", user.getCollegeId());
        payload.put("mustChangePassword", user.getMustChangePassword());
        payload.put("lastLoginAt", user.getLastLoginAt());
        payload.put("profile", switch (RoleType.valueOf(user.getRole())) {
            case STUDENT -> studentMapper.selectOne(Wrappers.<StudentEntity>lambdaQuery().eq(StudentEntity::getUserId, user.getId()));
            case TEACHER -> teacherMapper.selectOne(Wrappers.<TeacherEntity>lambdaQuery().eq(TeacherEntity::getUserId, user.getId()));
            case COLLEGE_ADMIN -> user.getCollegeId() == null ? null : collegeMapper.selectById(user.getCollegeId());
            case SUPER_ADMIN -> null;
        });
        return payload;
    }

    private Map<String, Object> toMentorApplicationPayload(MentorApplicationEntity item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", item.getId());
        payload.put("status", item.getStatus());
        payload.put("studentRemark", item.getStudentRemark());
        payload.put("teacherRemark", item.getTeacherRemark());
        payload.put("collegeRemark", item.getCollegeRemark());
        payload.put("createdAt", item.getCreatedAt());
        payload.put("teacherReviewedAt", item.getTeacherReviewedAt());
        payload.put("collegeReviewedAt", item.getCollegeReviewedAt());
        payload.put("effectiveAt", item.getEffectiveAt());
        payload.put("student", toStudentSimple(requireStudent(item.getStudentId())));
        payload.put("teacher", toTeacherSimple(requireTeacher(item.getTeacherId())));
        return payload;
    }

    private Map<String, Object> toInternshipPayload(InternshipApplicationEntity item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", item.getId());
        payload.put("status", item.getStatus());
        payload.put("batchName", item.getBatchName());
        payload.put("position", item.getPosition());
        payload.put("gradeTarget", item.getGradeTarget());
        payload.put("startDate", item.getStartDate());
        payload.put("endDate", item.getEndDate());
        payload.put("remark", item.getRemark());
        payload.put("attachments", readMapList(item.getAttachmentsJson()));
        payload.put("organizationConfirmation", item.getOrganizationConfirmation());
        payload.put("organizationFeedback", item.getOrganizationFeedback());
        payload.put("receivedAt", item.getReceivedAt());
        payload.put("reviewComment", item.getReviewComment());
        payload.put("student", toStudentSimple(requireStudent(item.getStudentId())));
        payload.put("organization", requireOrganization(item.getOrganizationId()));
        return payload;
    }

    private Map<String, Object> toFormTemplatePayload(FormTemplateEntity item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", item.getCode());
        payload.put("name", item.getName());
        payload.put("category", item.getCategory());
        payload.put("description", item.getDescription());
        payload.put("applicableTypes", readList(item.getApplicableTypesJson()));
        payload.put("fieldSchema", readMapList(item.getFieldSchemaJson()));
        payload.put("enabled", isTemplateEnabled(item));
        payload.put("sortNo", Optional.ofNullable(item.getSortNo()).orElse(100));
        payload.put("createdAt", item.getCreatedAt());
        payload.put("updatedAt", item.getUpdatedAt());
        return payload;
    }

    private Map<String, Object> toTeacherSimple(TeacherEntity teacher) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", teacher.getId());
        payload.put("name", teacher.getName());
        payload.put("employeeNo", teacher.getEmployeeNo());
        payload.put("department", teacher.getDepartment());
        payload.put("phone", teacher.getPhone());
        return payload;
    }

    private Map<String, Object> toFormPayload(FormInstanceEntity item) {
        StudentEntity student = requireStudent(item.getStudentId());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", item.getId());
        payload.put("studentId", item.getStudentId());
        payload.put("studentName", student.getName());
        payload.put("studentNo", student.getStudentNo());
        payload.put("internshipType", student.getInternshipType());
        payload.put("mentorTeacherName", Optional.ofNullable(currentEffectiveTeacher(item.getStudentId())).map(TeacherEntity::getName).orElse(""));
        payload.put("templateCode", item.getTemplateCode());
        payload.put("templateName", item.getTemplateName());
        payload.put("category", item.getCategory());
        payload.put("status", item.getStatus());
        payload.put("version", item.getVersionNo());
        payload.put("content", readMap(item.getContentJson()));
        payload.put("attachments", readMapList(item.getAttachmentsJson()));
        payload.put("teacherComment", item.getTeacherComment());
        payload.put("collegeComment", item.getCollegeComment());
        payload.put("score", item.getScore());
        payload.put("createdAt", item.getCreatedAt());
        payload.put("updatedAt", item.getUpdatedAt());
        payload.put("submittedAt", item.getSubmittedAt());
        payload.put("teacherReviewedAt", item.getTeacherReviewedAt());
        payload.put("collegeReviewedAt", item.getCollegeReviewedAt());
        payload.put("history", readMapList(item.getHistoryJson()));
        return payload;
    }

    private Map<String, Object> toMessagePayload(MessageNoticeEntity item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", item.getId());
        payload.put("type", item.getType());
        payload.put("title", item.getTitle());
        payload.put("content", item.getContent());
        payload.put("link", item.getLink());
        payload.put("read", item.getReadFlag());
        payload.put("createdAt", item.getCreatedAt());
        return payload;
    }

    private Map<String, Object> toAuditPayload(AuditLogEntity item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", item.getId());
        payload.put("type", item.getType());
        payload.put("operatorId", item.getOperatorId());
        payload.put("action", item.getAction());
        payload.put("detail", item.getDetail());
        payload.put("createdAt", item.getCreatedAt());
        return payload;
    }

    private Map<String, Object> toStudentSimple(StudentEntity item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", item.getId());
        payload.put("name", item.getName());
        payload.put("studentNo", item.getStudentNo());
        payload.put("major", item.getMajor());
        payload.put("className", item.getClassName());
        payload.put("internshipType", item.getInternshipType());
        payload.put("internshipStatus", item.getInternshipStatus());
        return payload;
    }


    private void createMessage(String userId, String type, String title, String content, String link) {
        MessageNoticeEntity message = new MessageNoticeEntity();
        message.setId(IdGenerator.nextId("message"));
        message.setUserId(userId);
        message.setType(type);
        message.setTitle(title);
        message.setContent(content);
        message.setLink(link);
        message.setReadFlag(false);
        message.setCreatedAt(LocalDateTime.now());
        messageNoticeMapper.insert(message);
    }

    private void insertAudit(String type, String operatorId, String action, String detail) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setId(IdGenerator.nextId("log"));
        entity.setType(type);
        entity.setOperatorId(operatorId);
        entity.setAction(action);
        entity.setDetail(detail);
        entity.setCreatedAt(LocalDateTime.now());
        auditLogMapper.insert(entity);
    }

    private void requireRole(LoginUser loginUser, RoleType... allowed) {
        Set<String> roles = Arrays.stream(allowed).map(Enum::name).collect(Collectors.toSet());
        if (!roles.contains(loginUser.role())) {
            throw new BizException("当前角色无权访问该资源");
        }
    }

    private UserAccountEntity requireUser(String userId) {
        UserAccountEntity user = userAccountMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        return user;
    }

    private StudentEntity requireStudentByUser(String userId) {
        StudentEntity student = studentMapper.selectOne(Wrappers.<StudentEntity>lambdaQuery().eq(StudentEntity::getUserId, userId));
        if (student == null) {
            throw new BizException("学生信息不存在");
        }
        return student;
    }

    private TeacherEntity requireTeacherByUser(String userId) {
        TeacherEntity teacher = teacherMapper.selectOne(Wrappers.<TeacherEntity>lambdaQuery().eq(TeacherEntity::getUserId, userId));
        if (teacher == null) {
            throw new BizException("教师信息不存在");
        }
        return teacher;
    }

    private StudentEntity requireStudent(String studentId) {
        StudentEntity student = studentMapper.selectById(studentId);
        if (student == null) {
            throw new BizException("学生不存在");
        }
        return student;
    }

    private TeacherEntity requireTeacher(String teacherId) {
        TeacherEntity teacher = teacherMapper.selectById(teacherId);
        if (teacher == null) {
            throw new BizException("教师不存在");
        }
        return teacher;
    }

    private OrganizationEntity requireOrganization(String organizationId) {
        OrganizationEntity entity = organizationMapper.selectById(organizationId);
        if (entity == null) {
            throw new BizException("实习单位不存在");
        }
        return entity;
    }

    private MentorApplicationEntity requireMentorApplication(String applicationId) {
        MentorApplicationEntity entity = mentorApplicationMapper.selectById(applicationId);
        if (entity == null) {
            throw new BizException("指导申请不存在");
        }
        return entity;
    }

    private void ensureMentorTeacherReviewable(MentorApplicationEntity entity) {
        if (!MentorApplicationStatus.PENDING_TEACHER.getLabel().equals(entity.getStatus())) {
            throw new BizException("当前状态不可由教师处理");
        }
    }

    private void ensureMentorCollegeReviewable(LoginUser loginUser, MentorApplicationEntity entity) {
        TeacherEntity teacher = requireTeacher(entity.getTeacherId());
        if (!Objects.equals(teacher.getCollegeId(), loginUser.collegeId())) {
            throw new BizException("无权处理该指导申请");
        }
        if (!MentorApplicationStatus.PENDING_COLLEGE.getLabel().equals(entity.getStatus())) {
            throw new BizException("当前状态不可由学院复核");
        }
    }

    private InternshipApplicationEntity requireInternshipApplication(String applicationId) {
        InternshipApplicationEntity entity = internshipApplicationMapper.selectById(applicationId);
        if (entity == null) {
            throw new BizException("实习申请不存在");
        }
        return entity;
    }

    private void ensureInternshipReviewable(LoginUser loginUser, InternshipApplicationEntity entity) {
        StudentEntity student = requireStudent(entity.getStudentId());
        if (!Objects.equals(student.getCollegeId(), loginUser.collegeId())) {
            throw new BizException("无权审批该实习申请");
        }
        if (!InternshipApplicationStatus.PENDING_COLLEGE.getLabel().equals(entity.getStatus())) {
            throw new BizException("当前状态不可重复审批");
        }
    }

    private FormTemplateEntity requireTemplate(String code) {
        FormTemplateEntity template = formTemplateMapper.selectById(code);
        if (template == null) {
            throw new BizException("表单模板不存在");
        }
        return template;
    }

    private FormInstanceEntity requireForm(String formId) {
        FormInstanceEntity entity = formInstanceMapper.selectById(formId);
        if (entity == null) {
            throw new BizException("表单不存在");
        }
        return entity;
    }

    private void ensureStudentCanEditForm(FormInstanceEntity entity) {
        if (!Set.of(
                FormStatus.DRAFT.getLabel(),
                FormStatus.TEACHER_RETURNED.getLabel(),
                FormStatus.COLLEGE_RETURNED.getLabel()
        ).contains(entity.getStatus())) {
            throw new BizException("当前状态不可修改表单");
        }
    }

    private void ensureTeacherCanReviewForm(FormInstanceEntity entity) {
        if (!FormStatus.TEACHER_REVIEWING.getLabel().equals(entity.getStatus())) {
            throw new BizException("当前状态不可由教师审核");
        }
    }

    private void ensureCollegeAdminFormAccess(LoginUser loginUser, FormInstanceEntity entity) {
        StudentEntity student = requireStudent(entity.getStudentId());
        if (!Objects.equals(student.getCollegeId(), loginUser.collegeId())) {
            throw new BizException("无权处理该表单");
        }
    }

    private void ensureCollegeCanReviewForm(FormInstanceEntity entity) {
        if (!FormStatus.COLLEGE_REVIEWING.getLabel().equals(entity.getStatus())) {
            throw new BizException("当前状态不可由学院处理");
        }
    }

    private void ensureEvaluationConfirmable(EvaluationRecordEntity entity) {
        if (!Boolean.TRUE.equals(entity.getSubmittedToCollege())) {
            throw new BizException("教师尚未提交评价，当前不可确认");
        }
        if (Boolean.TRUE.equals(entity.getConfirmedByCollege())) {
            throw new BizException("该评价已完成学院确认");
        }
    }

    private void validateReviewScore(Integer score) {
        if (score != null && (score < 0 || score > 100)) {
            throw new BizException("归档评分需在 0 到 100 之间");
        }
    }

    private void applyCollegeReview(FormInstanceEntity entity, Boolean approved, Integer score, String comment) {
        validateReviewScore(score);
        entity.setCollegeComment(Optional.ofNullable(comment).orElse(""));
        if (score != null) {
            entity.setScore(score);
        }
        entity.setCollegeReviewedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setStatus(Boolean.TRUE.equals(approved) ? FormStatus.ARCHIVED.getLabel() : FormStatus.COLLEGE_RETURNED.getLabel());
    }

    private void notifyStudentAfterCollegeReview(FormInstanceEntity entity, Boolean approved, String comment) {
        StudentEntity student = requireStudent(entity.getStudentId());
        createMessage(
                requireUser(student.getUserId()).getId(),
                Boolean.TRUE.equals(approved) ? "审核结果" : "退回通知",
                entity.getTemplateName() + (Boolean.TRUE.equals(approved) ? "已归档" : "被学院退回"),
                Optional.ofNullable(comment).orElse("请查看学院处理意见。"),
                "/student/tasks"
        );
    }

    private void applyTeacherEvaluation(EvaluationRecordEntity entity, Requests.EvaluationSaveRequest request) {
        if (request.finalScore() != null && (request.finalScore() < 0 || request.finalScore() > 100)) {
            throw new BizException("建议成绩需在 0 到 100 之间");
        }
        entity.setStageComment(Optional.ofNullable(request.stageComment()).orElse(""));
        entity.setSummaryComment(Optional.ofNullable(request.summaryComment()).orElse(""));
        entity.setFinalScore(Optional.ofNullable(request.finalScore()).orElse(90));
        entity.setDimensionScoresJson(writeJson(normalizeEvaluationDimensions(request.dimensionScores())));
        entity.setStrengthsComment(Optional.ofNullable(request.strengthsComment()).orElse(""));
        entity.setImprovementComment(Optional.ofNullable(request.improvementComment()).orElse(""));
        entity.setCollegeComment("");
        entity.setCollegeScore(null);
        entity.setSubmittedToCollege(true);
        entity.setConfirmedByCollege(false);
        entity.setEvaluatedAt(LocalDateTime.now());
        entity.setCollegeConfirmedAt(null);
    }

    private List<Map<String, Object>> normalizeEvaluationDimensions(List<Map<String, Object>> dimensionScores) {
        List<Map<String, Object>> rawList = (dimensionScores == null || dimensionScores.isEmpty()) ? defaultEvaluationDimensions() : dimensionScores;
        List<Map<String, Object>> normalized = new ArrayList<>();
        Set<String> keys = new HashSet<>();

        for (Map<String, Object> item : rawList) {
            String key = Optional.ofNullable(item.get("key")).map(String::valueOf).map(String::trim).orElse("");
            String label = Optional.ofNullable(item.get("label")).map(String::valueOf).map(String::trim).orElse("");
            String comment = Optional.ofNullable(item.get("comment")).map(String::valueOf).map(String::trim).orElse("");
            Integer score = parseInteger(item.get("score"));
            if (key.isBlank() || label.isBlank()) {
                throw new BizException("评价维度编码和名称不能为空");
            }
            if (!keys.add(key)) {
                throw new BizException("评价维度不能重复");
            }
            if (score == null || score < 0 || score > 100) {
                throw new BizException("评价维度分数需在 0 到 100 之间");
            }
            Map<String, Object> normalizedItem = new LinkedHashMap<>();
            normalizedItem.put("key", key);
            normalizedItem.put("label", label);
            normalizedItem.put("score", score);
            normalizedItem.put("comment", comment);
            normalized.add(normalizedItem);
        }
        return normalized;
    }

    private List<Map<String, Object>> defaultEvaluationDimensions() {
        return List.of(
                new LinkedHashMap<>(Map.of("key", "ethics", "label", "职业素养", "score", 90, "comment", "")),
                new LinkedHashMap<>(Map.of("key", "teaching", "label", "教学实施", "score", 90, "comment", "")),
                new LinkedHashMap<>(Map.of("key", "management", "label", "班级管理", "score", 90, "comment", "")),
                new LinkedHashMap<>(Map.of("key", "reflection", "label", "反思改进", "score", 90, "comment", ""))
        );
    }

    private Integer parseInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception exception) {
            return null;
        }
    }

    private void notifyCollegeForEvaluation(EvaluationRecordEntity entity) {
        StudentEntity student = requireStudent(entity.getStudentId());
        UserAccountEntity collegeAdmin = userAccountMapper.selectOne(
                Wrappers.<UserAccountEntity>lambdaQuery()
                        .eq(UserAccountEntity::getRole, RoleType.COLLEGE_ADMIN.name())
                        .eq(UserAccountEntity::getCollegeId, student.getCollegeId())
                        .last("limit 1")
        );
        if (collegeAdmin != null) {
            createMessage(collegeAdmin.getId(), "待办提醒", student.getName() + " 的实习评价待学院确认", "请确认最终成绩与评价意见。", "/college/evaluations");
        }
    }

    private TeacherEntity currentEffectiveTeacher(String studentId) {
        MentorApplicationEntity relation = mentorApplicationMapper.selectOne(
                Wrappers.<MentorApplicationEntity>lambdaQuery()
                        .eq(MentorApplicationEntity::getStudentId, studentId)
                        .eq(MentorApplicationEntity::getStatus, MentorApplicationStatus.EFFECTIVE.getLabel())
                        .orderByDesc(MentorApplicationEntity::getEffectiveAt)
                        .last("limit 1")
        );
        return relation == null ? null : teacherMapper.selectById(relation.getTeacherId());
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return LocalDateTime.now();
        }
        String normalized = value.contains("T") ? value : value.replace(" ", "T");
        return LocalDateTime.parse(normalized, DateTimeFormatter.ISO_DATE_TIME);
    }

    private String toMonthKey(LocalDateTime value, DateTimeFormatter formatter) {
        return value == null ? "" : value.format(formatter);
    }

    private List<FormTemplateEntity> listAllTemplates() {
        return formTemplateMapper.selectList(
                Wrappers.<FormTemplateEntity>lambdaQuery()
                        .orderByAsc(FormTemplateEntity::getSortNo)
                        .orderByAsc(FormTemplateEntity::getCode)
        );
    }

    private boolean isTemplateEnabled(FormTemplateEntity entity) {
        return !Boolean.FALSE.equals(entity.getEnabled());
    }

    private void applyTemplateConfig(FormTemplateEntity entity,
                                     String name,
                                     String category,
                                     String description,
                                     List<String> applicableTypes,
                                     List<Map<String, Object>> fieldSchema,
                                     Boolean enabled,
                                     Integer sortNo) {
        if (name == null || name.isBlank()) {
            throw new BizException("模板名称不能为空");
        }
        if (category == null || category.isBlank()) {
            throw new BizException("模板分类不能为空");
        }
        if (!Set.of("COMMON", "TEACHING", "HEAD_TEACHER").contains(category)) {
            throw new BizException("模板分类不合法");
        }
        if (applicableTypes == null || applicableTypes.isEmpty()) {
            throw new BizException("适用实习类型不能为空");
        }
        if (fieldSchema == null || fieldSchema.isEmpty()) {
            throw new BizException("字段配置不能为空");
        }

        List<String> normalizedTypes = applicableTypes.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
        if (normalizedTypes.isEmpty() || normalizedTypes.stream().anyMatch(item -> !Set.of("TEACHING", "HEAD_TEACHER").contains(item))) {
            throw new BizException("适用实习类型不合法");
        }

        List<Map<String, Object>> normalizedFields = normalizeTemplateFields(fieldSchema);
        entity.setName(name.trim());
        entity.setCategory(category.trim());
        entity.setDescription(Optional.ofNullable(description).map(String::trim).orElse(""));
        entity.setApplicableTypesJson(writeJson(normalizedTypes));
        entity.setFieldSchemaJson(writeJson(normalizedFields));
        entity.setEnabled(!Boolean.FALSE.equals(enabled));
        entity.setSortNo(Optional.ofNullable(sortNo).orElse(100));
        entity.setUpdatedAt(LocalDateTime.now());
    }

    private List<Map<String, Object>> normalizeTemplateFields(List<Map<String, Object>> fieldSchema) {
        List<Map<String, Object>> normalized = new ArrayList<>();
        Set<String> keys = new HashSet<>();

        for (Map<String, Object> field : fieldSchema) {
            String key = Optional.ofNullable(field.get("key")).map(String::valueOf).map(String::trim).orElse("");
            String label = Optional.ofNullable(field.get("label")).map(String::valueOf).map(String::trim).orElse("");
            String type = Optional.ofNullable(field.get("type")).map(String::valueOf).map(String::trim).orElse("text");
            String placeholder = Optional.ofNullable(field.get("placeholder")).map(String::valueOf).map(String::trim).orElse("");
            boolean required = Boolean.parseBoolean(String.valueOf(Optional.ofNullable(field.get("required")).orElse(false)));

            if (key.isBlank() || !key.matches("[a-zA-Z][a-zA-Z0-9_]{1,31}")) {
                throw new BizException("字段编码格式不合法");
            }
            if (label.isBlank()) {
                throw new BizException("字段名称不能为空");
            }
            if (!Set.of("text", "textarea", "date").contains(type)) {
                throw new BizException("字段类型仅支持 text、textarea、date");
            }
            if (!keys.add(key)) {
                throw new BizException("字段编码不能重复");
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", key);
            item.put("label", label);
            item.put("type", type);
            item.put("required", required);
            item.put("placeholder", placeholder);
            normalized.add(item);
        }

        return normalized;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new BizException("JSON 序列化失败");
        }
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception exception) {
            return new LinkedHashMap<>();
        }
    }

    private List<Map<String, Object>> readMapList(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception exception) {
            return new ArrayList<>();
        }
    }

    private List<String> readList(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception exception) {
            return new ArrayList<>();
        }
    }
}
