package com.cloudmart.pet.config;

import com.cloudmart.common.constant.SecurityConstants;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Feign 请求拦截器（与 mall-wish 同构）：透传内部调用头与用户身份。
 *
 * <p>当 mall-pet 通过 Feign 调用 mall-wish（捞瓶/星光）/mall-notification（提醒）
 * 时，注入 {@code X-Internal-Call: true} 和当前用户 ID，使下游服务复用
 * {@code InternalCallAuthenticationFilter} 完成认证。</p>
 */
@Component
public class FeignRequestInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        template.header(SecurityConstants.INTERNAL_CALL_HEADER, "true");

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            String userId = authentication.getName();
            if (userId != null && !"anonymousUser".equals(userId)) {
                template.header(SecurityConstants.USER_ID_HEADER, userId);
            }
        }
    }
}
