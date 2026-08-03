package com.cloudmart.auth.service.impl;

import com.cloudmart.auth.dto.LoginRequest;
import com.cloudmart.auth.dto.LoginResponse;
import com.cloudmart.auth.dto.RefreshRequest;
import com.cloudmart.auth.dto.UserDTO;
import com.cloudmart.auth.dto.ValidateRequest;
import com.cloudmart.auth.feign.UserFeignClient;
import com.cloudmart.auth.service.RefreshTokenService;
import com.cloudmart.auth.util.JwtProvider;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceImplTest {

    private UserFeignClient userFeignClient;
    private JwtProvider jwtProvider;
    private RefreshTokenService refreshTokenService;
    private AuthServiceImpl authService;

    private static final Long USER_ID = 1L;
    private static final String USERNAME = "testuser";
    private static final String PASSWORD = "password123";
    private static final String ACCESS_TOKEN = "access.token.value";
    private static final String REFRESH_TOKEN = "refresh-token-uuid";

    @BeforeEach
    void setUp() {
        userFeignClient = mock(UserFeignClient.class);
        jwtProvider = mock(JwtProvider.class);
        refreshTokenService = mock(RefreshTokenService.class);
        authService = new AuthServiceImpl(userFeignClient, jwtProvider, refreshTokenService, 900L);
    }

    @Nested
    @DisplayName("login")
    class LoginTests {

        @Test
        @DisplayName("should return login response on successful login")
        void login_success_returnsLoginResponse() {
            LoginRequest request = new LoginRequest(USERNAME, PASSWORD);
            UserDTO userDTO = new UserDTO(USER_ID, USERNAME, "test@example.com",
                    "13800138000", "测试用户", null, 1, null);

            when(userFeignClient.validateUser(any(ValidateRequest.class)))
                    .thenReturn(ApiResponse.ok(userDTO));
            when(jwtProvider.generateAccessToken(USER_ID, "user")).thenReturn(ACCESS_TOKEN);
            when(refreshTokenService.createRefreshToken(USER_ID)).thenReturn(REFRESH_TOKEN);

            LoginResponse result = authService.login(request);

            assertThat(result).isNotNull();
            assertThat(result.accessToken()).isEqualTo(ACCESS_TOKEN);
            assertThat(result.refreshToken()).isEqualTo(REFRESH_TOKEN);
            assertThat(result.tokenType()).isEqualTo("Bearer");
            verify(refreshTokenService).createRefreshToken(USER_ID);
        }

        @Test
        @DisplayName("should throw when credentials are wrong")
        void login_wrongPassword_throwsException() {
            LoginRequest request = new LoginRequest(USERNAME, "wrongpassword");

            when(userFeignClient.validateUser(any(ValidateRequest.class)))
                    .thenReturn(ApiResponse.fail("AUTH_FAILED", "用户名或密码错误"));

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo("AUTH_FAILED");
        }

        @Test
        @DisplayName("should throw when response data is null")
        void login_nullUserData_throwsException() {
            LoginRequest request = new LoginRequest(USERNAME, PASSWORD);

            when(userFeignClient.validateUser(any(ValidateRequest.class)))
                    .thenReturn(new ApiResponse<>(true, null, null, null));

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo("AUTH_FAILED");
        }
    }

    @Nested
    @DisplayName("refresh")
    class RefreshTests {

        @Test
        @DisplayName("should return new tokens on valid refresh token")
        void refreshToken_success_returnsNewTokens() {
            RefreshRequest request = new RefreshRequest(REFRESH_TOKEN);

            when(refreshTokenService.rotateRefreshToken(REFRESH_TOKEN)).thenReturn(USER_ID);
            when(jwtProvider.generateAccessToken(USER_ID, "user")).thenReturn(ACCESS_TOKEN);
            when(refreshTokenService.createRefreshToken(USER_ID)).thenReturn("new-refresh-token");

            LoginResponse result = authService.refresh(request);

            assertThat(result).isNotNull();
            assertThat(result.accessToken()).isEqualTo(ACCESS_TOKEN);
            assertThat(result.refreshToken()).isEqualTo("new-refresh-token");
            assertThat(result.tokenType()).isEqualTo("Bearer");
        }

        @Test
        @DisplayName("should throw when refresh token is expired or invalid")
        void refreshToken_expiredToken_throwsException() {
            RefreshRequest request = new RefreshRequest("expired-token");

            when(refreshTokenService.rotateRefreshToken("expired-token")).thenReturn(null);

            assertThatThrownBy(() -> authService.refresh(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo("INVALID_REFRESH_TOKEN");
        }

        @Test
        @DisplayName("should throw when refresh token reuse is detected")
        void refreshToken_reusedToken_throwsException() {
            RefreshRequest request = new RefreshRequest(REFRESH_TOKEN);

            when(refreshTokenService.rotateRefreshToken(REFRESH_TOKEN))
                    .thenThrow(new IllegalStateException("Refresh token reuse detected for user: " + USER_ID));

            assertThatThrownBy(() -> authService.refresh(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo("TOKEN_REUSE_DETECTED");
        }
    }

    @Nested
    @DisplayName("logout")
    class LogoutTests {

        @Test
        @DisplayName("should revoke all tokens for user")
        void logout_revokesAllTokens() {
            authService.logout(USER_ID);

            verify(refreshTokenService).revokeAllTokensForUser(USER_ID);
        }
    }
}
