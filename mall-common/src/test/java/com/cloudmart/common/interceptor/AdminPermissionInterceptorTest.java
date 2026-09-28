package com.cloudmart.common.interceptor;

import com.cloudmart.common.annotation.RequiresAdmin;
import com.cloudmart.common.annotation.RequiresPermission;
import com.cloudmart.common.context.AdminSecurityContext;
import com.cloudmart.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SEC-01 管理权限拦截器：只对显式声明 @RequiresAdmin / @RequiresPermission 的
 * 处理方法生效——普通 USER/SERVICE 请求（无管理注解端点）不再被误拦。
 */
@DisplayName("AdminPermissionInterceptor 显式管理注解拦截")
class AdminPermissionInterceptorTest {

    private final AdminPermissionInterceptor interceptor = new AdminPermissionInterceptor();

    @BeforeEach
    void setUp() {
        AdminSecurityContext.clear();
    }

    @AfterEach
    void tearDown() {
        AdminSecurityContext.clear();
    }

    static class SampleController {

        @RequiresAdmin
        public void adminOnly() {
        }

        @RequiresPermission("business:refund:retry")
        public void refundRetry() {
        }

        public void open() {
        }
    }

    @RequiresPermission("catalog:manage")
    static class ClassAnnotatedController {

        public void any() {
        }
    }

    private HandlerMethod handler(Class<?> beanType, String methodName) throws Exception {
        Method method = beanType.getMethod(methodName);
        return new HandlerMethod(beanType.getDeclaredConstructor().newInstance(), method);
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/business/1");
        request.setRequestURI("/admin/business/1");
        return request;
    }

    private void loginAdmin(long userId, Set<String> permissions) {
        AdminSecurityContext.set(new AdminSecurityContext(userId, "ops", "admin", permissions, 3L));
    }

    @Test
    @DisplayName("无管理注解的方法：无管理员上下文也放行（修复普通请求误拦）")
    void methodWithoutAdminAnnotations_passesWithoutAdminContext() throws Exception {
        boolean allowed = interceptor.preHandle(request(), new MockHttpServletResponse(),
                handler(SampleController.class, "open"));

        assertThat(allowed).isTrue();
    }

    @Test
    @DisplayName("非 HandlerMethod（资源处理器）直接放行")
    void nonHandlerMethod_passes() {
        boolean allowed = interceptor.preHandle(request(), new MockHttpServletResponse(), new Object());

        assertThat(allowed).isTrue();
    }

    @Test
    @DisplayName("带 @RequiresAdmin 的方法：无上下文 → UNAUTHORIZED")
    void adminMethodWithoutContext_rejected() throws Exception {
        assertThatThrownBy(() -> interceptor.preHandle(request(), new MockHttpServletResponse(),
                handler(SampleController.class, "adminOnly")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "UNAUTHORIZED");
    }

    @Test
    @DisplayName("带 @RequiresAdmin 的方法：普通用户上下文 → FORBIDDEN")
    void adminMethodWithUserRole_forbidden() throws Exception {
        // USER 令牌即使错误地携带了管理员上下文，role != admin 也必须拒绝
        AdminSecurityContext.set(new AdminSecurityContext(42L, "alice", "user", Set.of(), null));

        assertThatThrownBy(() -> interceptor.preHandle(request(), new MockHttpServletResponse(),
                handler(SampleController.class, "adminOnly")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "FORBIDDEN");
    }

    @Test
    @DisplayName("SEC-03：已认证但非管理员的 principal（无上下文）→ FORBIDDEN 而非 401")
    void adminMethodWithAuthenticatedNonAdmin_forbidden() throws Exception {
        // USER 令牌由 UserJwtAuthenticationFilter 建立 ROLE_USER，不产生 AdminSecurityContext
        org.springframework.security.authentication.UsernamePasswordAuthenticationToken authentication =
                org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
                        "42", null, java.util.List.of());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(authentication);

        try {
            assertThatThrownBy(() -> interceptor.preHandle(request(), new MockHttpServletResponse(),
                    handler(SampleController.class, "adminOnly")))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", "FORBIDDEN");
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("带 @RequiresAdmin 的方法：管理员上下文放行")
    void adminMethodWithAdminContext_allowed() throws Exception {
        loginAdmin(7L, Set.of());

        boolean allowed = interceptor.preHandle(request(), new MockHttpServletResponse(),
                handler(SampleController.class, "adminOnly"));

        assertThat(allowed).isTrue();
    }

    @Test
    @DisplayName("权限码缺失 → FORBIDDEN，权限码匹配 → 放行")
    void requiresPermission_enforcedByPermissionCode() throws Exception {
        loginAdmin(7L, Set.of("monitor:job:list"));

        assertThatThrownBy(() -> interceptor.preHandle(request(), new MockHttpServletResponse(),
                handler(SampleController.class, "refundRetry")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "FORBIDDEN");

        loginAdmin(7L, Set.of("business:refund:retry"));
        boolean allowed = interceptor.preHandle(request(), new MockHttpServletResponse(),
                handler(SampleController.class, "refundRetry"));
        assertThat(allowed).isTrue();
    }

    @Test
    @DisplayName("类级 @RequiresPermission 同样生效")
    void classLevelAnnotation_enforced() throws Exception {
        assertThatThrownBy(() -> interceptor.preHandle(request(), new MockHttpServletResponse(),
                handler(ClassAnnotatedController.class, "any")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "UNAUTHORIZED");

        loginAdmin(7L, Set.of("catalog:manage"));
        boolean allowed = interceptor.preHandle(request(), new MockHttpServletResponse(),
                handler(ClassAnnotatedController.class, "any"));
        assertThat(allowed).isTrue();
    }
}
