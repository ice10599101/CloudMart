package com.cloudmart.wish.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Long→JS 安全序列化验证：雪花 ID 字符串化防精度丢失，
 * 小计数保持数字避免契约变更。
 */
class JacksonConfigTest {

    private final ObjectMapper mapper = new JacksonConfig().objectMapper();

    record Payload(Long snowflakeId, Long smallCount, long primitiveCount, String label) {}

    @Test
    @DisplayName("超出 JS 安全整数的雪花 ID 序列化为字符串")
    void snowflakeIdSerializedAsString() throws Exception {
        Payload payload = new Payload(2093809381291352065L, 5L, 7L, "x");
        String json = mapper.writeValueAsString(payload);

        assertThat(json).contains("\"snowflakeId\":\"2093809381291352065\"");
    }

    @Test
    @DisplayName("安全范围内计数保持数字（契约不变）")
    void smallCountsStayNumeric() throws Exception {
        Payload payload = new Payload(1L, 42L, 99L, "x");
        String json = mapper.writeValueAsString(payload);

        assertThat(json).contains("\"smallCount\":42");
        assertThat(json).contains("\"primitiveCount\":99");
    }

    @Test
    @DisplayName("负向越界值同样字符串化")
    void negativeOverflowSerializedAsString() throws Exception {
        Map<String, Long> value = new LinkedHashMap<>();
        value.put("v", -9_007_199_254_740_992L);

        assertThat(mapper.writeValueAsString(value)).contains("\"v\":\"-9007199254740992\"");
    }

    @Test
    @DisplayName("字符串形式的 Long 可正常反序列化回 Long")
    void stringLongDeserializes() throws Exception {
        Payload payload = mapper.readValue(
                "{\"snowflakeId\":\"2093809381291352065\",\"smallCount\":42,\"primitiveCount\":99,\"label\":\"x\"}",
                Payload.class);

        assertThat(payload.snowflakeId()).isEqualTo(2093809381291352065L);
        assertThat(payload.smallCount()).isEqualTo(42L);
        assertThat(payload.primitiveCount()).isEqualTo(99L);
    }
}
