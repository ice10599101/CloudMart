package com.cloudmart.user.config;

import com.cloudmart.common.constant.SecurityConstants;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Feign 请求拦截器：透传当前用户身份（数据字段）。
 *
 * <p>当 mall-user 通过 Feign 调用 mall-community 时，注入当前查看者 ID，
 * 供下游（如他人资料脱敏）识别查看者。服务间身份由自动装配的
 * ServiceTokenFeignInterceptor 签发 X-Service-Token 建立，
 * 裸 X-Internal-Call 头已随 SEC-01 废除。</p>
 */
@Component
public class FeignRequestInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            String userId = authentication.getName();
            if (userId != null && !"anonymousUser".equals(userId) && !"INTERNAL_SERVICE".equals(userId)) {
                template.header(SecurityConstants.USER_ID_HEADER, userId);
            }
        }
    }
}