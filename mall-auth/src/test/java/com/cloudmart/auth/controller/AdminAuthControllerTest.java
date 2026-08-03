package com.cloudmart.auth.controller;

import com.cloudmart.auth.dto.LoginRequest;
import com.cloudmart.auth.dto.LoginResponse;
import com.cloudmart.auth.dto.RefreshRequest;
import com.cloudmart.auth.service.AdminAuthService;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminAuthControllerTest {

    private MockMvc mockMvc;

    private final AdminAuthService adminAuthService = Mockito.mock(AdminAuthService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminAuthController(adminAuthService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @Nested
    @DisplayName("POST /admin/login")
    class LoginTests {

        @Test
        @DisplayName("管理员登录成功返回信封格式")
        void login_ShouldReturnSuccessEnvelope() throws Exception {
            LoginResponse response = new LoginResponse(
                    "admin-access", "admin-refresh", "Bearer", 3600L, "admin-token-id");
            given(adminAuthService.login(any(LoginRequest.class), any())).willReturn(response);

            mockMvc.perform(post("/admin/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new LoginRequest("admin", "admin123"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.accessToken").value("admin-access"))
                    .andExpect(jsonPath("$.data.refreshToken").value("admin-refresh"))
                    .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.data.expiresIn").value(3600))
                    .andExpect(jsonPath("$.data.tokenId").value("admin-token-id"));
        }

        @Test
        @DisplayName("管理员登录失败返回错误信封")
        void login_WhenAuthFailed_ShouldReturnErrorEnvelope() throws Exception {
            willThrow(new BusinessException("AUTH_FAILED", "管理员认证失败"))
                    .given(adminAuthService).login(any(LoginRequest.class), any());

            mockMvc.perform(post("/admin/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new LoginRequest("admin", "wrong"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("AUTH_FAILED"))
                    .andExpect(jsonPath("$.error.message").value("管理员认证失败"));
        }

        @Test
        @DisplayName("管理员登录请求体校验失败返回VALIDATION_ERROR")
        void login_WhenInvalidInput_ShouldReturnValidationError() throws Exception {
            mockMvc.perform(post("/admin/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"account\":\"\",\"password\":\"\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
    }

    @Nested
    @DisplayName("POST /admin/refresh")
    class RefreshTests {

        @Test
        @DisplayName("管理员刷新Token成功返回信封格式")
        void refresh_ShouldReturnSuccessEnvelope() throws Exception {
            LoginResponse response = new LoginResponse(
                    "new-admin-access", "new-admin-refresh", "Bearer", 3600L, "new-admin-token-id");
            given(adminAuthService.refresh("admin-refresh-token")).willReturn(response);

            mockMvc.perform(post("/admin/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RefreshRequest("admin-refresh-token"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.accessToken").value("new-admin-access"))
                    .andExpect(jsonPath("$.data.refreshToken").value("new-admin-refresh"));
        }

        @Test
        @DisplayName("管理员刷新Token无效返回错误信封")
        void refresh_WhenInvalidToken_ShouldReturnErrorEnvelope() throws Exception {
            willThrow(new BusinessException("INVALID_REFRESH_TOKEN", "刷新令牌无效"))
                    .given(adminAuthService).refresh("bad-admin-refresh");

            mockMvc.perform(post("/admin/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RefreshRequest("bad-admin-refresh"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
        }
    }

    @Nested
    @DisplayName("POST /admin/logout")
    class LogoutTests {

        @Test
        @DisplayName("管理员登出成功返回信封格式")
        void logout_ShouldReturnSuccessEnvelope() throws Exception {
            willDoNothing().given(adminAuthService).logout(1L);

            mockMvc.perform(post("/admin/logout"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }
}
