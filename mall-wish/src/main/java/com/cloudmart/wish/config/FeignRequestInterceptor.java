package com.cloudmart.wish.config;

import com.cloudmart.common.constant.SecurityConstants;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Feign 请求拦截器：透传内部调用头与用户身份。
 *
 * <p>当 mall-wish 通过 Feign 调用 mall-user / mall-community / mall-file 时，
 * 注入 {@code X-Internal-Call: true} 和当前用户 ID，使下游服务可识别调用方身份
 * 下游服务端点按自身安全策略处理（B01 后 mall-wish 不再信任裸头）。</p>
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
