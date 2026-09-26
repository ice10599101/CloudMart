package com.cloudmart.pet.config;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** T16/B07 契约验证（Jackson 3 / Spring Boot 4 MVC 实际路径）：Long→字符串、LocalDateTime→RFC3339 UTC。 */
class JacksonConfigContractTest {

    @Test
    void longIdsAlwaysStringAndTimeWithZ() {
        JsonMapper.Builder builder = JsonMapper.builder();
        new PetJsonMapperCustomizer().petB07JsonContractCustomizer().customize(builder);
        JsonMapper mapper = builder.build();

        record Sample(Long id, Long count, LocalDateTime time) {
        }

        String json = mapper.writeValueAsString(new Sample(2103814471804837889L, 5L,
                LocalDateTime.of(2026, 9, 26, 11, 56, 12)));

        System.out.println("JACKSON3_OUT=" + json);
        assertThat(json).contains("\"id\":\"2103814471804837889\"");
        assertThat(json).contains("\"count\":\"5\"");
        assertThat(json).contains("\"time\":\"2026-09-26T11:56:12Z\"");

        // 反序列化兼容：带 Z / 带偏移 / 无时区（按 UTC 解释）
        Sample parsed = mapper.readValue(
                "{\"id\":\"2103814471804837889\",\"count\":\"5\",\"time\":\"2026-09-26T19:56:12+08:00\"}",
                Sample.class);
        assertThat(parsed.time()).isEqualTo(LocalDateTime.of(2026, 9, 26, 11, 56, 12));
    }
}
