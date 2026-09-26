package com.cloudmart.pet.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * JSON 契约（B07 业务 ID 与时间契约统一）：
 *
 * <ul>
 *   <li><b>ID</b>：所有业务 ID 的 JSON 输出统一为字符串——雪花 ID 是 Long，客户端
 *       {@code Number(id)} 解析超安全整数会静默丢精度且不可恢复，必须从服务端输出修复。
 *       仅包装类型 Long 全量转字符串；真正的数量/等级/秒数在 VO 中声明为原生
 *       int/long（或 Integer），保持 JSON number，不盲目字符串化。</li>
 *   <li><b>时间点</b>：LocalDateTime 字段统一按 UTC 输出 RFC 3339（{@code 2026-09-26T01:00:00Z}），
 *       数据库存储与计算口径即 UTC，客户端不需要猜测时区；反序列化兼容带 Z/带偏移/
 *       无时区（无时区按 UTC 解释，兼容旧客户端输入）。</li>
 *   <li><b>业务日</b>：LocalDate 输出 {@code 2026-09-26}（业务日归属 businessZone，纯日期无时刻）。</li>
 * </ul>
 */
@Configuration
public class JacksonConfig {

    /** UTC RFC 3339 输出：2026-09-26T01:00:00Z */
    static class UtcLocalDateTimeSerializer extends JsonSerializer<LocalDateTime> {
        @Override
        public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            gen.writeString(value.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        }
    }

    /** 兼容解析：带 Z / 带偏移 / 无时区（无时区按 UTC 解释，存储口径即 UTC） */
    static class UtcLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {
        @Override
        public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            String text = parser.getText();
            if (text == null || text.isBlank()) {
                return null;
            }
            try {
                boolean hasZone = text.endsWith("Z")
                        || (text.length() > 6 && (text.charAt(text.length() - 3) == ':')
                        && (text.lastIndexOf('+') > 10 || text.lastIndexOf('-') > 10));
                if (hasZone) {
                    return OffsetDateTime.parse(text).atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
                }
                return LocalDateTime.parse(text);
            } catch (DateTimeException e) {
                try {
                    return LocalDateTime.ofInstant(Instant.parse(text), ZoneOffset.UTC);
                } catch (DateTimeParseException ignored) {
                    throw new IOException("无法解析时间: " + text, e);
                }
            }
        }
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addSerializer(Long.class, ToStringSerializer.instance);
        javaTimeModule.addSerializer(Long.TYPE, ToStringSerializer.instance);
        javaTimeModule.addSerializer(LocalDateTime.class, new UtcLocalDateTimeSerializer());
        javaTimeModule.addDeserializer(LocalDateTime.class, new UtcLocalDateTimeDeserializer());
        javaTimeModule.addSerializer(LocalDate.class, new LocalDateSerializer(DateTimeFormatter.ISO_LOCAL_DATE));
        javaTimeModule.addDeserializer(LocalDate.class, new LocalDateDeserializer(DateTimeFormatter.ISO_LOCAL_DATE));
        mapper.registerModule(javaTimeModule);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
