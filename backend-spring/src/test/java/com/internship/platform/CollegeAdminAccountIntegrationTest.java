package com.internship.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CollegeAdminAccountIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldDownloadStudentAndTeacherImportTemplatesAsCollegeAdmin() throws Exception {
        String token = loginAsCollegeAdmin();

        MvcResult studentResult = mockMvc.perform(get("/api/students/import-template")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();

        assertExcelDownload(studentResult, "student-import-template.xlsx");

        MvcResult teacherResult = mockMvc.perform(get("/api/teachers/import-template")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();

        assertExcelDownload(teacherResult, "teacher-import-template.xlsx");
    }

    @Test
    void shouldCreateCollegeAndAdminWhenApprovingCollegeApplication() throws Exception {
        String rootToken = loginAsRoot();

        JsonNode reviewResult = readData(
                mockMvc.perform(post("/api/admin/college-applications/{id}/review", "college-app-001")
                                .header("Authorization", bearer(rootToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "approved": true,
                                          "comment": "\u901a\u8fc7\u5165\u9a7b\u5ba1\u6838"
                                        }
                                        """))
                        .andExpect(status().isOk())
                        .andReturn()
        );

        assertThat(reviewResult.path("approved").asBoolean()).isTrue();
        assertThat(reviewResult.path("status").asText()).isEqualTo("\u5df2\u901a\u8fc7");
        assertThat(reviewResult.path("reviewComment").asText()).contains("\u5b66\u9662\u7ba1\u7406\u5458\u8d26\u53f7");
        assertThat(reviewResult.path("generatedCollegeAdmin").asBoolean()).isTrue();
        assertThat(reviewResult.path("collegeAdminAccount").asText()).startsWith("college");
        assertThat(reviewResult.path("defaultPassword").asText()).isEqualTo("123456");
        assertThat(reviewResult.path("mustChangePassword").asBoolean()).isTrue();

        JsonNode applications = readData(
                mockMvc.perform(get("/api/admin/college-applications")
                                .header("Authorization", bearer(rootToken)))
                        .andExpect(status().isOk())
                        .andReturn()
        );
        JsonNode application = findById(applications, "college-app-001");
        assertThat(application.path("status").asText()).isEqualTo("\u5df2\u901a\u8fc7");
        assertThat(application.path("reviewComment").asText()).contains(reviewResult.path("collegeAdminAccount").asText());

        JsonNode collegeAdmins = readData(
                mockMvc.perform(get("/api/admin/college-admins")
                                .header("Authorization", bearer(rootToken)))
                        .andExpect(status().isOk())
                        .andReturn()
        );
        JsonNode createdAdmin = findByValue(collegeAdmins, "collegeName", "\u6570\u5b66\u4e0e\u7edf\u8ba1\u5b66\u9662");
        assertThat(createdAdmin).isNotNull();
        assertThat(createdAdmin.path("account").asText()).isEqualTo(reviewResult.path("collegeAdminAccount").asText());
        assertThat(createdAdmin.path("mustChangePassword").asBoolean()).isTrue();

        JsonNode loginData = readData(
                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "account": "%s",
                                          "password": "123456"
                                        }
                                        """.formatted(createdAdmin.path("account").asText())))
                        .andExpect(status().isOk())
                        .andReturn()
        );
        assertThat(loginData.path("user").path("role").asText()).isEqualTo("COLLEGE_ADMIN");
    }

    @Test
    void shouldListAndManageCollegeAdminAccounts() throws Exception {
        String rootToken = loginAsRoot();

        JsonNode initialList = readData(
                mockMvc.perform(get("/api/admin/college-admins")
                                .header("Authorization", bearer(rootToken)))
                        .andExpect(status().isOk())
                        .andReturn()
        );

        JsonNode collegeAdmin = findByAccount(initialList, "college01");
        assertThat(collegeAdmin.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(collegeAdmin.path("collegeName").asText()).isNotBlank();

        mockMvc.perform(patch("/api/admin/college-admins/{id}/status", "user-college-001")
                        .header("Authorization", bearer(rootToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "DISABLED"
                                }
                                """))
                .andExpect(status().isOk());

        JsonNode disabledList = readData(
                mockMvc.perform(get("/api/admin/college-admins")
                                .header("Authorization", bearer(rootToken)))
                        .andExpect(status().isOk())
                        .andReturn()
        );
        assertThat(findByAccount(disabledList, "college01").path("status").asText()).isEqualTo("DISABLED");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "account": "college01",
                                  "password": "123456"
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/admin/college-admins/{id}/status", "user-college-001")
                        .header("Authorization", bearer(rootToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "ACTIVE"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/college-admins/{id}/reset-password", "user-college-001")
                        .header("Authorization", bearer(rootToken)))
                .andExpect(status().isOk());

        JsonNode updatedList = readData(
                mockMvc.perform(get("/api/admin/college-admins")
                                .header("Authorization", bearer(rootToken)))
                        .andExpect(status().isOk())
                        .andReturn()
        );

        JsonNode updatedAdmin = findByAccount(updatedList, "college01");
        assertThat(updatedAdmin.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(updatedAdmin.path("mustChangePassword").asBoolean()).isTrue();

        JsonNode loginData = readData(
                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "account": "college01",
                                          "password": "123456"
                                        }
                                        """))
                        .andExpect(status().isOk())
                        .andReturn()
        );
        assertThat(loginData.path("user").path("mustChangePassword").asBoolean()).isTrue();
    }

    private void assertExcelDownload(MvcResult result, String fileName) {
        String contentType = result.getResponse().getContentType();
        String disposition = result.getResponse().getHeader("Content-Disposition");
        byte[] bytes = result.getResponse().getContentAsByteArray();

        assertThat(contentType).contains("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(disposition).contains(fileName);
        assertThat(bytes.length).isGreaterThan(200);
        assertThat((char) bytes[0]).isEqualTo('P');
        assertThat((char) bytes[1]).isEqualTo('K');
    }

    private String loginAsRoot() throws Exception {
        return login("root", "123456");
    }

    private String loginAsCollegeAdmin() throws Exception {
        return login("college01", "123456");
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

    private JsonNode findByValue(JsonNode array, String field, String value) {
        for (JsonNode item : array) {
            if (value.equals(item.path(field).asText())) {
                return item;
            }
        }
        return null;
    }

    private JsonNode findByAccount(JsonNode array, String account) {
        JsonNode item = findByValue(array, "account", account);
        if (item != null) {
            return item;
        }
        throw new IllegalStateException("college admin not found: " + account);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}