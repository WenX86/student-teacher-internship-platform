package com.internship.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.platform.constant.FormStatus;
import com.internship.platform.constant.InternshipApplicationStatus;
import com.internship.platform.constant.MentorApplicationStatus;
import com.internship.platform.entity.EvaluationRecordEntity;
import com.internship.platform.mapper.EvaluationRecordMapper;
import com.internship.platform.util.IdGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class StateFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EvaluationRecordMapper evaluationRecordMapper;

    @Test
    void shouldEnforceMentorApplicationReviewSequence() throws Exception {
        String teacherToken = login("T1002", "123456");
        String collegeToken = login("college01", "123456");

        mockMvc.perform(post("/api/mentor-applications/{id}/college-review", "mentor-app-002")
                        .header("Authorization", bearer(collegeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": true,
                                  "comment": "跳过教师确认直接复核"
                                }
                                """))
                .andExpect(status().isBadRequest());

        JsonNode pendingApplication = findById(listMentorApplications(collegeToken), "mentor-app-002");
        assertThat(pendingApplication.path("status").asText()).isEqualTo(MentorApplicationStatus.PENDING_TEACHER.getLabel());

        mockMvc.perform(post("/api/mentor-applications/{id}/teacher-review", "mentor-app-002")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": true,
                                  "comment": "同意接收"
                                }
                                """))
                .andExpect(status().isOk());

        JsonNode afterTeacherReview = findById(listMentorApplications(collegeToken), "mentor-app-002");
        assertThat(afterTeacherReview.path("status").asText()).isEqualTo(MentorApplicationStatus.PENDING_COLLEGE.getLabel());
        assertThat(afterTeacherReview.path("teacherReviewedAt").asText()).isNotBlank();

        mockMvc.perform(post("/api/mentor-applications/{id}/teacher-review", "mentor-app-002")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": false,
                                  "comment": "重复处理"
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/mentor-applications/{id}/college-review", "mentor-app-002")
                        .header("Authorization", bearer(collegeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": true,
                                  "comment": "学院复核通过"
                                }
                                """))
                .andExpect(status().isOk());

        JsonNode effectiveApplication = findById(listMentorApplications(collegeToken), "mentor-app-002");
        assertThat(effectiveApplication.path("status").asText()).isEqualTo(MentorApplicationStatus.EFFECTIVE.getLabel());
        assertThat(effectiveApplication.path("collegeReviewedAt").asText()).isNotBlank();
        assertThat(effectiveApplication.path("effectiveAt").asText()).isNotBlank();
    }

    @Test
    void shouldRequireEffectiveMentorBeforeCreatingAndReReviewingInternshipApplication() throws Exception {
        String studentToken = login("20230002", "123456");
        String collegeToken = login("college01", "123456");

        mockMvc.perform(post("/api/internship-applications")
                        .header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationId": "org-002",
                                  "batchName": "2026春季补测批次",
                                  "position": "班主任助理",
                                  "gradeTarget": "小学五年级",
                                  "startDate": "2026-03-25",
                                  "endDate": "2026-06-30",
                                  "remark": "先测无效指导关系拦截",
                                  "attachments": []
                                }
                                """))
                .andExpect(status().isBadRequest());

        makeMentorEffectiveForStudent002();

        mockMvc.perform(post("/api/internship-applications/{id}/review", "internship-app-002")
                        .header("Authorization", bearer(collegeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": true,
                                  "organizationConfirmation": "已确认接收",
                                  "organizationFeedback": "同意安排班主任实习",
                                  "receivedAt": "2026-03-18",
                                  "comment": "审核通过"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/internship-applications/{id}/review", "internship-app-002")
                        .header("Authorization", bearer(collegeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": false,
                                  "organizationConfirmation": "重复处理",
                                  "organizationFeedback": "不应再次驳回",
                                  "receivedAt": "2026-03-19",
                                  "comment": "重复审批"
                                }
                                """))
                .andExpect(status().isBadRequest());

        JsonNode application = findById(listInternshipApplications(collegeToken), "internship-app-002");
        assertThat(application.path("status").asText()).isEqualTo(InternshipApplicationStatus.APPROVED.getLabel());
        assertThat(application.path("reviewComment").asText()).isEqualTo("审核通过");
        assertThat(application.path("receivedAt").asText()).isNotBlank();

        JsonNode student = findByValue(listStudents(collegeToken), "studentNo", "20230002");
        assertThat(student.path("internshipStatus").asText()).isEqualTo("实习中");
    }
    @Test
    void shouldEnforceFormReviewAndEditStateFlow() throws Exception {
        String studentToken = login("20230002", "123456");
        String teacherToken = login("T1002", "123456");

        makeMentorEffectiveForStudent002();
        approveInternshipApplicationForStudent002();

        mockMvc.perform(post("/api/forms/{id}/teacher-review", "form-003")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": true,
                                  "score": 90,
                                  "comment": "草稿不能直接审核"
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/forms/{id}", "form-003")
                        .header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateCode": "class-duty-record",
                                  "content": {
                                    "title": "班主任值守记录第1版",
                                    "summary": "完成早读检查、班级巡查和纪律提醒"
                                  },
                                  "submit": true,
                                  "attachments": []
                                }
                                """))
                .andExpect(status().isOk());

        JsonNode submittedForm = findById(listForms(studentToken), "form-003");
        assertThat(submittedForm.path("status").asText()).isEqualTo(FormStatus.TEACHER_REVIEWING.getLabel());
        assertThat(submittedForm.path("version").asInt()).isEqualTo(2);

        mockMvc.perform(post("/api/forms/{id}/teacher-review", "form-003")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": true,
                                  "score": 91,
                                  "comment": "教师审核通过"
                                }
                                """))
                .andExpect(status().isOk());

        JsonNode collegeReviewingForm = findById(listForms(studentToken), "form-003");
        assertThat(collegeReviewingForm.path("status").asText()).isEqualTo(FormStatus.COLLEGE_REVIEWING.getLabel());
        assertThat(collegeReviewingForm.path("teacherReviewedAt").asText()).isNotBlank();

        mockMvc.perform(post("/api/forms/{id}/teacher-review", "form-003")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": false,
                                  "score": 80,
                                  "comment": "已归档后不能再审"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectRepeatedCollegeApplicationReview() throws Exception {
        String rootToken = login("root", "123456");

        mockMvc.perform(post("/api/admin/college-applications/{id}/review", "college-app-001")
                        .header("Authorization", bearer(rootToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": true,
                                  "comment": "首次审核通过"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/college-applications/{id}/review", "college-app-001")
                        .header("Authorization", bearer(rootToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": false,
                                  "comment": "不应允许重复审核"
                                }
                                """))
                .andExpect(status().isBadRequest());

        JsonNode application = findById(
                readData(
                        mockMvc.perform(get("/api/admin/college-applications")
                                        .header("Authorization", bearer(rootToken)))
                                .andExpect(status().isOk())
                                .andReturn()
                ),
                "college-app-001"
        );
        assertThat(application.path("status").asText()).isEqualTo("已通过");
    }

    @Test
    void shouldAllowStudentToResubmitFormAfterTeacherAndCollegeReturn() throws Exception {
        String studentToken = login("20230002", "123456");
        String teacherToken = login("T1002", "123456");
        String collegeToken = login("college01", "123456");

        makeMentorEffectiveForStudent002();
        approveInternshipApplicationForStudent002();

        mockMvc.perform(put("/api/forms/{id}", "form-003")
                        .header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateCode": "class-duty-record",
                                  "content": {
                                    "title": "班主任值守记录首次提交",
                                    "summary": "完成早读检查与学生纪律提醒"
                                  },
                                  "submit": true,
                                  "attachments": []
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/forms/{id}/teacher-review", "form-003")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": false,
                                  "score": 78,
                                  "comment": "请补充班级管理细节"
                                }
                                """))
                .andExpect(status().isOk());

        JsonNode teacherReturned = findById(listForms(studentToken), "form-003");
        assertThat(teacherReturned.path("status").asText()).isEqualTo(FormStatus.TEACHER_RETURNED.getLabel());
        assertThat(teacherReturned.path("teacherComment").asText()).contains("请补充班级管理细节");

        mockMvc.perform(put("/api/forms/{id}", "form-003")
                        .header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateCode": "class-duty-record",
                                  "content": {
                                    "title": "班主任值守记录教师退回后重提",
                                    "summary": "已补充学生管理、家校沟通与晚点名情况"
                                  },
                                  "submit": true,
                                  "attachments": []
                                }
                                """))
                .andExpect(status().isOk());

        JsonNode afterTeacherResubmit = findById(listForms(studentToken), "form-003");
        assertThat(afterTeacherResubmit.path("status").asText()).isEqualTo(FormStatus.TEACHER_REVIEWING.getLabel());
        assertThat(afterTeacherResubmit.path("version").asInt()).isEqualTo(3);
        assertThat(afterTeacherResubmit.path("history").size()).isEqualTo(2);

        mockMvc.perform(post("/api/forms/{id}/teacher-review", "form-003")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": true,
                                  "score": 90,
                                  "comment": "教师复核通过"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/forms/{id}/college-review", "form-003")
                        .header("Authorization", bearer(collegeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": false,
                                  "score": 82,
                                  "comment": "请补充学院要求的归档要点"
                                }
                                """))
                .andExpect(status().isOk());

        JsonNode collegeReturned = findById(listForms(studentToken), "form-003");
        assertThat(collegeReturned.path("status").asText()).isEqualTo(FormStatus.COLLEGE_RETURNED.getLabel());
        assertThat(collegeReturned.path("collegeComment").asText()).contains("请补充学院要求的归档要点");

        mockMvc.perform(put("/api/forms/{id}", "form-003")
                        .header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateCode": "class-duty-record",
                                  "content": {
                                    "title": "班主任值守记录学院退回后重提",
                                    "summary": "已补全归档要点与反思总结"
                                  },
                                  "submit": true,
                                  "attachments": []
                                }
                                """))
                .andExpect(status().isOk());

        JsonNode afterCollegeResubmit = findById(listForms(studentToken), "form-003");
        assertThat(afterCollegeResubmit.path("status").asText()).isEqualTo(FormStatus.TEACHER_REVIEWING.getLabel());
        assertThat(afterCollegeResubmit.path("version").asInt()).isEqualTo(4);
        assertThat(afterCollegeResubmit.path("history").size()).isEqualTo(3);
        assertThat(afterCollegeResubmit.path("teacherComment").asText()).isBlank();
        assertThat(afterCollegeResubmit.path("collegeComment").asText()).isBlank();
    }

    @Test
    void shouldAllowStudentToCreateNewInternshipApplicationAfterCollegeReturn() throws Exception {
        String studentToken = login("20230002", "123456");
        String collegeToken = login("college01", "123456");

        makeMentorEffectiveForStudent002();

        mockMvc.perform(post("/api/internship-applications/{id}/review", "internship-app-002")
                        .header("Authorization", bearer(collegeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": false,
                                  "organizationConfirmation": "材料待补充",
                                  "organizationFeedback": "请补充单位确认说明",
                                  "receivedAt": "2026-03-18",
                                  "comment": "学院退回后允许重新申请"
                                }
                                """))
                .andExpect(status().isOk());

        JsonNode rejectedApplication = findById(listInternshipApplications(studentToken), "internship-app-002");
        assertThat(rejectedApplication.path("status").asText()).isEqualTo(InternshipApplicationStatus.REJECTED.getLabel());

        mockMvc.perform(post("/api/internship-applications")
                        .header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationId": "org-002",
                                  "batchName": "2026春季重提批次",
                                  "position": "班主任助理",
                                  "gradeTarget": "小学五年级",
                                  "startDate": "2026-03-28",
                                  "endDate": "2026-06-30",
                                  "remark": "学院退回后重新提交",
                                  "attachments": []
                                }
                                """))
                .andExpect(status().isOk());

        JsonNode applications = listInternshipApplications(studentToken);
        JsonNode resubmittedApplication = findByValue(applications, "batchName", "2026春季重提批次");
        assertThat(resubmittedApplication.path("status").asText()).isEqualTo(InternshipApplicationStatus.PENDING_COLLEGE.getLabel());
        assertThat(applications.size()).isEqualTo(2);
    }

    @Test
    void shouldSkipInvalidRowsDuringBatchCollegeArchive() throws Exception {
        String studentToken = login("20230002", "123456");
        String teacherToken = login("T1002", "123456");
        String collegeToken = login("college01", "123456");

        makeMentorEffectiveForStudent002();
        approveInternshipApplicationForStudent002();

        mockMvc.perform(put("/api/forms/{id}", "form-003")
                        .header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateCode": "class-duty-record",
                                  "content": {
                                    "title": "批量归档测试表单",
                                    "summary": "推动到学院审核中"
                                  },
                                  "submit": true,
                                  "attachments": []
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/forms/{id}/teacher-review", "form-003")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": true,
                                  "score": 88,
                                  "comment": "允许进入学院归档"
                                }
                                """))
                .andExpect(status().isOk());

        JsonNode batchResult = readData(
                mockMvc.perform(post("/api/forms/batch-college-review")
                                .header("Authorization", bearer(collegeToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "formIds": ["form-003", "form-001", "form-002", "form-missing"],
                                          "approved": true,
                                          "score": 93,
                                          "comment": "批量归档测试"
                                        }
                                        """))
                        .andExpect(status().isOk())
                        .andReturn()
        );

        assertThat(batchResult.path("processedCount").asInt()).isEqualTo(1);
        assertThat(batchResult.path("archivedCount").asInt()).isEqualTo(1);
        assertThat(batchResult.path("returnedCount").asInt()).isZero();
        assertThat(batchResult.path("skipped").size()).isEqualTo(3);

        JsonNode form003 = findById(listForms(collegeToken), "form-003");
        assertThat(form003.path("status").asText()).isEqualTo(FormStatus.ARCHIVED.getLabel());
        JsonNode form001 = findById(listForms(collegeToken), "form-001");
        assertThat(form001.path("status").asText()).isEqualTo(FormStatus.ARCHIVED.getLabel());
        JsonNode form002 = findById(listForms(collegeToken), "form-002");
        assertThat(form002.path("status").asText()).isEqualTo(FormStatus.TEACHER_REVIEWING.getLabel());
    }

    @Test
    void shouldRejectCollegeConfirmEvaluationWhenNotSubmittedOrAlreadyConfirmed() throws Exception {
        String collegeToken = login("college01", "123456");

        EvaluationRecordEntity draftEvaluation = new EvaluationRecordEntity();
        draftEvaluation.setId(IdGenerator.nextId("eval"));
        draftEvaluation.setTeacherId("teacher-002");
        draftEvaluation.setStudentId("student-002");
        draftEvaluation.setStageComment("草稿评价");
        draftEvaluation.setSummaryComment("尚未提交学院");
        draftEvaluation.setFinalScore(85);
        draftEvaluation.setDimensionScoresJson("[]");
        draftEvaluation.setStrengthsComment("待补充");
        draftEvaluation.setImprovementComment("待补充");
        draftEvaluation.setCollegeComment("");
        draftEvaluation.setCollegeScore(null);
        draftEvaluation.setSubmittedToCollege(false);
        draftEvaluation.setConfirmedByCollege(false);
        draftEvaluation.setEvaluatedAt(LocalDateTime.now());
        draftEvaluation.setCollegeConfirmedAt(null);
        evaluationRecordMapper.insert(draftEvaluation);

        JsonNode notSubmittedRoot = readRoot(
                mockMvc.perform(post("/api/evaluations/{id}/college-confirm", draftEvaluation.getId())
                                .header("Authorization", bearer(collegeToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "collegeScore": 86,
                                          "collegeComment": "不应允许确认"
                                        }
                                        """))
                        .andExpect(status().isBadRequest())
                        .andReturn()
        );
        assertThat(notSubmittedRoot.path("message").asText()).contains("教师尚未提交评价");

        JsonNode alreadyConfirmedRoot = readRoot(
                mockMvc.perform(post("/api/evaluations/{id}/college-confirm", "eval-001")
                                .header("Authorization", bearer(collegeToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "collegeScore": 91,
                                          "collegeComment": "不应重复确认"
                                        }
                                        """))
                        .andExpect(status().isBadRequest())
                        .andReturn()
        );
        assertThat(alreadyConfirmedRoot.path("message").asText()).contains("已完成学院确认");
    }

    @Test
    void shouldRejectTeacherEditingEvaluationAfterCollegeConfirmation() throws Exception {
        String teacherToken = login("T1002", "123456");
        String collegeToken = login("college01", "123456");

        makeMentorEffectiveForStudent002();

        mockMvc.perform(post("/api/evaluations")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "studentId": "student-002",
                                  "stageComment": "阶段表现稳定",
                                  "summaryComment": "具备较好的班级管理基础",
                                  "finalScore": 89,
                                  "dimensionScores": [
                                    {
                                      "key": "ethics",
                                      "label": "职业素养",
                                      "score": 90,
                                      "comment": "认真负责"
                                    },
                                    {
                                      "key": "teaching",
                                      "label": "教学实施",
                                      "score": 88,
                                      "comment": "组织有序"
                                    },
                                    {
                                      "key": "management",
                                      "label": "班级管理",
                                      "score": 89,
                                      "comment": "能及时处理突发情况"
                                    },
                                    {
                                      "key": "reflection",
                                      "label": "反思改进",
                                      "score": 89,
                                      "comment": "能根据反馈调整"
                                    }
                                  ],
                                  "strengthsComment": "沟通耐心",
                                  "improvementComment": "继续提升总结深度"
                                }
                                """))
                .andExpect(status().isOk());

        JsonNode evaluation = findByValue(listEvaluations(collegeToken), "studentId", "student-002");
        mockMvc.perform(post("/api/evaluations/{id}/college-confirm", evaluation.path("id").asText())
                        .header("Authorization", bearer(collegeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "collegeScore": 90,
                                  "collegeComment": "学院确认通过"
                                }
                                """))
                .andExpect(status().isOk());

        JsonNode editAfterConfirmRoot = readRoot(
                mockMvc.perform(post("/api/evaluations")
                                .header("Authorization", bearer(teacherToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "studentId": "student-002",
                                          "stageComment": "尝试修改已确认评价",
                                          "summaryComment": "不应允许再次提交",
                                          "finalScore": 92,
                                          "dimensionScores": [
                                            {
                                              "key": "ethics",
                                              "label": "职业素养",
                                              "score": 92,
                                              "comment": "保持稳定"
                                            },
                                            {
                                              "key": "teaching",
                                              "label": "教学实施",
                                              "score": 91,
                                              "comment": "持续提升"
                                            },
                                            {
                                              "key": "management",
                                              "label": "班级管理",
                                              "score": 92,
                                              "comment": "控制良好"
                                            },
                                            {
                                              "key": "reflection",
                                              "label": "反思改进",
                                              "score": 93,
                                              "comment": "反思充分"
                                            }
                                          ],
                                          "strengthsComment": "不应覆盖原评价",
                                          "improvementComment": "不应覆盖原评价"
                                        }
                                        """))
                        .andExpect(status().isBadRequest())
                        .andReturn()
        );
        assertThat(editAfterConfirmRoot.path("message").asText()).contains("学院已确认该评价");
    }
    @Test
    void shouldAllowArchivedFormModificationRequestAndResubmission() throws Exception {
        String studentToken = login("20230002", "123456");
        String teacherToken = login("T1002", "123456");
        String collegeToken = login("college01", "123456");

        makeMentorEffectiveForStudent002();
        approveInternshipApplicationForStudent002();

        mockMvc.perform(put("/api/forms/{id}", "form-003")
                        .header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateCode": "class-duty-record",
                                  "content": {
                                    "title": "归档修改测试表单",
                                    "summary": "先完成归档，再申请修改"
                                  },
                                  "submit": true,
                                  "attachments": []
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/forms/{id}/teacher-review", "form-003")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": true,
                                  "score": 91,
                                  "comment": "教师审核通过"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/forms/{id}/college-review", "form-003")
                        .header("Authorization", bearer(collegeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": true,
                                  "score": 93,
                                  "comment": "学院归档通过"
                                }
                                """))
                .andExpect(status().isOk());

        JsonNode archivedForm = findById(listForms(studentToken), "form-003");
        assertThat(archivedForm.path("status").asText()).isEqualTo(FormStatus.ARCHIVED.getLabel());
        int archivedVersion = archivedForm.path("version").asInt();

        mockMvc.perform(post("/api/forms/{id}/modification-request", "form-003")
                        .header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "补充归档后的教学记录"
                                }
                                """))
                .andExpect(status().isOk());

        JsonNode requestingForm = findById(listForms(collegeToken), "form-003");
        assertThat(requestingForm.path("status").asText()).isEqualTo(FormStatus.MODIFICATION_REQUESTING.getLabel());
        assertThat(requestingForm.path("modificationReason").asText()).isEqualTo("补充归档后的教学记录");

        mockMvc.perform(post("/api/forms/{id}/modification-review", "form-003")
                        .header("Authorization", bearer(collegeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": true,
                                  "comment": "同意修改"
                                }
                                """))
                .andExpect(status().isOk());

        JsonNode allowedForm = findById(listForms(studentToken), "form-003");
        assertThat(allowedForm.path("status").asText()).isEqualTo(FormStatus.MODIFICATION_ALLOWED.getLabel());
        assertThat(allowedForm.path("modificationReviewComment").asText()).isEqualTo("同意修改");

        mockMvc.perform(put("/api/forms/{id}", "form-003")
                        .header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateCode": "class-duty-record",
                                  "content": {
                                    "title": "归档修改后的表单",
                                    "summary": "修改后重新提交"
                                  },
                                  "submit": true,
                                  "attachments": []
                                }
                                """))
                .andExpect(status().isOk());

        JsonNode resubmittedForm = findById(listForms(studentToken), "form-003");
        assertThat(resubmittedForm.path("status").asText()).isEqualTo(FormStatus.TEACHER_REVIEWING.getLabel());
        assertThat(resubmittedForm.path("version").asInt()).isEqualTo(archivedVersion + 1);
    }

    private void makeMentorEffectiveForStudent002() throws Exception {
        String teacherToken = login("T1002", "123456");
        String collegeToken = login("college01", "123456");

        JsonNode mentorApplication = findById(listMentorApplications(collegeToken), "mentor-app-002");
        if (MentorApplicationStatus.PENDING_TEACHER.getLabel().equals(mentorApplication.path("status").asText())) {
            mockMvc.perform(post("/api/mentor-applications/{id}/teacher-review", "mentor-app-002")
                            .header("Authorization", bearer(teacherToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "approved": true,
                                      "comment": "指导关系确认"
                                    }
                                    """))
                    .andExpect(status().isOk());
        }

        mentorApplication = findById(listMentorApplications(collegeToken), "mentor-app-002");
        if (MentorApplicationStatus.PENDING_COLLEGE.getLabel().equals(mentorApplication.path("status").asText())) {
            mockMvc.perform(post("/api/mentor-applications/{id}/college-review", "mentor-app-002")
                            .header("Authorization", bearer(collegeToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "approved": true,
                                      "comment": "学院确认生效"
                                    }
                                    """))
                    .andExpect(status().isOk());
        }
    }

    private void approveInternshipApplicationForStudent002() throws Exception {
        String collegeToken = login("college01", "123456");

        JsonNode application = findById(listInternshipApplications(collegeToken), "internship-app-002");
        if (InternshipApplicationStatus.APPROVED.getLabel().equals(application.path("status").asText())) {
            return;
        }

        mockMvc.perform(post("/api/internship-applications/{id}/review", "internship-app-002")
                        .header("Authorization", bearer(collegeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": true,
                                  "organizationConfirmation": "已确认接收",
                                  "organizationFeedback": "同意安排班主任实习",
                                  "receivedAt": "2026-03-18",
                                  "comment": "审核通过"
                                }
                                """))
                .andExpect(status().isOk());
    }

    private JsonNode listMentorApplications(String token) throws Exception {
        return readData(
                mockMvc.perform(get("/api/mentor-applications")
                                .header("Authorization", bearer(token)))
                        .andExpect(status().isOk())
                        .andReturn()
        );
    }

    private JsonNode listInternshipApplications(String token) throws Exception {
        return readData(
                mockMvc.perform(get("/api/internship-applications")
                                .header("Authorization", bearer(token)))
                        .andExpect(status().isOk())
                        .andReturn()
        );
    }

    private JsonNode listForms(String token) throws Exception {
        return readData(
                mockMvc.perform(get("/api/forms")
                                .header("Authorization", bearer(token)))
                        .andExpect(status().isOk())
                        .andReturn()
        );
    }

    private JsonNode listStudents(String token) throws Exception {
        return readData(
                mockMvc.perform(get("/api/students")
                                .header("Authorization", bearer(token)))
                        .andExpect(status().isOk())
                        .andReturn()
        );
    }

    private JsonNode listEvaluations(String token) throws Exception {
        return readData(
                mockMvc.perform(get("/api/evaluations")
                                .header("Authorization", bearer(token)))
                        .andExpect(status().isOk())
                        .andReturn()
        );
    }

    private String login(String account, String password) throws Exception {
        JsonNode data = readData(
                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "account": "%s",
                                          "password": "%s"
                                        }
                                        """.formatted(account, password)))
                        .andExpect(status().isOk())
                        .andReturn()
        );
        return data.path("token").asText();
    }

    private JsonNode readRoot(MvcResult result) throws Exception {
        String response = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response);
    }

    private JsonNode readData(MvcResult result) throws Exception {
        return readRoot(result).path("data");
    }

    private JsonNode findById(JsonNode array, String id) {
        for (JsonNode item : array) {
            if (id.equals(item.path("id").asText())) {
                return item;
            }
        }
        throw new IllegalStateException("item not found: " + id);
    }

    private JsonNode findByValue(JsonNode array, String field, String expected) {
        for (JsonNode item : array) {
            if (expected.equals(item.path(field).asText())) {
                return item;
            }
        }
        throw new IllegalStateException("item not found by " + field + ": " + expected);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
