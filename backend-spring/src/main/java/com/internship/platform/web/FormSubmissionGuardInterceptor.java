package com.internship.platform.web;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.internship.platform.common.BizException;
import com.internship.platform.constant.InternshipApplicationStatus;
import com.internship.platform.constant.MentorApplicationStatus;
import com.internship.platform.constant.RoleType;
import com.internship.platform.entity.InternshipApplicationEntity;
import com.internship.platform.entity.MentorApplicationEntity;
import com.internship.platform.entity.StudentEntity;
import com.internship.platform.mapper.InternshipApplicationMapper;
import com.internship.platform.mapper.MentorApplicationMapper;
import com.internship.platform.mapper.StudentMapper;
import com.internship.platform.security.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class FormSubmissionGuardInterceptor implements HandlerInterceptor {

    private static final String FORMS_PATH = "/api/forms";
    private static final String FORMS_PREFIX = "/api/forms/";

    private final StudentMapper studentMapper;
    private final MentorApplicationMapper mentorApplicationMapper;
    private final InternshipApplicationMapper internshipApplicationMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!requiresGuard(request)) {
            return true;
        }

        LoginUser loginUser = currentLoginUser();
        if (loginUser == null || !RoleType.STUDENT.name().equals(loginUser.role())) {
            return true;
        }

        StudentEntity student = studentMapper.selectOne(
                Wrappers.<StudentEntity>lambdaQuery()
                        .eq(StudentEntity::getUserId, loginUser.id())
                        .last("limit 1")
        );
        if (student == null) {
            return true;
        }

        ensurePracticeContext(student);
        return true;
    }

    private boolean requiresGuard(HttpServletRequest request) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        return ("POST".equalsIgnoreCase(method) && FORMS_PATH.equals(uri))
                || ("PUT".equalsIgnoreCase(method) && uri != null && uri.startsWith(FORMS_PREFIX));
    }

    private LoginUser currentLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
            return null;
        }
        return loginUser;
    }

    private void ensurePracticeContext(StudentEntity student) {
        if (!hasEffectiveMentor(student.getId())) {
            throw new BizException("请先完成指导关系生效后再提交表单");
        }
        if (!hasApprovedInternship(student)) {
            throw new BizException("请先完成实习申请审批通过后再提交表单");
        }
    }

    private boolean hasEffectiveMentor(String studentId) {
        return mentorApplicationMapper.selectCount(
                Wrappers.<MentorApplicationEntity>lambdaQuery()
                        .eq(MentorApplicationEntity::getStudentId, studentId)
                        .eq(MentorApplicationEntity::getStatus, MentorApplicationStatus.EFFECTIVE.getLabel())
        ) > 0;
    }

    private boolean hasApprovedInternship(StudentEntity student) {
        return "实习中".equals(Optional.ofNullable(student.getInternshipStatus()).orElse(""))
                || internshipApplicationMapper.selectCount(
                Wrappers.<InternshipApplicationEntity>lambdaQuery()
                        .eq(InternshipApplicationEntity::getStudentId, student.getId())
                        .eq(InternshipApplicationEntity::getStatus, InternshipApplicationStatus.APPROVED.getLabel())
        ) > 0;
    }
}
