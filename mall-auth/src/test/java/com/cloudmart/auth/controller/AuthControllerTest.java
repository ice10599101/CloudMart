package com.cloudmart.auth.controller;

import com.cloudmart.auth.dto.LoginRequest;
import com.cloudmart.auth.dto.LoginResponse;
import com.cloudmart.auth.dto.RefreshRequest;
import com.cloudmart.auth.service.AdminAuthService;
import com.cloudmart.auth.service.AuthService;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private MockMvc mockMvc;

    private final AuthService authService = Mockito.mock(AuthService.class);
    private final AdminAuthService adminAuthService = Mockito.mock(AdminAuthService.class);
    private final JWKSource<SecurityContext> jwkSource = Mockito.mock(JWKSource.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new AuthController(authService),
                        new JwksController(jwkSource),
                        new AdminAuthController(adminAuthService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("POST /login - 登录成功返回信封格式")
    void login_ShouldReturnSuccessEnvelope() throws Exception {
        LoginResponse response = new LoginResponse(
                "access-token", "refresh-token", "Bearer", 3600L, "token-id");
        given(authService.login(any(LoginRequest.class))).willReturn(response);

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("user1", "pass123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(3600))
                .andExpect(jsonPath("$.data.tokenId").value("token-id"));
    }

    @Test
    @DisplayName("POST /login - 登录失败返回错误信封")
    void login_WhenAuthFailed_ShouldReturnErrorEnvelope() throws Exception {
        willThrow(new BusinessException("AUTH_FAILED", "用户名或密码错误"))
                .given(authService).login(any(LoginRequest.class));

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("user1", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_FAILED"))
                .andExpect(jsonPath("$.error.message").value("用户名或密码错误"));
    }

    @Test
    @DisplayName("POST /login - 请求体校验失败返回VALIDATION_ERROR")
    void login_WhenInvalidInput_ShouldReturnValidationError() throws Exception {
        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /refresh - 刷新Token成功返回信封格式")
    void refresh_ShouldReturnSuccessEnvelope() throws Exception {
        LoginResponse response = new LoginResponse(
                "new-access", "new-refresh", "Bearer", 3600L, "new-token-id");
        given(authService.refresh(any(RefreshRequest.class))).willReturn(response);

        mockMvc.perform(post("/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest("old-refresh-token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("new-access"))
                .andExpect(jsonPath("$.data.refreshToken").value("new-refresh"));
    }

    @Test
    @DisplayName("POST /refresh - 无效RefreshToken返回错误信封")
    void refresh_WhenInvalidToken_ShouldReturnErrorEnvelope() throws Exception {
        willThrow(new BusinessException("INVALID_REFRESH_TOKEN", "刷新令牌无效"))
                .given(authService).refresh(any(RefreshRequest.class));

        mockMvc.perform(post("/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest("bad-token"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("POST /logout - 登出成功返回信封格式")
    void logout_ShouldReturnSuccessEnvelope() throws Exception {
        willDoNothing().given(authService).logout(1L);

        mockMvc.perform(post("/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("GET /oauth2/jwks - 获取JWKS成功")
    void jwks_ShouldReturnJwkSet() throws Exception {
        com.nimbusds.jose.jwk.JWK jwk = Mockito.mock(com.nimbusds.jose.jwk.JWK.class);
        given(jwkSource.get(any(JWKSelector.class), any())).willReturn(List.of(jwk));

        mockMvc.perform(get("/oauth2/jwks"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /oauth2/jwks - JWK源异常返回错误信封")
    void jwks_WhenJwkSourceFails_ShouldReturnErrorEnvelope() throws Exception {
        given(jwkSource.get(any(JWKSelector.class), any()))
                .willThrow(new RuntimeException("JWK source error"));

        mockMvc.perform(get("/oauth2/jwks"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("JWK_LOAD_FAILED"));
    }

    @Test
    @DisplayName("POST /admin/login - 管理员登录成功返回信封格式")
    void adminLogin_ShouldReturnSuccessEnvelope() throws Exception {
        LoginResponse response = new LoginResponse(
                "admin-access", "admin-refresh", "Bearer", 3600L, "admin-token-id");
        given(adminAuthService.login(any(LoginRequest.class), any())).willReturn(response);

        mockMvc.perform(post("/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin", "admin123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("admin-access"))
                .andExpect(jsonPath("$.data.refreshToken").value("admin-refresh"));
    }

    @Test
    @DisplayName("POST /admin/login - 管理员登录失败返回错误信封")
    void adminLogin_WhenAuthFailed_ShouldReturnErrorEnvelope() throws Exception {
        willThrow(new BusinessException("AUTH_FAILED", "管理员认证失败"))
                .given(adminAuthService).login(any(LoginRequest.class), any());

        mockMvc.perform(post("/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_FAILED"));
    }

    @Test
    @DisplayName("POST /admin/refresh - 管理员刷新Token成功返回信封格式")
    void adminRefresh_ShouldReturnSuccessEnvelope() throws Exception {
        LoginResponse response = new LoginResponse(
                "new-admin-access", "new-admin-refresh", "Bearer", 3600L, "new-admin-token-id");
        given(adminAuthService.refresh("admin-refresh-token")).willReturn(response);

        mockMvc.perform(post("/admin/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest("admin-refresh-token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("new-admin-access"));
    }

    @Test
    @DisplayName("POST /admin/refresh - 管理员刷新Token无效返回错误信封")
    void adminRefresh_WhenInvalidToken_ShouldReturnErrorEnvelope() throws Exception {
        willThrow(new BusinessException("INVALID_REFRESH_TOKEN", "刷新令牌无效"))
                .given(adminAuthService).refresh("bad-admin-refresh");

        mockMvc.perform(post("/admin/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest("bad-admin-refresh"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("POST /admin/logout - 管理员登出成功返回信封格式")
    void adminLogout_ShouldReturnSuccessEnvelope() throws Exception {
        willDoNothing().given(adminAuthService).logout(1L);

        mockMvc.perform(post("/admin/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
