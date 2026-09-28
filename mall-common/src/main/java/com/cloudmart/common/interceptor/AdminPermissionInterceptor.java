package com.cloudmart.common.interceptor;

import com.cloudmart.common.annotation.RequiresAdmin;
import com.cloudmart.common.annotation.RequiresPermission;
import com.cloudmart.common.context.AdminSecurityContext;
import com.cloudmart.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 管理权限拦截器（SEC-01）：只对显式声明 {@link RequiresAdmin} /
 * {@link RequiresPermission} 的处理方法生效。
 *
 * <p>修正项：此前在判断注解之前先要求 AdminSecurityContext 存在，导致注册了本
 * 拦截器（{@code /**}）的服务里普通 USER/SERVICE 请求一律被拒绝。现在无管理注解
 * 的端点直接放行，由各自 SecurityFilterChain 与对象授权（SEC-04）兜底；带管理注解
 * 的端点仍强制管理员上下文与权限码——不能通过删除拦截器恢复可用性，否则会重新
 * 暴露管理端点的授权缺口。</p>
 */
@Component
public class AdminPermissionInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AdminPermissionInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequiresAdmin requiresAdmin = handlerMethod.getMethodAnnotation(RequiresAdmin.class);
        if (requiresAdmin == null) {
            requiresAdmin = handlerMethod.getBeanType().getAnnotation(RequiresAdmin.class);
        }
        RequiresPermission requiresPermission = handlerMethod.getMethodAnnotation(RequiresPermission.class);
        if (requiresPermission == null) {
            requiresPermission = handlerMethod.getBeanType().getAnnotation(RequiresPermission.class);
        }
        // 非管理端点：匿名/USER/SERVICE 请求由 SecurityFilterChain 与对象授权处理
        if (requiresAdmin == null && requiresPermission == null) {
            return true;
        }

        AdminSecurityContext context = AdminSecurityContext.get();
        if (context == null) {
            // 区分未认证与已认证但非管理员：匿名 → 401；USER/SERVICE 令牌 → 403
            org.springframework.security.core.Authentication authentication =
                    org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            boolean isAuthenticated = authentication != null && authentication.isAuthenticated()
                    && !(authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken);
            if (isAuthenticated) {
                log.warn("Admin access denied for authenticated non-admin principal={} for {} {}",
                        authentication.getName(), request.getMethod(), request.getRequestURI());
                throw new BusinessException("FORBIDDEN", "需要管理员权限");
            }
            log.warn("Permission check failed: no AdminSecurityContext for {} {}",
                    request.getMethod(), request.getRequestURI());
            throw new BusinessException("UNAUTHORIZED", "未登录或登录已过期");
        }

        if (requiresAdmin != null && !"admin".equals(context.role())) {
            throw new BusinessException("FORBIDDEN", "需要管理员权限");
        }

        if (requiresPermission != null && !context.hasPermission(requiresPermission.value())) {
            log.warn("Permission denied: userId={} requires={} has={} for {} {}",
                    context.userId(), requiresPermission.value(), context.permissions(),
                    request.getMethod(), request.getRequestURI());
            throw new BusinessException("FORBIDDEN", "没有操作权限：" + requiresPermission.value());
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AdminSecurityContext.clear();
    }
}
