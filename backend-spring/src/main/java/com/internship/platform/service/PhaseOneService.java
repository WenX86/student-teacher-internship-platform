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
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PhaseOneService {

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
        List<FormTemplateEntity> templates = formTemplateMapper.selectList(Wrappers.<FormTemplateEntity>lambdaQuery());
        if (RoleType.STUDENT.name().equals(loginUser.role())) {
            String internshipType = requireStudentByUser(loginUser.id()).getInternshipType();
            return templates.stream()
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

        List<Map<String, Object>> history = readMapList(entity.getHistoryJson());
        history.add(Map.of(
                "version", entity.getVersionNo(),
                "status", entity.getStatus(),
                "content", readMap(entity.getContentJson()),
                "updatedAt", Optional.ofNullable(entity.getUpdatedAt()).map(LocalDateTime::toString).orElse("")
        ));

        entity.setVersionNo(entity.getVersionNo() + 1);
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
        entity.setCollegeComment(Optional.ofNullable(request.comment()).orElse(""));
        entity.setCollegeReviewedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setStatus(Boolean.TRUE.equals(request.approved()) ? FormStatus.ARCHIVED.getLabel() : FormStatus.COLLEGE_RETURNED.getLabel());
        formInstanceMapper.updateById(entity);

        StudentEntity student = requireStudent(entity.getStudentId());
        createMessage(requireUser(student.getUserId()).getId(), Boolean.TRUE.equals(request.approved()) ? "审核结果" : "退回通知", entity.getTemplateName() + (Boolean.TRUE.equals(request.approved()) ? "已归档" : "被学院退回"), Optional.ofNullable(request.comment()).orElse("请查看学院处理意见。"), "/student/tasks");
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
            payload.put("studentId", item.getStudentId());
            payload.put("stageComment", item.getStageComment());
            payload.put("summaryComment", item.getSummaryComment());
            payload.put("finalScore", item.getFinalScore());
            payload.put("submittedToCollege", item.getSubmittedToCollege());
            payload.put("confirmedByCollege", item.getConfirmedByCollege());
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
            entity.setConfirmedByCollege(false);
            entity.setSubmittedToCollege(true);
            entity.setStageComment(request.stageComment());
            entity.setSummaryComment(request.summaryComment());
            entity.setFinalScore(request.finalScore());
            evaluationRecordMapper.insert(entity);
            return;
        }
        entity.setStageComment(request.stageComment());
        entity.setSummaryComment(request.summaryComment());
        entity.setFinalScore(request.finalScore());
        entity.setSubmittedToCollege(true);
        evaluationRecordMapper.updateById(entity);
    }

    public Map<String, Object> reportSummary(LoginUser loginUser) {
        requireRole(loginUser, RoleType.COLLEGE_ADMIN, RoleType.SUPER_ADMIN);
        List<StudentEntity> students = RoleType.COLLEGE_ADMIN.name().equals(loginUser.role())
                ? studentMapper.selectList(Wrappers.<StudentEntity>lambdaQuery().eq(StudentEntity::getCollegeId, loginUser.collegeId()))
                : studentMapper.selectList(Wrappers.<StudentEntity>lambdaQuery());
        Set<String> studentIds = students.stream().map(StudentEntity::getId).collect(Collectors.toSet());
        List<TeacherEntity> teachers = RoleType.COLLEGE_ADMIN.name().equals(loginUser.role())
                ? teacherMapper.selectList(Wrappers.<TeacherEntity>lambdaQuery().eq(TeacherEntity::getCollegeId, loginUser.collegeId()))
                : teacherMapper.selectList(Wrappers.<TeacherEntity>lambdaQuery());
        List<FormInstanceEntity> forms = studentIds.isEmpty() ? List.of() : formInstanceMapper.selectList(
                Wrappers.<FormInstanceEntity>lambdaQuery().in(FormInstanceEntity::getStudentId, studentIds)
        );

        Map<String, Object> studentsPayload = new LinkedHashMap<>();
        studentsPayload.put("total", students.size());
        studentsPayload.put("applied", studentIds.isEmpty() ? 0 : internshipApplicationMapper.selectCount(Wrappers.<InternshipApplicationEntity>lambdaQuery().in(InternshipApplicationEntity::getStudentId, studentIds)));
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

    public List<CollegeApplicationEntity> collegeApplications(LoginUser loginUser) {
        requireRole(loginUser, RoleType.SUPER_ADMIN);
        return collegeApplicationMapper.selectList(Wrappers.<CollegeApplicationEntity>lambdaQuery().orderByDesc(CollegeApplicationEntity::getCreatedAt));
    }

    @Transactional
    public void reviewCollegeApplication(LoginUser loginUser, String applicationId, Requests.DecisionRequest request) {
        requireRole(loginUser, RoleType.SUPER_ADMIN);
        CollegeApplicationEntity entity = collegeApplicationMapper.selectById(applicationId);
        if (entity == null) {
            throw new BizException("入驻申请不存在");
        }
        entity.setStatus(Boolean.TRUE.equals(request.approved()) ? "已通过" : "已驳回");
        entity.setReviewComment(Optional.ofNullable(request.comment()).orElse(""));
        collegeApplicationMapper.updateById(entity);
    }

    public Map<String, Object> basicData(LoginUser loginUser) {
        requireRole(loginUser, RoleType.SUPER_ADMIN);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("colleges", collegeMapper.selectList(Wrappers.<CollegeEntity>lambdaQuery()));
        payload.put("roles", Arrays.stream(RoleType.values()).map(Enum::name).toList());
        payload.put("formStatuses", Arrays.stream(FormStatus.values()).map(FormStatus::getLabel).toList());
        return payload;
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
        payload.put("applicableTypes", readList(item.getApplicableTypesJson()));
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

    private Map<String, Object> toTeacherSimple(TeacherEntity item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", item.getId());
        payload.put("name", item.getName());
        payload.put("employeeNo", item.getEmployeeNo());
        payload.put("department", item.getDepartment());
        payload.put("phone", item.getPhone());
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

    private InternshipApplicationEntity requireInternshipApplication(String applicationId) {
        InternshipApplicationEntity entity = internshipApplicationMapper.selectById(applicationId);
        if (entity == null) {
            throw new BizException("实习申请不存在");
        }
        return entity;
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
