package com.internship.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
class SystemSettingsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldListConfigurableReminderSettings() throws Exception {
        String token = loginAsRoot();

        JsonNode data = readData(
                mockMvc.perform(get("/api/admin/system-settings")
                                .header("Authorization", bearer(token)))
                        .andExpect(status().isOk())
                        .andReturn()
        );

        JsonNode teacherLevel = findByKey(data, "teacher_review_alert_level");
        JsonNode teacherSwitch = findByKey(data, "teacher_review_remind_enabled");
        JsonNode teacherTitle = findByKey(data, "teacher_review_reminder_title_template");

        assertThat(data.isArray()).isTrue();
        assertThat(data.size()).isGreaterThanOrEqualTo(20);
        assertThat(teacherLevel.path("valueType").asText()).isEqualTo("SELECT");
        assertThat(teacherLevel.path("options").isArray()).isTrue();
        assertThat(teacherSwitch.path("valueType").asText()).isEqualTo("BOOLEAN");
        assertThat(teacherTitle.path("valueType").asText()).isEqualTo("TEXT");
    }

    @Test
    void shouldSaveReminderSwitchAndAlertLevel() throws Exception {
        String token = loginAsRoot();

        String body = """
                {
                  "items": [
                    { "key": "teacher_review_alert_level", "value": "danger" },
                    { "key": "teacher_review_remind_enabled", "value": "0" }
                  ]
                }
                """;

        mockMvc.perform(put("/api/admin/system-settings")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        JsonNode data = readData(
                mockMvc.perform(get("/api/admin/system-settings")
                                .header("Authorization", bearer(token)))
                        .andExpect(status().isOk())
                        .andReturn()
        );

        assertThat(findByKey(data, "teacher_review_alert_level").path("value").asText()).isEqualTo("danger");
        assertThat(findByKey(data, "teacher_review_remind_enabled").path("value").asText()).isEqualTo("0");
    }

    @Test
    void shouldRejectInvalidAlertLevel() throws Exception {
        String token = loginAsRoot();

        String body = """
                {
                  "items": [
                    { "key": "teacher_review_alert_level", "value": "critical" }
                  ]
                }
                """;

        String response = mockMvc.perform(put("/api/admin/system-settings")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode root = objectMapper.readTree(response);
        assertThat(root.path("success").asBoolean()).isFalse();
        assertThat(root.path("message").asText()).contains("配置值不合法");
    }

    private String loginAsRoot() throws Exception {
        String body = """
                {
                  "account": "root",
                  "password": "123456"
                }
                """;

        JsonNode data = readData(
                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andExpect(status().isOk())
                        .andReturn()
        );
        return data.path("token").asText();
    }

    private JsonNode readData(MvcResult result) throws Exception {
        String response = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode root = objectMapper.readTree(response);
        return root.path("data");
    }

    private JsonNode findByKey(JsonNode array, String key) {
        for (JsonNode item : array) {
            if (key.equals(item.path("key").asText())) {
                return item;
            }
        }
        throw new IllegalStateException("setting not found: " + key);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}