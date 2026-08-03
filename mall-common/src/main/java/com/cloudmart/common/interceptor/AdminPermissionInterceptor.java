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

@Component
public class AdminPermissionInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AdminPermissionInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        AdminSecurityContext context = AdminSecurityContext.get();
        if (context == null) {
            log.warn("Permission check failed: no AdminSecurityContext for {} {}",
                    request.getMethod(), request.getRequestURI());
            throw new BusinessException("UNAUTHORIZED", "未登录或登录已过期");
        }

        RequiresAdmin requiresAdmin = handlerMethod.getMethodAnnotation(RequiresAdmin.class);
        if (requiresAdmin == null) {
            requiresAdmin = handlerMethod.getBeanType().getAnnotation(RequiresAdmin.class);
        }
        if (requiresAdmin != null && !"admin".equals(context.role())) {
            throw new BusinessException("FORBIDDEN", "需要管理员权限");
        }

        RequiresPermission requiresPermission = handlerMethod.getMethodAnnotation(RequiresPermission.class);
        if (requiresPermission == null) {
            requiresPermission = handlerMethod.getBeanType().getAnnotation(RequiresPermission.class);
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
