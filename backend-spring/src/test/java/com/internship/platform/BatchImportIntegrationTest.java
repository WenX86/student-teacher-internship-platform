package com.internship.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BatchImportIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldImportStudentsFromCsv() throws Exception {
        String token = loginAsCollegeAdmin();
        String csv = "\u59d3\u540d,\u5b66\u53f7,\u4e13\u4e1a,\u73ed\u7ea7,\u8054\u7cfb\u7535\u8bdd,\u5b9e\u4e60\u7c7b\u578b\n"
                + "\u8d75\u516d,20239991,\u6c49\u8bed\u8a00\u6587\u5b66,\u4e2d\u65871\u73ed,13812340001,TEACHING\n";
        MockMultipartFile file = new MockMultipartFile("file", "students.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        JsonNode data = readData(
                mockMvc.perform(multipart("/api/students/import")
                                .file(file)
                                .header("Authorization", bearer(token))
                                .contentType(MULTIPART_FORM_DATA))
                        .andExpect(status().isOk())
                        .andReturn()
        );

        assertThat(data.path("successCount").asInt()).isEqualTo(1);
        JsonNode students = readData(
                mockMvc.perform(get("/api/students")
                                .header("Authorization", bearer(token)))
                        .andExpect(status().isOk())
                        .andReturn()
        );
        assertThat(findByValue(students, "studentNo", "20239991")).isNotNull();
    }

    @Test
    void shouldImportStudentsFromXlsx() throws Exception {
        String token = loginAsCollegeAdmin();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "students.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createWorkbookBytes(new XSSFWorkbook(), List.of(
                        List.of("\u59d3\u540d", "\u5b66\u53f7", "\u4e13\u4e1a", "\u73ed\u7ea7", "\u8054\u7cfb\u7535\u8bdd", "\u5b9e\u4e60\u7c7b\u578b"),
                        List.of("\u94b1\u4e03", "20239992", "\u5c0f\u5b66\u6559\u80b2", "\u5c0f\u65592\u73ed", "13812340002", "HEAD_TEACHER")
                ))
        );

        JsonNode data = readData(
                mockMvc.perform(multipart("/api/students/import")
                                .file(file)
                                .header("Authorization", bearer(token))
                                .contentType(MULTIPART_FORM_DATA))
                        .andExpect(status().isOk())
                        .andReturn()
        );

        assertThat(data.path("successCount").asInt()).isEqualTo(1);
        JsonNode students = readData(
                mockMvc.perform(get("/api/students")
                                .header("Authorization", bearer(token)))
                        .andExpect(status().isOk())
                        .andReturn()
        );
        assertThat(findByValue(students, "studentNo", "20239992")).isNotNull();
    }

    @Test
    void shouldImportTeachersFromXls() throws Exception {
        String token = loginAsCollegeAdmin();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "teachers.xls",
                "application/vnd.ms-excel",
                createWorkbookBytes(new HSSFWorkbook(), List.of(
                        List.of("\u59d3\u540d", "\u5de5\u53f7", "\u90e8\u95e8", "\u8054\u7cfb\u7535\u8bdd"),
                        List.of("\u5468\u8001\u5e08", "T209901", "\u6570\u5b66\u6559\u7814\u5ba4", "13912340003")
                ))
        );

        JsonNode data = readData(
                mockMvc.perform(multipart("/api/teachers/import")
                                .file(file)
                                .header("Authorization", bearer(token))
                                .contentType(MULTIPART_FORM_DATA))
                        .andExpect(status().isOk())
                        .andReturn()
        );

        assertThat(data.path("successCount").asInt()).isEqualTo(1);
        JsonNode teachers = readData(
                mockMvc.perform(get("/api/teachers")
                                .header("Authorization", bearer(token)))
                        .andExpect(status().isOk())
                        .andReturn()
        );
        assertThat(findByValue(teachers, "employeeNo", "T209901")).isNotNull();
    }

    @Test
    void shouldImportTeachersFromXlsxAndReturnRowErrors() throws Exception {
        String token = loginAsCollegeAdmin();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "teachers.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createWorkbookBytes(new XSSFWorkbook(), List.of(
                        List.of("\u59d3\u540d", "\u5de5\u53f7", "\u90e8\u95e8", "\u8054\u7cfb\u7535\u8bdd"),
                        List.of("\u5434\u8001\u5e08", "T209902", "\u82f1\u8bed\u6559\u7814\u5ba4", "13912340004"),
                        List.of("\u91cd\u590d\u8001\u5e08", "T209902", "\u82f1\u8bed\u6559\u7814\u5ba4", "13912340005")
                ))
        );

        JsonNode data = readData(
                mockMvc.perform(multipart("/api/teachers/import")
                                .file(file)
                                .header("Authorization", bearer(token))
                                .contentType(MULTIPART_FORM_DATA))
                        .andExpect(status().isOk())
                        .andReturn()
        );

        assertThat(data.path("successCount").asInt()).isEqualTo(1);
        assertThat(data.path("skippedCount").asInt()).isEqualTo(1);
        assertThat(data.path("errors").isArray()).isTrue();
        assertThat(data.path("errors").get(0).asText()).contains("\u5de5\u53f7\u91cd\u590d");
    }

    @Test
    void shouldRejectStudentImportWhenTemplateMissingRequiredColumn() throws Exception {
        String token = loginAsCollegeAdmin();
        String csv = "\u59d3\u540d,\u5b66\u53f7,\u4e13\u4e1a,\u73ed\u7ea7,\u8054\u7cfb\u7535\u8bdd\n"
                + "\u5f20\u5c0f\u6797,20239993,\u6559\u80b2\u5b66,2023\u7ea72\u73ed,13812340006\n";
        MockMultipartFile file = new MockMultipartFile("file", "students-missing-column.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        JsonNode root = readRoot(
                mockMvc.perform(multipart("/api/students/import")
                                .file(file)
                                .header("Authorization", bearer(token))
                                .contentType(MULTIPART_FORM_DATA))
                        .andExpect(status().isBadRequest())
                        .andReturn()
        );

        assertThat(root.path("message").asText()).contains("\u5bfc\u5165\u6a21\u677f\u7f3a\u5c11\u5217");
        assertThat(root.path("message").asText()).contains("\u5b9e\u4e60\u7c7b\u578b");
    }

    @Test
    void shouldSkipStudentsWithExistingAccount() throws Exception {
        String token = loginAsCollegeAdmin();
        String csv = "\u59d3\u540d,\u5b66\u53f7,\u4e13\u4e1a,\u73ed\u7ea7,\u8054\u7cfb\u7535\u8bdd,\u5b9e\u4e60\u7c7b\u578b\n"
                + "\u91cd\u590d\u5b66\u751f,20230001,\u6c49\u8bed\u8a00\u6587\u5b66,2023\u7ea71\u73ed,13812340007,TEACHING\n";
        MockMultipartFile file = new MockMultipartFile("file", "students-duplicate.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        JsonNode data = readData(
                mockMvc.perform(multipart("/api/students/import")
                                .file(file)
                                .header("Authorization", bearer(token))
                                .contentType(MULTIPART_FORM_DATA))
                        .andExpect(status().isOk())
                        .andReturn()
        );

        assertThat(data.path("successCount").asInt()).isZero();
        assertThat(data.path("skippedCount").asInt()).isEqualTo(1);
        assertThat(data.path("errors").get(0).asText()).contains("\u5b66\u53f7\u5df2\u5b58\u5728");
    }

    @Test
    void shouldSkipStudentsWithInvalidInternshipType() throws Exception {
        String token = loginAsCollegeAdmin();
        String csv = "\u59d3\u540d,\u5b66\u53f7,\u4e13\u4e1a,\u73ed\u7ea7,\u8054\u7cfb\u7535\u8bdd,\u5b9e\u4e60\u7c7b\u578b\n"
                + "\u7c7b\u578b\u5f02\u5e38\u5b66\u751f,20239994,\u6570\u5b66\u4e0e\u5e94\u7528\u6570\u5b66,2023\u7ea73\u73ed,13812340008,UNKNOWN\n";
        MockMultipartFile file = new MockMultipartFile("file", "students-invalid-type.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        JsonNode data = readData(
                mockMvc.perform(multipart("/api/students/import")
                                .file(file)
                                .header("Authorization", bearer(token))
                                .contentType(MULTIPART_FORM_DATA))
                        .andExpect(status().isOk())
                        .andReturn()
        );

        assertThat(data.path("successCount").asInt()).isZero();
        assertThat(data.path("skippedCount").asInt()).isEqualTo(1);
        assertThat(data.path("errors").get(0).asText()).contains("\u5b9e\u4e60\u7c7b\u578b\u4ec5\u652f\u6301");
    }

    @Test
    void shouldRejectEmptyStudentImportFile() throws Exception {
        String token = loginAsCollegeAdmin();
        MockMultipartFile file = new MockMultipartFile("file", "students.csv", "text/csv", new byte[0]);

        JsonNode root = readRoot(
                mockMvc.perform(multipart("/api/students/import")
                                .file(file)
                                .header("Authorization", bearer(token))
                                .contentType(MULTIPART_FORM_DATA))
                        .andExpect(status().isBadRequest())
                        .andReturn()
        );

        assertThat(root.path("message").asText()).contains("\u8bf7\u5148\u9009\u62e9\u5bfc\u5165\u6587\u4ef6");
    }

    @Test
    void shouldRejectStudentImportWithUnsupportedExtension() throws Exception {
        String token = loginAsCollegeAdmin();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "students.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "\u59d3\u540d,\u5b66\u53f7".getBytes(StandardCharsets.UTF_8)
        );

        JsonNode root = readRoot(
                mockMvc.perform(multipart("/api/students/import")
                                .file(file)
                                .header("Authorization", bearer(token))
                                .contentType(MULTIPART_FORM_DATA))
                        .andExpect(status().isBadRequest())
                        .andReturn()
        );

        assertThat(root.path("message").asText()).contains("\u4ec5\u652f\u6301 CSV\u3001XLSX \u6216 XLS \u5bfc\u5165");
    }

    @Test
    void shouldRejectStudentImportWhenOnlyBlankRowsProvided() throws Exception {
        String token = loginAsCollegeAdmin();
        String csv = "\u59d3\u540d,\u5b66\u53f7,\u4e13\u4e1a,\u73ed\u7ea7,\u8054\u7cfb\u7535\u8bdd,\u5b9e\u4e60\u7c7b\u578b\n\n  ,  ,  ,  ,  ,  \n";
        MockMultipartFile file = new MockMultipartFile("file", "students-blank-rows.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        JsonNode root = readRoot(
                mockMvc.perform(multipart("/api/students/import")
                                .file(file)
                                .header("Authorization", bearer(token))
                                .contentType(MULTIPART_FORM_DATA))
                        .andExpect(status().isBadRequest())
                        .andReturn()
        );

        assertThat(root.path("message").asText()).contains("\u5bfc\u5165\u6587\u4ef6\u6ca1\u6709\u53ef\u5904\u7406\u7684\u6570\u636e\u884c");
    }

    private byte[] createWorkbookBytes(Workbook workbook, List<List<String>> rows) throws Exception {
        try (Workbook target = workbook; ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = target.createSheet("import");
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex += 1) {
                Row row = sheet.createRow(rowIndex);
                List<String> values = rows.get(rowIndex);
                for (int columnIndex = 0; columnIndex < values.size(); columnIndex += 1) {
                    row.createCell(columnIndex).setCellValue(values.get(columnIndex));
                }
            }
            target.write(outputStream);
            return outputStream.toByteArray();
        }
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

    private JsonNode findByValue(JsonNode array, String field, String expected) {
        for (JsonNode item : array) {
            if (expected.equals(item.path(field).asText())) {
                return item;
            }
        }
        return null;
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}