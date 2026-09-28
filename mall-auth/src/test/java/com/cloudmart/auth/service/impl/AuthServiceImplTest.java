package com.cloudmart.auth.service.impl;

import com.cloudmart.auth.dto.LoginRequest;
import com.cloudmart.auth.dto.LoginResponse;
import com.cloudmart.auth.dto.RefreshRequest;
import com.cloudmart.auth.dto.UserDTO;
import com.cloudmart.auth.dto.ValidateRequest;
import com.cloudmart.auth.feign.UserFeignClient;
import com.cloudmart.auth.service.RefreshTokenService;
import com.cloudmart.auth.service.AuthSessionService;
import com.cloudmart.auth.service.SubjectType;
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
    private AuthSessionService authSessionService;
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
        authSessionService = mock(AuthSessionService.class);
        when(authSessionService.issueSession(any(), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(new AuthSessionService.IssuedSession("session-1", 0L));
        authService = new AuthServiceImpl(userFeignClient, jwtProvider, refreshTokenService,
                authSessionService, 900L);
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
            when(jwtProvider.generateUserAccessToken(any(com.cloudmart.auth.util.JwtProvider.TokenPrincipal.class)))
                .thenReturn(ACCESS_TOKEN);
            when(refreshTokenService.createRefreshToken(SubjectType.USER, USER_ID, "session-1"))
                    .thenReturn(REFRESH_TOKEN);

            LoginResponse result = authService.login(request);

            assertThat(result).isNotNull();
            assertThat(result.accessToken()).isEqualTo(ACCESS_TOKEN);
            assertThat(result.refreshToken()).isEqualTo(REFRESH_TOKEN);
            assertThat(result.tokenType()).isEqualTo("Bearer");
            verify(refreshTokenService).createRefreshToken(SubjectType.USER, USER_ID, "session-1");
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

            when(refreshTokenService.rotateRefreshToken(SubjectType.USER, REFRESH_TOKEN))
.thenReturn(rotation(REFRESH_TOKEN, USER_ID));
            when(refreshTokenService.bindFamilySession(REFRESH_TOKEN, "session-1")).thenReturn("old-sid");
            when(jwtProvider.generateUserAccessToken(any(com.cloudmart.auth.util.JwtProvider.TokenPrincipal.class)))
                .thenReturn(ACCESS_TOKEN);
            LoginResponse result = authService.refresh(request);

            assertThat(result).isNotNull();
            assertThat(result.accessToken()).isEqualTo(ACCESS_TOKEN);
            assertThat(result.refreshToken()).isEqualTo(REFRESH_TOKEN);
            assertThat(result.tokenType()).isEqualTo("Bearer");
        }

        @Test
        @DisplayName("刷新后家族重绑新会话并撤销旧会话（不无限新增活动 sid）")
        void refreshToken_rebindsFamilyAndRevokesPreviousSession() {
            RefreshRequest request = new RefreshRequest(REFRESH_TOKEN);

            when(refreshTokenService.rotateRefreshToken(SubjectType.USER, REFRESH_TOKEN))
.thenReturn(rotation(REFRESH_TOKEN, USER_ID));
            when(refreshTokenService.bindFamilySession(REFRESH_TOKEN, "session-1")).thenReturn("session-0");
            when(jwtProvider.generateUserAccessToken(any(com.cloudmart.auth.util.JwtProvider.TokenPrincipal.class)))
                .thenReturn(ACCESS_TOKEN);

            authService.refresh(request);

            verify(refreshTokenService).bindFamilySession(REFRESH_TOKEN, "session-1");
            verify(authSessionService).revokeSession("session-0");
        }

        @Test
        @DisplayName("绑定无旧会话时不执行撤销")
        void refreshToken_noPreviousSession_noRevoke() {
            RefreshRequest request = new RefreshRequest(REFRESH_TOKEN);

            when(refreshTokenService.rotateRefreshToken(SubjectType.USER, REFRESH_TOKEN))
.thenReturn(rotation(REFRESH_TOKEN, USER_ID));
            when(refreshTokenService.bindFamilySession(REFRESH_TOKEN, "session-1")).thenReturn(null);
            when(jwtProvider.generateUserAccessToken(any(com.cloudmart.auth.util.JwtProvider.TokenPrincipal.class)))
                .thenReturn(ACCESS_TOKEN);

            authService.refresh(request);

            verify(authSessionService, org.mockito.Mockito.never()).revokeSession(org.mockito.ArgumentMatchers.anyString());
        }

        @Test
        @DisplayName("should throw when refresh token is expired or invalid")
        void refreshToken_expiredToken_throwsException() {
            RefreshRequest request = new RefreshRequest("expired-token");

            when(refreshTokenService.rotateRefreshToken(SubjectType.USER, "expired-token"))
                    .thenThrow(new BusinessException("REFRESH_TOKEN_EXPIRED", "expired"));

            assertThatThrownBy(() -> authService.refresh(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo("REFRESH_TOKEN_EXPIRED");
        }

        @Test
        @DisplayName("should throw when refresh token reuse is detected")
        void refreshToken_reusedToken_throwsException() {
            RefreshRequest request = new RefreshRequest(REFRESH_TOKEN);

            when(refreshTokenService.rotateRefreshToken(SubjectType.USER, REFRESH_TOKEN))
                    .thenThrow(new BusinessException("TOKEN_REUSE_DETECTED", "reuse"));

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
        @DisplayName("退出当前设备：撤销会话与该会话绑定的家族，不动其他设备")
        void logout_revokesSessionAndBoundFamilyOnly() {
            authService.logout(USER_ID, "session-1");

            verify(authSessionService).revokeSession("session-1");
            verify(refreshTokenService).revokeFamilyBySession(SubjectType.USER, USER_ID, "session-1");
            verify(refreshTokenService, org.mockito.Mockito.never())
                    .revokeAllTokensForSubject(org.mockito.Mockito.any(), org.mockito.ArgumentMatchers.anyLong());
        }

        @Test
        @DisplayName("sid 缺失时保守撤销全部刷新家族（绝不伪登出）")
        void logout_blankSid_fallsBackToRevokeAll() {
            authService.logout(USER_ID, " ");

            verify(refreshTokenService).revokeAllTokensForSubject(SubjectType.USER, USER_ID);
            verify(refreshTokenService, org.mockito.Mockito.never())
                    .revokeFamilyBySession(org.mockito.Mockito.any(), org.mockito.ArgumentMatchers.anyLong(),
                            org.mockito.ArgumentMatchers.anyString());
        }
    }

    @Nested
    @DisplayName("logoutAll")
    class LogoutAllTests {

        @Test
        @DisplayName("退出全部设备：认证状态版本递增（硬失效）+ 撤销全部刷新家族")
        void logoutAll_hardInvalidatesSubject() {
            authService.logoutAll(USER_ID);

            verify(authSessionService).invalidate(SubjectType.USER, USER_ID, true);
        }
    }

    /** SEC-02：构造成功轮换返回值 */
    private RefreshTokenService.RotationResult rotation(String tokenValue, Long subjectId) {
        return new RefreshTokenService.RotationResult(SubjectType.USER, subjectId, tokenValue, 600L);
    }
}
