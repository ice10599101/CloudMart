package com.cloudmart.wish.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T22 契约固化：Web 端提交 toISOString()（带 Z 后缀），
 * Jackson LocalDateTime 反序列化的实际行为（实证：容忍 Z 后缀，按 UTC 墙钟落值）。
 */
class JacksonZSuffixTest {

    record Req(LocalDateTime openAt) {
    }

    @Test
    @DisplayName("带 Z 后缀的 ISO 串可解析，且按 UTC 墙钟取值；不带后缀行为不变")
    void zSuffix_parseability() throws Exception {
        ObjectMapper mapper = new JacksonConfig().objectMapper();

        Req zulu = mapper.readValue("{\"openAt\":\"2027-01-01T00:00:00.000Z\"}", Req.class);
        assertThat(zulu.openAt())
                .as("Z 后缀（UTC）按 UTC 墙钟落值——与模块 openAt=UTC 语义一致")
                .isEqualTo(LocalDateTime.of(2027, 1, 1, 0, 0, 0));

        Req plain = mapper.readValue("{\"openAt\":\"2027-01-01T00:00:00\"}", Req.class);
        assertThat(plain.openAt()).isEqualTo(LocalDateTime.of(2027, 1, 1, 0, 0, 0));
    }
}
