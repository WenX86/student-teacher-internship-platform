package com.internship.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.platform.constant.MentorApplicationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MessageNotificationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldSendMentorApplicationNotificationsToTeacherCollegeAndStudent() throws Exception {
        String studentToken = login("20230002", "123456");
        String teacherToken = login("T1002", "123456");
        String collegeToken = login("college01", "123456");

        mockMvc.perform(post("/api/mentor-applications/{id}/teacher-review", "mentor-app-002")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": false,
                                  "comment": "请先补充指导申请说明"
                                }
                                """))
                .andExpect(status().isOk());

        JsonNode rejectedMessage = findMessageByTitle(listMessages(studentToken), "指导教师申请被驳回");
        assertThat(rejectedMessage.path("type").asText()).isEqualTo("退回通知");
        assertThat(rejectedMessage.path("content").asText()).contains("请先补充指导申请说明");
        assertThat(rejectedMessage.path("link").asText()).isEqualTo("/student/mentor-applications");

        mockMvc.perform(post("/api/mentor-applications")
                        .header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "teacherId": "teacher-002",
                                  "studentRemark": "补充说明后再次申请"
                                }
                                """))
                .andExpect(status().isOk());

        JsonNode createdApplication = findByValue(listMentorApplications(studentToken), "studentRemark", "补充说明后再次申请");
        JsonNode teacherPendingMessage = findMessageByTitle(listMessages(teacherToken), "李四 发起了指导教师申请");
        assertThat(teacherPendingMessage.path("type").asText()).isEqualTo("待办提醒");
        assertThat(teacherPendingMessage.path("link").asText()).isEqualTo("/teacher/mentor-requests");

        mockMvc.perform(post("/api/mentor-applications/{id}/teacher-review", createdApplication.path("id").asText())
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": true,
                                  "comment": "同意接收"
                                }
                                """))
                .andExpect(status().isOk());

        JsonNode collegeReviewMessage = findMessageByTitle(listMessages(collegeToken), "李四 的指导关系待复核");
        assertThat(collegeReviewMessage.path("type").asText()).isEqualTo("待办提醒");
        assertThat(collegeReviewMessage.path("link").asText()).isEqualTo("/college/mentor-relations");

        mockMvc.perform(post("/api/mentor-applications/{id}/college-review", createdApplication.path("id").asText())
                        .header("Authorization", bearer(collegeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": true,
                                  "comment": "学院审核通过"
                                }
                                """))
                .andExpect(status().isOk());

        JsonNode effectiveMessage = findMessageByTitle(listMessages(studentToken), "指导关系已正式生效");
        assertThat(effectiveMessage.path("type").asText()).isEqualTo("审核结果");
        assertThat(effectiveMessage.path("link").asText()).isEqualTo("/student/mentor-applications");
    }

    @Test
    void shouldSendInternshipNotificationsToCollegeAndStudentOnRejectAndResubmit() throws Exception {
        String studentToken = login("20230002", "123456");
        String collegeToken = login("college01", "123456");

        makeMentorEffectiveForStudent002();

        int initialCollegePendingCount = countMessagesByTitle(listMessages(collegeToken), "李四 提交了实习申请");

        mockMvc.perform(post("/api/internship-applications")
                        .header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationId": "org-002",
                                  "batchName": "2026春季消息测试批次",
                                  "position": "班主任助理",
                                  "gradeTarget": "小学五年级",
                                  "startDate": "2026-03-26",
                                  "endDate": "2026-06-30",
                                  "remark": "测试学院待办提醒",
                                  "attachments": []
                                }
                                """))
                .andExpect(status().isOk());

        JsonNode createdApplication = findByValue(listInternshipApplications(studentToken), "batchName", "2026春季消息测试批次");
        assertThat(countMessagesByTitle(listMessages(collegeToken), "李四 提交了实习申请")).isEqualTo(initialCollegePendingCount + 1);

        mockMvc.perform(post("/api/internship-applications/{id}/review", createdApplication.path("id").asText())
                        .header("Authorization", bearer(collegeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": false,
                                  "organizationConfirmation": "待补充材料",
                                  "organizationFeedback": "请上传接收回执",
                                  "receivedAt": "2026-03-27",
                                  "comment": "请补充单位接收材料"
                                }
                                """))
                .andExpect(status().isOk());

        JsonNode rejectedMessage = findMessageByTitle(listMessages(studentToken), "实习申请被退回");
        assertThat(rejectedMessage.path("type").asText()).isEqualTo("退回通知");
        assertThat(rejectedMessage.path("content").asText()).contains("请补充单位接收材料");
        assertThat(rejectedMessage.path("link").asText()).isEqualTo("/student/internship-application");

        mockMvc.perform(post("/api/internship-applications")
                        .header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "organizationId": "org-002",
                                  "batchName": "2026春季消息重提批次",
                                  "position": "班主任助理",
                                  "gradeTarget": "小学五年级",
                                  "startDate": "2026-03-28",
                                  "endDate": "2026-06-30",
                                  "remark": "补充材料后再次提交",
                                  "attachments": []
                                }
                                """))
                .andExpect(status().isOk());

        assertThat(countMessagesByTitle(listMessages(collegeToken), "李四 提交了实习申请")).isEqualTo(initialCollegePendingCount + 2);
    }

    @Test
    void shouldSendFormNotificationsToTeacherCollegeAndStudent() throws Exception {
        String studentToken = login("20230002", "123456");
        String teacherToken = login("T1002", "123456");
        String collegeToken = login("college01", "123456");

        makeMentorEffectiveForStudent002();
        approveInternshipApplicationForStudent002();
        int initialTeacherReviewReminderCount = countMessagesByLink(listMessages(teacherToken), "/teacher/reviews");
        int initialCollegeArchiveReminderCount = countMessagesByLink(listMessages(collegeToken), "/college/archive");

        mockMvc.perform(put("/api/forms/{id}", "form-003")
                        .header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateCode": "class-duty-record",
                                  "content": {
                                    "title": "值守记录消息测试",
                                    "summary": "提交给教师审核"
                                  },
                                  "submit": true,
                                  "attachments": []
                                }
                                """))
                .andExpect(status().isOk());

        JsonNode teacherSubmitMessage = firstMessageByLink(listMessages(teacherToken), "/teacher/reviews");
        assertThat(countMessagesByLink(listMessages(teacherToken), "/teacher/reviews")).isEqualTo(initialTeacherReviewReminderCount + 1);
        assertThat(teacherSubmitMessage.path("type").asText()).isEqualTo("待办提醒");
        assertThat(teacherSubmitMessage.path("link").asText()).isEqualTo("/teacher/reviews");

        mockMvc.perform(post("/api/forms/{id}/teacher-review", "form-003")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": false,
                                  "score": 79,
                                  "comment": "请补充晚点名情况"
                                }
                                """))
                .andExpect(status().isOk());

        JsonNode studentReturnedMessage = findMessageByTitle(listMessages(studentToken), "值守记录被教师退回");
        assertThat(studentReturnedMessage.path("type").asText()).isEqualTo("退回通知");
        assertThat(studentReturnedMessage.path("content").asText()).contains("请补充晚点名情况");
        assertThat(studentReturnedMessage.path("link").asText()).isEqualTo("/student/forms");

        mockMvc.perform(put("/api/forms/{id}", "form-003")
                        .header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateCode": "class-duty-record",
                                  "content": {
                                    "title": "值守记录消息测试重提",
                                    "summary": "已补充晚点名与纪律反馈"
                                  },
                                  "submit": true,
                                  "attachments": []
                                }
                                """))
                .andExpect(status().isOk());

        JsonNode teacherResubmitMessage = firstMessageByLink(listMessages(teacherToken), "/teacher/reviews");
        assertThat(countMessagesByLink(listMessages(teacherToken), "/teacher/reviews")).isEqualTo(initialTeacherReviewReminderCount + 2);
        assertThat(teacherResubmitMessage.path("type").asText()).isEqualTo("待办提醒");
        assertThat(teacherResubmitMessage.path("link").asText()).isEqualTo("/teacher/reviews");

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

        JsonNode collegeArchiveMessage = firstMessageByLink(listMessages(collegeToken), "/college/archive");
        assertThat(countMessagesByLink(listMessages(collegeToken), "/college/archive")).isEqualTo(initialCollegeArchiveReminderCount + 1);
        assertThat(collegeArchiveMessage.path("type").asText()).isEqualTo("待办提醒");
        assertThat(collegeArchiveMessage.path("link").asText()).isEqualTo("/college/archive");

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

        JsonNode studentArchivedMessage = findMessageByTitle(listMessages(studentToken), "值守记录已归档");
        assertThat(studentArchivedMessage.path("type").asText()).isEqualTo("审核结果");
        assertThat(studentArchivedMessage.path("link").asText()).isEqualTo("/student/forms");
    }

    @Test
    void shouldSendEvaluationNotificationsToCollegeAndStudent() throws Exception {
        String teacherToken = login("T1002", "123456");
        String studentToken = login("20230002", "123456");
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

        JsonNode collegeEvaluationMessage = findMessageByTitle(listMessages(collegeToken), "李四 的实习评价待学院确认");
        assertThat(collegeEvaluationMessage.path("type").asText()).isEqualTo("待办提醒");
        assertThat(collegeEvaluationMessage.path("link").asText()).isEqualTo("/college/evaluations");

        JsonNode evaluation = findEvaluationByStudentNo(listEvaluations(collegeToken), "20230002");
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

        JsonNode studentEvaluationMessage = findMessageByTitle(listMessages(studentToken), "学院已确认实习评价");
        assertThat(studentEvaluationMessage.path("type").asText()).isEqualTo("评价结果");
        assertThat(studentEvaluationMessage.path("link").asText()).isEqualTo("/student/results");
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
        if ("已通过".equals(application.path("status").asText())) {
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

    @Test
    void shouldMarkAllMessagesReadForCurrentUser() throws Exception {
        String studentToken = login("20230002", "123456");
        String teacherToken = login("T1002", "123456");

        mockMvc.perform(post("/api/mentor-applications/{id}/teacher-review", "mentor-app-002")
                        .header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approved": false,
                                  "comment": "全部已读测试消息"
                                }
                                """))
                .andExpect(status().isOk());

        JsonNode messagesBefore = listMessages(studentToken);
        long unreadBefore = countUnreadMessages(messagesBefore);
        assertThat(unreadBefore).isGreaterThan(0);

        JsonNode result = readData(
                mockMvc.perform(post("/api/messages/read-all")
                                .header("Authorization", bearer(studentToken)))
                        .andExpect(status().isOk())
                        .andReturn()
        );
        assertThat(result.path("updatedCount").asLong()).isEqualTo(unreadBefore);

        JsonNode messagesAfter = listMessages(studentToken);
        assertThat(countUnreadMessages(messagesAfter)).isZero();
        for (JsonNode item : messagesAfter) {
            assertThat(item.path("read").asBoolean()).isTrue();
        }
    }

    private JsonNode listMessages(String token) throws Exception {
        return readData(
                mockMvc.perform(get("/api/messages")
                                .header("Authorization", bearer(token)))
                        .andExpect(status().isOk())
                        .andReturn()
        );
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

    private JsonNode listEvaluations(String token) throws Exception {
        return readData(
                mockMvc.perform(get("/api/evaluations")
                                .header("Authorization", bearer(token)))
                        .andExpect(status().isOk())
                        .andReturn()
        );
    }

    private int countMessagesByTitle(JsonNode messages, String title) {
        int count = 0;
        for (JsonNode item : messages) {
            if (title.equals(item.path("title").asText())) {
                count += 1;
            }
        }
        return count;
    }

    private JsonNode findMessageByTitle(JsonNode messages, String title) {
        for (JsonNode item : messages) {
            if (title.equals(item.path("title").asText())) {
                return item;
            }
        }
        throw new IllegalStateException("message not found: " + title);
    }

    private int countMessagesByLink(JsonNode messages, String link) {
        int count = 0;
        for (JsonNode item : messages) {
            if (link.equals(item.path("link").asText())) {
                count += 1;
            }
        }
        return count;
    }

    private JsonNode firstMessageByLink(JsonNode messages, String link) {
        for (JsonNode item : messages) {
            if (link.equals(item.path("link").asText())) {
                return item;
            }
        }
        throw new IllegalStateException("message not found by link: " + link);
    }

    private long countUnreadMessages(JsonNode messages) {
        long count = 0;
        for (JsonNode item : messages) {
            if (!item.path("read").asBoolean()) {
                count += 1;
            }
        }
        return count;
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

    private JsonNode findEvaluationByStudentNo(JsonNode evaluations, String studentNo) {
        for (JsonNode item : evaluations) {
            if (studentNo.equals(item.path("student").path("studentNo").asText())) {
                return item;
            }
        }
        throw new IllegalStateException("evaluation not found for studentNo: " + studentNo);
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

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
