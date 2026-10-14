package com.cloudmart.pet.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * HTTP JSON 契约定制（B07）——Spring Boot 4 的 MVC 序列化走 Jackson 3（tools.jackson），
 * 不经过 mall-pet 的 Jackson 2 {@link JacksonConfig}；mall-common 的 JsSafeLong 定制器
 * 只把超安全整数转字符串。本定制器（经 `common.jsafe-long.enabled=false` 取代前者）
 * 把宠物模块的 B07 契约完整落到 HTTP 层：
 *
 * <ul>
 *   <li>业务 ID：Long 一律输出字符串（雪花 ID 超 JS 安全整数，客户端 Number 解析丢精度）；</li>
 *   <li>时间点：LocalDateTime 统一 RFC 3339 UTC（{@code 2026-09-26T01:00:00Z}）。
 *       入参必须携带时区（{@code Z} 或显式偏移，如 {@code +08:00}），服务端归一到 UTC；
 *       无时区裸时间（C24 已移除旧客户端兼容）直接判 400，禁止静默猜时区。</li>
 * </ul>
 *
 * <p>@Order(LOWEST_PRECEDENCE) 保证本定制器最后应用、覆盖 common 的 JsSafeLong 注册。</p>
 */
@Configuration
public class PetJsonMapperCustomizer {

    static final DateTimeFormatter RFC3339_UTC = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    /** Long → 字符串（B07：所有业务 ID 一律字符串；数量/等级用原生 int/long 之外的字段类型表达） */
    static class LongToStringSerializer extends ValueSerializer<Long> {
        @Override
        public void serialize(Long value, JsonGenerator gen, SerializationContext ctxt) {
            gen.writeString(String.valueOf(value));
        }
    }

    /** LocalDateTime → RFC 3339 UTC（输出带 Z） */
    static class UtcLocalDateTimeSerializer extends ValueSerializer<LocalDateTime> {
        @Override
        public void serialize(LocalDateTime value, JsonGenerator gen, SerializationContext ctxt) {
            gen.writeString(value.atOffset(ZoneOffset.UTC).format(RFC3339_UTC));
        }
    }

    /**
     * 严格解析（C24/R22）：仅接受带时区的 RFC 3339（{@code Z} 或显式偏移），归一到 UTC。
     * 无时区裸时间不再按 UTC 猜测，直接报 400——客户端改时区不能改变时间点语义。
     */
    static class UtcLocalDateTimeDeserializer extends ValueDeserializer<LocalDateTime> {
        @Override
        public LocalDateTime deserialize(JsonParser parser, DeserializationContext ctxt) {
            String text = parser.getString();
            if (text == null || text.isBlank()) {
                return null;
            }
            try {
                // ISO_OFFSET_DATE_TIME 同时接受 ...Z 与 ...+08:00；解析成功即携带时区
                return OffsetDateTime.parse(text).atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
            } catch (DateTimeParseException offsetFailure) {
                try {
                    // 语法是合法本地时间但缺时区：明确拒绝，而非静默按 UTC 解释
                    LocalDateTime.parse(text);
                    throw ctxt.weirdStringException(text, LocalDateTime.class,
                            "时间戳必须携带时区偏移（RFC 3339，如 2026-09-26T01:00:00Z 或 "
                                    + "2026-09-26T09:00:00+08:00），禁止无时区裸时间");
                } catch (DateTimeParseException ignored) {
                    throw ctxt.weirdStringException(text, LocalDateTime.class,
                            "不是合法的 RFC 3339 时间戳");
                }
            }
        }
    }

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    public JsonMapperBuilderCustomizer petB07JsonContractCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule("petB07Contract");
            module.addSerializer(Long.class, new LongToStringSerializer());
            module.addSerializer(Long.TYPE, new LongToStringSerializer());
            module.addSerializer(LocalDateTime.class, new UtcLocalDateTimeSerializer());
            module.addDeserializer(LocalDateTime.class, new UtcLocalDateTimeDeserializer());
            builder.addModule(module);
        };
    }
}
