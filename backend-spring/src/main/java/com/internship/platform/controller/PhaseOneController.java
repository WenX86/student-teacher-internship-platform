package com.internship.platform.controller;

import com.internship.platform.common.ApiResponse;
import com.internship.platform.dto.Requests;
import com.internship.platform.entity.CollegeApplicationEntity;
import com.internship.platform.entity.OrganizationEntity;
import com.internship.platform.service.BatchImportService;
import com.internship.platform.service.PhaseOneService;
import jakarta.validation.Valid;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class PhaseOneController {

    private final PhaseOneService phaseOneService;
    private final BatchImportService batchImportService;

    public PhaseOneController(PhaseOneService phaseOneService, BatchImportService batchImportService) {
        this.phaseOneService = phaseOneService;
        this.batchImportService = batchImportService;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok(Map.of("ok", true, "message", "Spring Boot 一期后端运行中"));
    }

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard() {
        return ApiResponse.ok(phaseOneService.dashboard(phaseOneService.currentLoginUser()));
    }

    @GetMapping("/messages")
    public ApiResponse<List<Map<String, Object>>> messages() {
        return ApiResponse.ok(phaseOneService.messages(phaseOneService.currentLoginUser()));
    }

    @GetMapping("/risk-alerts")
    public ApiResponse<List<Map<String, Object>>> riskAlerts() {
        return ApiResponse.ok(phaseOneService.riskAlerts(phaseOneService.currentLoginUser()));
    }

    @PostMapping("/risk-alerts/{id}/remind")
    public ApiResponse<Void> sendRiskReminder(@PathVariable String id) {
        phaseOneService.sendRiskReminder(phaseOneService.currentLoginUser(), id);
        return ApiResponse.ok();
    }

    @PostMapping("/messages/{id}/read")
    public ApiResponse<Void> markMessageRead(@PathVariable String id) {
        phaseOneService.markMessageRead(phaseOneService.currentLoginUser(), id);
        return ApiResponse.ok();
    }

    @PostMapping("/messages/read-all")
    public ApiResponse<Map<String, Object>> markAllMessagesRead() {
        long updatedCount = phaseOneService.markAllMessagesRead(phaseOneService.currentLoginUser());
        return ApiResponse.ok(Map.of("updatedCount", updatedCount));
    }

    @GetMapping("/students")
    public ApiResponse<List<Map<String, Object>>> students() {
        return ApiResponse.ok(phaseOneService.students(phaseOneService.currentLoginUser()));
    }

    @PostMapping("/students")
    public ApiResponse<Void> createStudent(@Valid @RequestBody Requests.StudentCreateRequest request) {
        phaseOneService.createStudent(phaseOneService.currentLoginUser(), request);
        return ApiResponse.ok();
    }

    @GetMapping("/students/import-template")
    public ResponseEntity<ByteArrayResource> downloadStudentImportTemplate() {
        return excelResponse(batchImportService.buildStudentTemplate(phaseOneService.currentLoginUser()), "student-import-template.xlsx");
    }

    @PostMapping("/students/import")
    public ApiResponse<Map<String, Object>> importStudents(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(batchImportService.importStudents(phaseOneService.currentLoginUser(), file));
    }

    @PostMapping("/students/{id}/reset-password")
    public ApiResponse<Void> resetStudentPassword(@PathVariable String id) {
        phaseOneService.resetStudentPassword(phaseOneService.currentLoginUser(), id);
        return ApiResponse.ok();
    }

    @PatchMapping("/students/{id}/status")
    public ApiResponse<Void> changeStudentStatus(@PathVariable String id, @RequestBody Map<String, String> body) {
        phaseOneService.changeStudentStatus(phaseOneService.currentLoginUser(), id, body.get("status"));
        return ApiResponse.ok();
    }

    @GetMapping("/teachers")
    public ApiResponse<List<Map<String, Object>>> teachers() {
        return ApiResponse.ok(phaseOneService.teachers(phaseOneService.currentLoginUser()));
    }

    @PostMapping("/teachers")
    public ApiResponse<Void> createTeacher(@Valid @RequestBody Requests.TeacherCreateRequest request) {
        phaseOneService.createTeacher(phaseOneService.currentLoginUser(), request);
        return ApiResponse.ok();
    }

    @GetMapping("/teachers/import-template")
    public ResponseEntity<ByteArrayResource> downloadTeacherImportTemplate() {
        return excelResponse(batchImportService.buildTeacherTemplate(phaseOneService.currentLoginUser()), "teacher-import-template.xlsx");
    }

    @PostMapping("/teachers/import")
    public ApiResponse<Map<String, Object>> importTeachers(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(batchImportService.importTeachers(phaseOneService.currentLoginUser(), file));
    }

    @PostMapping("/teachers/{id}/reset-password")
    public ApiResponse<Void> resetTeacherPassword(@PathVariable String id) {
        phaseOneService.resetTeacherPassword(phaseOneService.currentLoginUser(), id);
        return ApiResponse.ok();
    }

    @PatchMapping("/teachers/{id}/status")
    public ApiResponse<Void> changeTeacherStatus(@PathVariable String id, @RequestBody Map<String, String> body) {
        phaseOneService.changeTeacherStatus(phaseOneService.currentLoginUser(), id, body.get("status"));
        return ApiResponse.ok();
    }

    @GetMapping("/organizations")
    public ApiResponse<List<OrganizationEntity>> organizations() {
        return ApiResponse.ok(phaseOneService.organizations(phaseOneService.currentLoginUser()));
    }

    @PostMapping("/organizations")
    public ApiResponse<Void> createOrganization(@Valid @RequestBody Requests.OrganizationCreateRequest request) {
        phaseOneService.createOrganization(phaseOneService.currentLoginUser(), request);
        return ApiResponse.ok();
    }

    @PatchMapping("/organizations/{id}")
    public ApiResponse<Void> updateOrganization(@PathVariable String id, @Valid @RequestBody Requests.OrganizationCreateRequest request) {
        phaseOneService.updateOrganization(phaseOneService.currentLoginUser(), id, request);
        return ApiResponse.ok();
    }

    @GetMapping("/mentor-applications")
    public ApiResponse<List<Map<String, Object>>> mentorApplications() {
        return ApiResponse.ok(phaseOneService.mentorApplications(phaseOneService.currentLoginUser()));
    }

    @PostMapping("/mentor-applications")
    public ApiResponse<Void> createMentorApplication(@Valid @RequestBody Requests.MentorApplicationCreateRequest request) {
        phaseOneService.createMentorApplication(phaseOneService.currentLoginUser(), request);
        return ApiResponse.ok();
    }

    @PostMapping("/mentor-applications/{id}/teacher-review")
    public ApiResponse<Void> teacherReviewMentor(@PathVariable String id, @Valid @RequestBody Requests.DecisionRequest request) {
        phaseOneService.teacherReviewMentor(phaseOneService.currentLoginUser(), id, request);
        return ApiResponse.ok();
    }

    @PostMapping("/mentor-applications/{id}/college-review")
    public ApiResponse<Void> collegeReviewMentor(@PathVariable String id, @Valid @RequestBody Requests.DecisionRequest request) {
        phaseOneService.collegeReviewMentor(phaseOneService.currentLoginUser(), id, request);
        return ApiResponse.ok();
    }

    @GetMapping("/internship-applications")
    public ApiResponse<List<Map<String, Object>>> internshipApplications() {
        return ApiResponse.ok(phaseOneService.internshipApplications(phaseOneService.currentLoginUser()));
    }

    @PostMapping("/internship-applications")
    public ApiResponse<Void> createInternshipApplication(@Valid @RequestBody Requests.InternshipApplicationCreateRequest request) {
        phaseOneService.createInternshipApplication(phaseOneService.currentLoginUser(), request);
        return ApiResponse.ok();
    }

    @PostMapping("/internship-applications/{id}/review")
    public ApiResponse<Void> reviewInternship(@PathVariable String id, @Valid @RequestBody Requests.InternshipReviewRequest request) {
        phaseOneService.reviewInternshipApplication(phaseOneService.currentLoginUser(), id, request);
        return ApiResponse.ok();
    }

    @GetMapping("/form-templates")
    public ApiResponse<List<Map<String, Object>>> formTemplates() {
        return ApiResponse.ok(phaseOneService.formTemplates(phaseOneService.currentLoginUser()));
    }

    @GetMapping("/forms")
    public ApiResponse<List<Map<String, Object>>> forms(@RequestParam(required = false) String category) {
        return ApiResponse.ok(phaseOneService.forms(phaseOneService.currentLoginUser(), category));
    }

    @PostMapping("/forms")
    public ApiResponse<Void> createForm(@Valid @RequestBody Requests.FormSaveRequest request) {
        phaseOneService.createForm(phaseOneService.currentLoginUser(), request);
        return ApiResponse.ok();
    }

    @PutMapping("/forms/{id}")
    public ApiResponse<Void> updateForm(@PathVariable String id, @Valid @RequestBody Requests.FormSaveRequest request) {
        phaseOneService.updateForm(phaseOneService.currentLoginUser(), id, request);
        return ApiResponse.ok();
    }

    @PostMapping("/forms/{id}/modification-request")
    public ApiResponse<Void> requestFormModification(@PathVariable String id, @Valid @RequestBody Requests.FormModificationRequest request) {
        phaseOneService.requestFormModification(phaseOneService.currentLoginUser(), id, request);
        return ApiResponse.ok();
    }

    @PostMapping("/forms/{id}/modification-review")
    public ApiResponse<Void> reviewFormModification(@PathVariable String id, @Valid @RequestBody Requests.FormModificationReviewRequest request) {
        phaseOneService.reviewFormModification(phaseOneService.currentLoginUser(), id, request);
        return ApiResponse.ok();
    }

    @PostMapping("/forms/{id}/teacher-review")
    public ApiResponse<Void> teacherReviewForm(@PathVariable String id, @Valid @RequestBody Requests.FormReviewRequest request) {
        phaseOneService.teacherReviewForm(phaseOneService.currentLoginUser(), id, request);
        return ApiResponse.ok();
    }

    @PostMapping("/forms/{id}/college-review")
    public ApiResponse<Void> collegeReviewForm(@PathVariable String id, @Valid @RequestBody Requests.FormReviewRequest request) {
        phaseOneService.collegeReviewForm(phaseOneService.currentLoginUser(), id, request);
        return ApiResponse.ok();
    }

    @PostMapping("/forms/batch-college-review")
    public ApiResponse<Map<String, Object>> batchCollegeReviewForms(@Valid @RequestBody Requests.BatchFormReviewRequest request) {
        return ApiResponse.ok(phaseOneService.batchCollegeReviewForms(phaseOneService.currentLoginUser(), request));
    }

    @GetMapping("/guidance-records")
    public ApiResponse<List<Map<String, Object>>> guidanceRecords() {
        return ApiResponse.ok(phaseOneService.guidanceRecords(phaseOneService.currentLoginUser()));
    }

    @PostMapping("/guidance-records")
    public ApiResponse<Void> createGuidanceRecord(@Valid @RequestBody Requests.GuidanceRecordCreateRequest request) {
        phaseOneService.createGuidanceRecord(phaseOneService.currentLoginUser(), request);
        return ApiResponse.ok();
    }

    @GetMapping("/evaluations")
    public ApiResponse<List<Map<String, Object>>> evaluations() {
        return ApiResponse.ok(phaseOneService.evaluations(phaseOneService.currentLoginUser()));
    }

    @PostMapping("/evaluations")
    public ApiResponse<Void> saveEvaluation(@Valid @RequestBody Requests.EvaluationSaveRequest request) {
        phaseOneService.saveEvaluation(phaseOneService.currentLoginUser(), request);
        return ApiResponse.ok();
    }

    @PostMapping("/evaluations/{id}/college-confirm")
    public ApiResponse<Void> collegeConfirmEvaluation(@PathVariable String id, @Valid @RequestBody Requests.EvaluationCollegeConfirmRequest request) {
        phaseOneService.collegeConfirmEvaluation(phaseOneService.currentLoginUser(), id, request);
        return ApiResponse.ok();
    }

    @PostMapping("/evaluations/{id}/college-return")
    public ApiResponse<Void> collegeReturnEvaluation(@PathVariable String id, @Valid @RequestBody Requests.EvaluationCollegeReturnRequest request) {
        phaseOneService.collegeReturnEvaluation(phaseOneService.currentLoginUser(), id, request);
        return ApiResponse.ok();
    }

    @PostMapping("/evaluations/batch-college-confirm")
    public ApiResponse<Map<String, Object>> batchCollegeConfirmEvaluations(@Valid @RequestBody Requests.BatchEvaluationCollegeConfirmRequest request) {
        return ApiResponse.ok(phaseOneService.batchCollegeConfirmEvaluations(phaseOneService.currentLoginUser(), request));
    }

    @PostMapping("/evaluations/batch-college-return")
    public ApiResponse<Map<String, Object>> batchCollegeReturnEvaluations(@Valid @RequestBody Requests.BatchEvaluationCollegeReturnRequest request) {
        return ApiResponse.ok(phaseOneService.batchCollegeReturnEvaluations(phaseOneService.currentLoginUser(), request));
    }

    @GetMapping("/reports/summary")
    public ApiResponse<Map<String, Object>> reportSummary() {
        return ApiResponse.ok(phaseOneService.reportSummary(phaseOneService.currentLoginUser()));
    }

    @GetMapping("/reports/center")
    public ApiResponse<Map<String, Object>> reportCenter() {
        return ApiResponse.ok(phaseOneService.reportCenter(phaseOneService.currentLoginUser()));
    }

    @GetMapping("/admin/college-applications")
    public ApiResponse<List<CollegeApplicationEntity>> collegeApplications() {
        return ApiResponse.ok(phaseOneService.collegeApplications(phaseOneService.currentLoginUser()));
    }

    @PostMapping("/admin/college-applications/{id}/review")
    public ApiResponse<Map<String, Object>> reviewCollegeApplication(@PathVariable String id, @Valid @RequestBody Requests.DecisionRequest request) {
        return ApiResponse.ok(phaseOneService.reviewCollegeApplication(phaseOneService.currentLoginUser(), id, request));
    }

    @GetMapping("/admin/basic-data")
    public ApiResponse<Map<String, Object>> basicData() {
        return ApiResponse.ok(phaseOneService.basicData(phaseOneService.currentLoginUser()));
    }

    @PostMapping("/admin/colleges")
    public ApiResponse<Map<String, Object>> createCollege(@Valid @RequestBody Requests.CollegeCreateRequest request) {
        return ApiResponse.ok(phaseOneService.createCollege(phaseOneService.currentLoginUser(), request));
    }

    @GetMapping("/admin/college-admins")
    public ApiResponse<List<Map<String, Object>>> collegeAdmins() {
        return ApiResponse.ok(phaseOneService.collegeAdmins(phaseOneService.currentLoginUser()));
    }

    @PostMapping("/admin/college-admins")
    public ApiResponse<Map<String, Object>> createCollegeAdmin(@Valid @RequestBody Requests.CollegeAdminCreateRequest request) {
        return ApiResponse.ok(phaseOneService.createCollegeAdmin(phaseOneService.currentLoginUser(), request));
    }

    @PostMapping("/admin/college-admins/{id}/reset-password")
    public ApiResponse<Void> resetCollegeAdminPassword(@PathVariable String id) {
        phaseOneService.resetCollegeAdminPassword(phaseOneService.currentLoginUser(), id);
        return ApiResponse.ok();
    }

    @PatchMapping("/admin/college-admins/{id}/status")
    public ApiResponse<Void> changeCollegeAdminStatus(@PathVariable String id, @RequestBody Map<String, String> body) {
        phaseOneService.changeCollegeAdminStatus(phaseOneService.currentLoginUser(), id, body.get("status"));
        return ApiResponse.ok();
    }

    @GetMapping("/admin/system-settings")
    public ApiResponse<List<Map<String, Object>>> systemSettings() {
        return ApiResponse.ok(phaseOneService.systemSettings(phaseOneService.currentLoginUser()));
    }

    @PutMapping("/admin/system-settings")
    public ApiResponse<Void> saveSystemSettings(@Valid @RequestBody Requests.SystemSettingSaveRequest request) {
        phaseOneService.saveSystemSettings(phaseOneService.currentLoginUser(), request);
        return ApiResponse.ok();
    }

    @GetMapping("/admin/form-templates")
    public ApiResponse<List<Map<String, Object>>> adminFormTemplates() {
        return ApiResponse.ok(phaseOneService.adminFormTemplates(phaseOneService.currentLoginUser()));
    }

    @PostMapping("/admin/form-templates")
    public ApiResponse<Void> createFormTemplate(@Valid @RequestBody Requests.FormTemplateCreateRequest request) {
        phaseOneService.createFormTemplate(phaseOneService.currentLoginUser(), request);
        return ApiResponse.ok();
    }

    @PutMapping("/admin/form-templates/{code}")
    public ApiResponse<Void> updateFormTemplate(@PathVariable String code, @Valid @RequestBody Requests.FormTemplateUpdateRequest request) {
        phaseOneService.updateFormTemplate(phaseOneService.currentLoginUser(), code, request);
        return ApiResponse.ok();
    }

    @PatchMapping("/admin/form-templates/{code}/status")
    public ApiResponse<Void> changeFormTemplateStatus(@PathVariable String code, @Valid @RequestBody Requests.StatusToggleRequest request) {
        phaseOneService.changeFormTemplateStatus(phaseOneService.currentLoginUser(), code, request);
        return ApiResponse.ok();
    }

    @GetMapping("/admin/logs")
    public ApiResponse<List<Map<String, Object>>> logs() {
        return ApiResponse.ok(phaseOneService.logs(phaseOneService.currentLoginUser()));
    }
    private ResponseEntity<ByteArrayResource> excelResponse(byte[] bytes, String fileName) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(fileName, StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .contentLength(bytes.length)
                .body(new ByteArrayResource(bytes));
    }
}
