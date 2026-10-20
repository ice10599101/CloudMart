package com.cloudmart.risk.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.risk.service.BlacklistService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T23：不是“类上有注解”的静态断言，而是在真实方法级授权代理下用认证主体调用，
 * 验证 T02 的 INTERNAL 守卫确实拒绝用户/管理员 JWT 且放行服务主体。
 */
@DisplayName("T02/T23 风控管理域真实主体授权")
class BlacklistAuthorizationTest {

    private AnnotationConfigApplicationContext context;
    private MockMvc mockMvc;
    private final BlacklistService blacklistService = mock(BlacklistService.class);

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityConfig {
    }

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext();
        context.registerBean(MethodSecurityConfig.class);
        context.registerBean(BlacklistService.class, () -> blacklistService);
        context.registerBean(BlacklistController.class);
        context.refresh();
        mockMvc = MockMvcBuilders.standaloneSetup(context.getBean(BlacklistController.class))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    private static void authenticate(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        "42", null, List.of(new SimpleGrantedAuthority(role))));
    }

    @Test
    @DisplayName("普通用户令牌 → 403，黑名单服务未被触碰")
    void userPrincipalRejected() throws Exception {
        authenticate("ROLE_USER");

        mockMvc.perform(get("/blacklist/list"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        verifyNoInteractions(blacklistService);
    }

    @Test
    @DisplayName("管理员 JWT 也不能直达风控管理域 → 403")
    void adminPrincipalRejected() throws Exception {
        authenticate("ROLE_ADMIN");

        mockMvc.perform(get("/blacklist/list"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        verifyNoInteractions(blacklistService);
    }

    @Test
    @DisplayName("内部服务主体 → 放行")
    void internalPrincipalAllowed() throws Exception {
        authenticate("ROLE_INTERNAL");
        when(blacklistService.listBlacklist(null, 1, 20)).thenReturn(new Page<>(1, 20, 0L));

        mockMvc.perform(get("/blacklist/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
