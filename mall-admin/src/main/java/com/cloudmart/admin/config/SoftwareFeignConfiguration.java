package com.cloudmart.admin.config;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 小答软件管理端 Feign 专用配置：把管理后台入站请求的 {@code Idempotency-Key}
 * 原样透传给 mall-software。
 *
 * <p>为什么必须显式透传：mall-admin 的管理端方法只把 {@code @RequestBody} 转发给
 * 下游，Feign 默认不会继承 Servlet 请求头。缺失时 mall-software 会以
 * {@code Idempotency-Key 须为 1~128 字符} 拒绝全部写操作（曾被误判为 502）。
 * 幂等键统一走头部是手册 6.14/7.5 与前端/其余服务共同的契约，不放在请求体里再留一条路。</p>
 *
 * <p>作用域：仅挂在 {@code SoftwareFeignClient} 的 configuration 上（Feign 子容器），
 * 不会被主容器扫描为全局拦截器，避免把管理后台头部泄漏给其它下游服务。</p>
 */
@Configuration(proxyBeanMethods = false)
public class SoftwareFeignConfiguration {

    /** 幂等键头部名（与 mall-software / 前端契约一致） */
    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    @Bean
    RequestInterceptor idempotencyKeyRelayInterceptor() {
        return template -> {
            String key = currentRequestHeader(IDEMPOTENCY_KEY_HEADER);
            if (key != null && !key.isBlank()) {
                template.header(IDEMPOTENCY_KEY_HEADER, key.trim());
            }
        };
    }

    private String currentRequestHeader(String name) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return null;
        }
        return servletAttributes.getRequest().getHeader(name);
    }
}
