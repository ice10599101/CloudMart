package com.cloudmart.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 幂等性注解，标注在 Controller 方法上防止重复提交。
 *
 * <p>工作原理：通过 Redis SETNX 抢占幂等 key，首次请求放行，
 * 重复请求在 TTL 内被拦截。配合前端注入的 X-Idempotency-Key 请求头使用。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /**
     * 幂等 key 前缀，默认使用「类名#方法名」。
     * 最终 Redis key 格式：{@code idempotent:{prefix}:{idempotencyKey}}
     */
    String prefix() default "";

    /**
     * 幂等 key 的过期时间（秒），默认 30 秒。
     * TTL 应略大于接口正常响应时间，避免长耗时接口被误判为重复。
     */
    int ttl() default 30;

    /**
     * 幂等 key 的来源。
     * <ul>
     *   <li>HEADER — 从请求头 {@code X-Idempotency-Key} 获取（默认）</li>
     *   <li>SPELL — 从请求参数拼装（userId + method + params hash）</li>
     * </ul>
     */
    KeySource source() default KeySource.HEADER;

    enum KeySource {
        /** 从请求头 X-Idempotency-Key 获取 */
        HEADER,
        /** 从请求参数自动拼装 */
        SPELL
    }
}
