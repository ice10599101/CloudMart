package com.cloudmart.pet.config;

import com.cloudmart.common.api.ApiResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T16/B07/P03 契约验证（Jackson 3 / Spring Boot 4 MVC 实际路径）：
 * Long 业务 ID → 字符串（雪花 ID 超 JS 安全整数）；数量/total/count（Integer）→ JSON number；
 * LocalDateTime → RFC3339 UTC。
 */
class JacksonConfigContractTest {

    @Test
    void longIdsAlwaysStringAndTimeWithZ() {
        JsonMapper.Builder builder = JsonMapper.builder();
        new PetJsonMapperCustomizer().petB07JsonContractCustomizer().customize(builder);
        JsonMapper mapper = builder.build();

        record Sample(Long id, Integer count, LocalDateTime time) {
        }

        String json = mapper.writeValueAsString(new Sample(2103814471804837889L, 5,
                LocalDateTime.of(2026, 9, 26, 11, 56, 12)));

        System.out.println("JACKSON3_OUT=" + json);
        assertThat(json).contains("\"id\":\"2103814471804837889\"");
        // P03/§3.5：数量是 number，不是字符串
        assertThat(json).contains("\"count\":5");
        assertThat(json).contains("\"time\":\"2026-09-26T11:56:12Z\"");

        // 反序列化兼容：带 Z / 带偏移 / 无时区（按 UTC 解释）
        Sample parsed = mapper.readValue(
                "{\"id\":\"2103814471804837889\",\"count\":5,\"time\":\"2026-09-26T19:56:12+08:00\"}",
                Sample.class);
        assertThat(parsed.time()).isEqualTo(LocalDateTime.of(2026, 9, 26, 11, 56, 12));
    }

    @Test
    void paginationMeta_totalIsNumber() {
        JsonMapper.Builder builder = JsonMapper.builder();
        new PetJsonMapperCustomizer().petB07JsonContractCustomizer().customize(builder);
        JsonMapper mapper = builder.build();

        record Item(Long id) {
        }

        String json = mapper.writeValueAsString(
                ApiResponse.ok(new Item[]{new Item(2103814471804837889L)}, 1, 20, 100));

        // 信封分页契约：ID 为字符串、meta.total 为 number（P03/§3.5）
        assertThat(json).contains("\"id\":\"2103814471804837889\"");
        assertThat(json).contains("\"page\":1");
        assertThat(json).contains("\"pageSize\":20");
        assertThat(json).contains("\"total\":100");
        assertThat(json).doesNotContain("\"total\":\"100\"");
    }
}
