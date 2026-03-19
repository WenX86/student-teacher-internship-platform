package com.internship.platform.controller;

import com.internship.platform.common.ApiResponse;
import com.internship.platform.dto.Requests;
import com.internship.platform.entity.CollegeApplicationEntity;
import com.internship.platform.entity.OrganizationEntity;
import com.internship.platform.service.PhaseOneService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class PhaseOneController {

    private final PhaseOneService phaseOneService;

    public PhaseOneController(PhaseOneService phaseOneService) {
        this.phaseOneService = phaseOneService;
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

    @PostMapping("/messages/{id}/read")
    public ApiResponse<Void> markMessageRead(@PathVariable String id) {
        phaseOneService.markMessageRead(phaseOneService.currentLoginUser(), id);
        return ApiResponse.ok();
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

    @GetMapping("/reports/summary")
    public ApiResponse<Map<String, Object>> reportSummary() {
        return ApiResponse.ok(phaseOneService.reportSummary(phaseOneService.currentLoginUser()));
    }

    @GetMapping("/admin/college-applications")
    public ApiResponse<List<CollegeApplicationEntity>> collegeApplications() {
        return ApiResponse.ok(phaseOneService.collegeApplications(phaseOneService.currentLoginUser()));
    }

    @PostMapping("/admin/college-applications/{id}/review")
    public ApiResponse<Void> reviewCollegeApplication(@PathVariable String id, @Valid @RequestBody Requests.DecisionRequest request) {
        phaseOneService.reviewCollegeApplication(phaseOneService.currentLoginUser(), id, request);
        return ApiResponse.ok();
    }

    @GetMapping("/admin/basic-data")
    public ApiResponse<Map<String, Object>> basicData() {
        return ApiResponse.ok(phaseOneService.basicData(phaseOneService.currentLoginUser()));
    }

    @GetMapping("/admin/logs")
    public ApiResponse<List<Map<String, Object>>> logs() {
        return ApiResponse.ok(phaseOneService.logs(phaseOneService.currentLoginUser()));
    }
}
