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
        if (key != null && !key.isBlank()) {
            // 键长度收敛：超长键截断哈希语义由服务端键组装处理，这里仅做基础防护
            IDEMPOTENCY_KEY.set(key.strip());
        }
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler, Exception ex) {
        clear();
    }
}
