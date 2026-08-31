package com.cloudmart.wish.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * Jackson 配置。
 *
 * <p>统一时间序列化：所有 {@code LocalDateTime} 字段以 ISO 8601 字符串输出
 * （{@code yyyy-MM-dd'T'HH:mm:ss}），不输出时间戳。配合前端 day.js 直接解析。</p>
 *
 * <p>Long 精度保护：本模块主键为雪花 ID（19 位，约 2e18），超出 JS
 * {@code Number.MAX_SAFE_INTEGER}（2^53-1），数字形式下发会在前端 JSON.parse
 * 时末几位被舍入，导致按 ID 的详情/流转/奖励等操作全部打偏。</p>
 *
 * <p>处理策略：<b>仅超出安全范围的值</b>序列化为字符串（即雪花 ID）；
 * 小值（计数、枚举序号等）保持数字，避免不必要的 API 契约变更。
 * 前端 ID 类型统一声明为 {@code number | string}，两种形态兼容。</p>
 */
@Configuration
public class JacksonConfig {

    /** JS Number.MAX_SAFE_INTEGER = 2^53 - 1 = 9007199254740991 */
    private static final long JS_SAFE_MAX = 9_007_199_254_740_991L;

    /** 超出 JS 安全整数范围的 Long 序列化为字符串，其余保持数字 */
    static class JsSafeLongSerializer extends JsonSerializer<Long> {
        private static final ToStringSerializer STRING_SERIALIZER = ToStringSerializer.instance;

        @Override
        public void serialize(Long value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            if (value > JS_SAFE_MAX || value < -JS_SAFE_MAX) {
                STRING_SERIALIZER.serialize(value, gen, serializers);
            } else {
                gen.writeNumber(value);
            }
        }
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addSerializer(Long.class, new JsSafeLongSerializer());
        javaTimeModule.addSerializer(Long.TYPE, new JsSafeLongSerializer());
        mapper.registerModule(javaTimeModule);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
