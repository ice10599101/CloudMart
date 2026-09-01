package com.cloudmart.common.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JsSafeLongSerializer 行为验证：
 * 雪花 ID（超出 JS 安全整数范围）→ 字符串；普通数值（分页 total、金额分）→ 保持 number。
 */
class JsSafeLongSerializerTest {

    private JsonMapper jsonMapper;

    record Payload(Long snowflakeId, Long totalCount, long primitiveCount, Long nullValue) {}

    @BeforeEach
    void setUp() {
        SimpleModule module = new SimpleModule("jsSafeLong");
        module.addSerializer(Long.class, new JsSafeLongSerializer());
        module.addSerializer(Long.TYPE, new JsSafeLongSerializer());
        jsonMapper = JsonMapper.builder().addModule(module).build();
    }

    @Test
    @DisplayName("雪花 ID 超出 JS 安全范围时序列化为字符串，数值无损")
    void snowflakeIdSerializedAsString() {
        String json = jsonMapper.writeValueAsString(2093353998403764226L);
        assertThat(json).isEqualTo("\"2093353998403764226\"");
    }

    @Test
    @DisplayName("安全范围内的 Long 保持 number 序列化")
    void smallLongRemainsNumber() {
        assertThat(jsonMapper.writeValueAsString(100L)).isEqualTo("100");
        assertThat(jsonMapper.writeValueAsString(0L)).isEqualTo("0");
        assertThat(jsonMapper.writeValueAsString(-123L)).isEqualTo("-123");
        // 边界值：JS Number.MAX_SAFE_INTEGER 仍为 number
        assertThat(jsonMapper.writeValueAsString(9_007_199_254_740_991L)).isEqualTo("9007199254740991");
    }

    @Test
    @DisplayName("null Long 序列化为 null 而非字符串 \"null\"")
    void nullLongSerializedAsNull() {
        assertThat(jsonMapper.writeValueAsString(null)).isEqualTo("null");
        String json = jsonMapper.writeValueAsString(new Payload(1L, 2L, 3L, null));
        assertThat(json).contains("\"nullValue\":null");
    }

    @Test
    @DisplayName("record 混合字段：雪花 ID 字符串、计数字段 number")
    void mixedRecordFields() {
        Payload payload = new Payload(2093353998403764226L, 100L, 200L, null);
        String json = jsonMapper.writeValueAsString(payload);
        assertThat(json)
                .contains("\"snowflakeId\":\"2093353998403764226\"")
                .contains("\"totalCount\":100")
                .contains("\"primitiveCount\":200")
                .contains("\"nullValue\":null");
    }

    @Test
    @DisplayName("反序列化兼容：字符串形式的 Long 可被 Jackson 读回（Feign 调用方适配）")
    void deserializationAcceptsStringLong() {
        Payload payload = jsonMapper.readValue(
                "{\"snowflakeId\":\"2093353998403764226\",\"totalCount\":100,\"primitiveCount\":200,\"nullValue\":null}",
                Payload.class);
        assertThat(payload.snowflakeId()).isEqualTo(2093353998403764226L);
        assertThat(payload.totalCount()).isEqualTo(100L);
        assertThat(payload.primitiveCount()).isEqualTo(200L);
        assertThat(payload.nullValue()).isNull();
    }
}
