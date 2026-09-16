package com.cloudmart.user.config;

import com.cloudmart.common.constant.SecurityConstants;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Feign 请求拦截器：透传内部调用头与当前用户身份。
 *
 * <p>当 mall-user 通过 Feign 调用 mall-community 时，注入
 * {@code X-Internal-Call: true} 与当前查看者 ID，使下游可识别调用方身份
 * 并复用 {@code InternalCallAuthenticationFilter} 完成内部鉴权。</p>
 */
@Component
public class FeignRequestInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        template.header(SecurityConstants.INTERNAL_CALL_HEADER, "true");

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            String userId = authentication.getName();
            if (userId != null && !"anonymousUser".equals(userId) && !"INTERNAL_SERVICE".equals(userId)) {
                template.header(SecurityConstants.USER_ID_HEADER, userId);
            }
        }
    }
}