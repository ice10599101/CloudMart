package com.cloudmart.pet.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** T16/B07 序列化契约验证：Long→字符串、LocalDateTime→RFC3339 UTC(Z)。 */
class JacksonConfigContractTest {

    @Test
    void longIdsAlwaysStringAndTimeWithZ() throws Exception {
        ObjectMapper mapper = new JacksonConfig().objectMapper();

        record Sample(Long id, Long count, LocalDateTime time) {
        }

        String json = mapper.writeValueAsString(new Sample(2103814471804837889L, 5L,
                LocalDateTime.of(2026, 9, 26, 11, 56, 12)));

        System.out.println("JACKSON_OUT=" + json);
        assertThat(json).contains("\"id\":\"2103814471804837889\"");
        assertThat(json).contains("\"count\":\"5\"");
        assertThat(json).contains("\"time\":\"2026-09-26T11:56:12Z\"");
    }
}
