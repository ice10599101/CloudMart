package com.cloudmart.pet.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 请求级上下文（§6.1 幂等契约）：捕获客户端 {@code Idempotency-Key} 请求头。
 *
 * <p>同一按钮操作使用一个键、网络重试复用原键；写服务在构造业务操作键时优先采用
 * 该键（服务端确定性键 + 客户端键双保险），使重复提交收敛到同一笔业务操作。
 * 仅存 ThreadLocal，请求结束清理；Redis 不参与该键的持久化判定。</p>
 */
@Component
public class PetRequestContext implements HandlerInterceptor {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    /** 三端历史别名头（Web/Taro/RN 请求层现用）；目标契约统一为 Idempotency-Key */
    public static final String IDEMPOTENCY_KEY_ALIAS_HEADER = "X-Idempotency-Key";

    /** 单段幂等键长度上限：超长折叠为摘要（P02/TX-05 禁止截断碰撞） */
    private static final int CLIENT_KEY_MAX = 128;

    private static final ThreadLocal<String> IDEMPOTENCY_KEY = new ThreadLocal<>();

    /** 当前请求的幂等键；客户端未携带返回 null（调用方退回服务端确定性键） */
    public static String idempotencyKey() {
        return IDEMPOTENCY_KEY.get();
    }

    public static void setIdempotencyKey(String key) {
        IDEMPOTENCY_KEY.set(key);
    }

    public static void clear() {
        IDEMPOTENCY_KEY.remove();
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        String key = request.getHeader(IDEMPOTENCY_KEY_HEADER);
        if (key == null || key.isBlank()) {
            key = request.getHeader(IDEMPOTENCY_KEY_ALIAS_HEADER);
        }
        if (key != null && !key.isBlank()) {
            IDEMPOTENCY_KEY.set(normalize(key.strip()));
        }
        return true;
    }

    /** 超长键折叠为 前缀+SHA-256 固定摘要，保证同键重试得到同一折叠结果、不同键不碰撞 */
    private static String normalize(String key) {
        if (key.length() <= CLIENT_KEY_MAX) {
            return key;
        }
        String prefix = key.substring(0, 24);
        return prefix + ":h:" + sha256Hex(key);
    }

    private static String sha256Hex(String text) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest
                    .getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 摘要算法不可用", e);
        }
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler, Exception ex) {
        clear();
    }
}
