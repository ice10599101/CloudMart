package com.cloudmart.admin.config;

import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.common.context.AdminSecurityContext;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.stereotype.Component;

@Component
public class AdminFeignInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        template.header(SecurityConstants.INTERNAL_CALL_HEADER, "true");

        AdminSecurityContext ctx = AdminSecurityContext.get();
        if (ctx == null) {
            return;
        }
        template.header(SecurityConstants.USER_ID_HEADER, ctx.userId().toString());
        if (ctx.username() != null) {
            template.header(SecurityConstants.ADMIN_USERNAME_HEADER, ctx.username());
        }
        if (ctx.deptId() != null) {
            template.header(SecurityConstants.ADMIN_DEPT_ID_HEADER, ctx.deptId().toString());
        }
    }
}
