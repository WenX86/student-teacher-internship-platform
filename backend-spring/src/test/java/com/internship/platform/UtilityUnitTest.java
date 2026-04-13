package com.internship.platform;

import com.internship.platform.util.IdGenerator;
import com.internship.platform.util.PasswordUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UtilityUnitTest {

    @Test
    void shouldGenerateStableSha256Hash() {
        assertThat(PasswordUtils.sha256("123456"))
                .isEqualTo("8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92");
    }

    @Test
    void shouldGeneratePrefixedUniqueIds() {
        String firstId = IdGenerator.nextId("form");
        String secondId = IdGenerator.nextId("form");

        assertThat(firstId).matches("form-[0-9a-f]{8}");
        assertThat(secondId).matches("form-[0-9a-f]{8}");
        assertThat(secondId).isNotEqualTo(firstId);
    }
}
