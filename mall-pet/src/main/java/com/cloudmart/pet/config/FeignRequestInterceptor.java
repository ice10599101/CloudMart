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
 * 时，注入 {@code X-Internal-Call: true} 和当前用户 ID；受保护下游端点另由
 * {@code WishServiceTokenConfig} 签发的服务令牌完成认证。SEC-01 后用户 ID 只在
 * principal 为数字用户标识（用户请求）时转发——服务身份请求（principal=调用方服务名，
 * 如 mall-admin 代理的管理操作）不携带，避免把服务名当用户 ID 外传。</p>
 */
@Component
public class FeignRequestInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        template.header(SecurityConstants.INTERNAL_CALL_HEADER, "true");

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            String principal = authentication.getName();
            if (isNumericUserId(principal)) {
                template.header(SecurityConstants.USER_ID_HEADER, principal);
            }
        }
    }

    /** 用户主键为雪花数字 ID；服务身份（mall-admin/mall-job）不含数字以外字符 */
    private static boolean isNumericUserId(String principal) {
        if (principal == null || principal.isEmpty() || "anonymousUser".equals(principal)) {
            return false;
        }
        for (int i = 0; i < principal.length(); i++) {
            if (!Character.isDigit(principal.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
