package com.cloudmart.common.config;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/**
 * 防止 Long 雪花 ID 在浏览器端精度丢失的序列化器。
 *
 * <p>JS Number 采用 IEEE 754 双精度浮点，安全整数范围为 [-2^53+1, 2^53-1]（约 9.007e15），
 * 而雪花 ID 为 19 位十进制数（恒超出该范围），直接以 JSON number 下发会被浏览器
 * JSON.parse 静默截断（尾数变 000），导致按 ID 的编辑/删除请求 404 或指向错误记录。
 *
 * <p>策略：超出安全范围的值序列化为字符串（雪花 ID 恒定命中此分支）；安全范围内的
 * 值（分页 total、数量、金额分等）保持 number，前端现有数值语义零改动。
 * 反序列化方向 Jackson 默认支持 "123" 字符串到 Long 的宽松转换，Feign 调用方无需适配。
 */
public class JsSafeLongSerializer extends ValueSerializer<Long> {

    /** JS Number.MAX_SAFE_INTEGER = 2^53 - 1 */
    private static final long JS_SAFE_MAX = 9_007_199_254_740_991L;

    @Override
    public void serialize(Long value, JsonGenerator gen, SerializationContext ctxt) {
        if (value == null) {
            gen.writeNull();
            return;
        }
        if (value > JS_SAFE_MAX || value < -JS_SAFE_MAX) {
            gen.writeString(Long.toString(value));
        } else {
            gen.writeNumber(value);
        }
    }
}
