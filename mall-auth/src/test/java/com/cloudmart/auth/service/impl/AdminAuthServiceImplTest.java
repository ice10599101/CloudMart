package com.cloudmart.auth.service.impl;

import com.cloudmart.auth.dto.AdminUserDTO;
import com.cloudmart.auth.dto.LoginLogRecordRequest;
import com.cloudmart.auth.dto.LoginRequest;
import com.cloudmart.auth.dto.LoginResponse;
import com.cloudmart.auth.dto.ValidateRequest;
import com.cloudmart.auth.feign.AdminLoginLogFeignClient;
import com.cloudmart.auth.feign.AdminUserFeignClient;
import com.cloudmart.auth.service.RefreshTokenService;
import com.cloudmart.auth.service.AuthSessionService;
import com.cloudmart.auth.service.SubjectType;
import com.cloudmart.auth.util.JwtProvider;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminAuthServiceImplTest {

    private AdminUserFeignClient adminUserFeignClient;
    private AdminLoginLogFeignClient loginLogFeignClient;
    private JwtProvider jwtProvider;
    private RefreshTokenService refreshTokenService;
    private AuthSessionService authSessionService;
    private StringRedisTemplate redisTemplate;
    private ObjectMapper objectMapper;
    private AdminAuthServiceImpl adminAuthService;

    private static final Long ADMIN_ID = 1L;
    private static final String USERNAME = "admin";
    private static final String PASSWORD = "admin123";
    private static final String ACCESS_TOKEN = "admin-access-token";
    private static final String REFRESH_TOKEN = "admin-refresh-token";
    private static final long ACCESS_TOKEN_EXPIRATION = 900L;

    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void setUp() {
        adminUserFeignClient = mock(AdminUserFeignClient.class);
        loginLogFeignClient = mock(AdminLoginLogFeignClient.class);
        jwtProvider = mock(JwtProvider.class);
        refreshTokenService = mock(RefreshTokenService.class);
        redisTemplate = mock(StringRedisTemplate.class);
        objectMapper = new ObjectMapper();

        authSessionService = mock(AuthSessionService.class);
        when(authSessionService.issueSession(any(), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(new AuthSessionService.IssuedSession("session-1", 0L));
        adminAuthService = new AdminAuthServiceImpl(
                adminUserFeignClient, loginLogFeignClient, jwtProvider,
                refreshTokenService, authSessionService, redisTemplate, objectMapper, ACCESS_TOKEN_EXPIRATION
        );

        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private HttpServletRequest mockRequest(String ip, String userAgent) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(ip);
        when(request.getHeader("User-Agent")).thenReturn(userAgent);
        when(request.getRemoteAddr()).thenReturn(ip != null ? ip : "127.0.0.1");
        return request;
    }

    private AdminUserDTO createAdminDTO(boolean isSuperAdmin) {
        return new AdminUserDTO(ADMIN_ID, USERNAME, "管理员", 1L,
                isSuperAdmin ? Set.of() : Set.of("system:user:list"), isSuperAdmin);
    }

    @Nested
    @DisplayName("login")
    class LoginTests {

        @Test
        @DisplayName("should login successfully with valid credentials")
        void login_validCredentials_returnsLoginResponse() {
            HttpServletRequest httpRequest = mockRequest("127.0.0.1", "Chrome/120");
            LoginRequest request = new LoginRequest(USERNAME, PASSWORD);
            AdminUserDTO adminDTO = createAdminDTO(false);

            when(valueOperations.get(SecurityConstants.ADMIN_LOCK_PREFIX + USERNAME)).thenReturn(null);
            when(adminUserFeignClient.validateAdmin(any(ValidateRequest.class)))
                    .thenReturn(ApiResponse.ok(adminDTO));
            when(jwtProvider.generateAdminAccessToken(any(com.cloudmart.auth.util.JwtProvider.TokenPrincipal.class),
                any(), any(), any())).thenReturn(ACCESS_TOKEN);
            when(refreshTokenService.createRefreshToken(SubjectType.ADMIN, ADMIN_ID)).thenReturn(REFRESH_TOKEN);

            LoginResponse result = adminAuthService.login(request, httpRequest);

            assertThat(result).isNotNull();
            assertThat(result.accessToken()).isEqualTo(ACCESS_TOKEN);
            assertThat(result.refreshToken()).isEqualTo(REFRESH_TOKEN);
            assertThat(result.tokenType()).isEqualTo("Bearer");
            assertThat(result.tokenId()).isNotBlank();
        }

        @Test
        @DisplayName("should grant super admin all permissions")
        void login_superAdmin_getsAllPermissions() {
            HttpServletRequest httpRequest = mockRequest("127.0.0.1", "Chrome/120");
            LoginRequest request = new LoginRequest(USERNAME, PASSWORD);
            AdminUserDTO adminDTO = createAdminDTO(true);

            when(valueOperations.get(SecurityConstants.ADMIN_LOCK_PREFIX + USERNAME)).thenReturn(null);
            when(adminUserFeignClient.validateAdmin(any(ValidateRequest.class)))
                    .thenReturn(ApiResponse.ok(adminDTO));
            when(jwtProvider.generateAdminAccessToken(any(com.cloudmart.auth.util.JwtProvider.TokenPrincipal.class),
                eq(Set.of("*:*:*")), any(), any())).thenReturn(ACCESS_TOKEN);
            when(refreshTokenService.createRefreshToken(SubjectType.ADMIN, ADMIN_ID)).thenReturn(REFRESH_TOKEN);

            LoginResponse result = adminAuthService.login(request, httpRequest);

            assertThat(result).isNotNull();
            verify(jwtProvider).generateAdminAccessToken(any(com.cloudmart.auth.util.JwtProvider.TokenPrincipal.class),
                eq(Set.of("*:*:*")), eq(USERNAME), eq(1L));
        }

        @Test
        @DisplayName("should throw ACCOUNT_LOCKED when account is locked")
        void login_lockedAccount_throwsAccountLocked() {
            HttpServletRequest httpRequest = mockRequest("127.0.0.1", "Chrome/120");
            LoginRequest request = new LoginRequest(USERNAME, PASSWORD);

            when(valueOperations.get(SecurityConstants.ADMIN_LOCK_PREFIX + USERNAME)).thenReturn("1");

            assertThatThrownBy(() -> adminAuthService.login(request, httpRequest))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("ACCOUNT_LOCKED");
        }

        @Test
        @DisplayName("should throw AUTH_FAILED when credentials are wrong")
        void login_wrongCredentials_throwsAuthFailed() {
            HttpServletRequest httpRequest = mockRequest("127.0.0.1", "Chrome/120");
            LoginRequest request = new LoginRequest(USERNAME, "wrongpassword");

            when(valueOperations.get(SecurityConstants.ADMIN_LOCK_PREFIX + USERNAME)).thenReturn(null);
            when(adminUserFeignClient.validateAdmin(any(ValidateRequest.class)))
                    .thenReturn(ApiResponse.fail("AUTH_FAILED", "用户名或密码错误"));
            when(valueOperations.increment(anyString())).thenReturn(1L);

            assertThatThrownBy(() -> adminAuthService.login(request, httpRequest))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("AUTH_FAILED");
        }

        @Test
        @DisplayName("should throw AUTH_FAILED when response data is null")
        void login_nullResponseData_throwsAuthFailed() {
            HttpServletRequest httpRequest = mockRequest("127.0.0.1", "Chrome/120");
            LoginRequest request = new LoginRequest(USERNAME, PASSWORD);

            when(valueOperations.get(SecurityConstants.ADMIN_LOCK_PREFIX + USERNAME)).thenReturn(null);
            when(adminUserFeignClient.validateAdmin(any(ValidateRequest.class)))
                    .thenReturn(new ApiResponse<>(true, null, null, null));
            when(valueOperations.increment(anyString())).thenReturn(1L);

            assertThatThrownBy(() -> adminAuthService.login(request, httpRequest))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("AUTH_FAILED");
        }

        @Test
        @DisplayName("should increment login fail count on failed login")
        void login_failedLogin_incrementsFailCount() {
            HttpServletRequest httpRequest = mockRequest("127.0.0.1", "Chrome/120");
            LoginRequest request = new LoginRequest(USERNAME, "wrongpassword");

            when(valueOperations.get(SecurityConstants.ADMIN_LOCK_PREFIX + USERNAME)).thenReturn(null);
            when(adminUserFeignClient.validateAdmin(any(ValidateRequest.class)))
                    .thenReturn(ApiResponse.fail("AUTH_FAILED", "用户名或密码错误"));
            when(valueOperations.increment(anyString())).thenReturn(1L);

            assertThatThrownBy(() -> adminAuthService.login(request, httpRequest))
                    .isInstanceOf(BusinessException.class);

            verify(valueOperations).increment(SecurityConstants.ADMIN_LOGIN_FAIL_PREFIX + USERNAME);
        }

        @Test
        @DisplayName("should lock account after MAX_LOGIN_FAIL_COUNT attempts")
        void login_maxFailAttempts_locksAccount() {
            HttpServletRequest httpRequest = mockRequest("127.0.0.1", "Chrome/120");
            LoginRequest request = new LoginRequest(USERNAME, "wrongpassword");

            when(valueOperations.get(SecurityConstants.ADMIN_LOCK_PREFIX + USERNAME)).thenReturn(null);
            when(adminUserFeignClient.validateAdmin(any(ValidateRequest.class)))
                    .thenReturn(ApiResponse.fail("AUTH_FAILED", "用户名或密码错误"));
            when(valueOperations.increment(anyString())).thenReturn(5L);

            assertThatThrownBy(() -> adminAuthService.login(request, httpRequest))
                    .isInstanceOf(BusinessException.class);

            verify(valueOperations).set(eq(SecurityConstants.ADMIN_LOCK_PREFIX + USERNAME), eq("1"), any());
        }

        @Test
        @DisplayName("should clear login fail count on successful login")
        void login_successfulLogin_clearsFailCount() {
            HttpServletRequest httpRequest = mockRequest("127.0.0.1", "Chrome/120");
            LoginRequest request = new LoginRequest(USERNAME, PASSWORD);
            AdminUserDTO adminDTO = createAdminDTO(false);

            when(valueOperations.get(SecurityConstants.ADMIN_LOCK_PREFIX + USERNAME)).thenReturn(null);
            when(adminUserFeignClient.validateAdmin(any(ValidateRequest.class)))
                    .thenReturn(ApiResponse.ok(adminDTO));
            when(jwtProvider.generateAdminAccessToken(any(com.cloudmart.auth.util.JwtProvider.TokenPrincipal.class),
                any(), any(), any())).thenReturn(ACCESS_TOKEN);
            when(refreshTokenService.createRefreshToken(SubjectType.ADMIN, ADMIN_ID)).thenReturn(REFRESH_TOKEN);

            adminAuthService.login(request, httpRequest);

            verify(redisTemplate).delete(SecurityConstants.ADMIN_LOGIN_FAIL_PREFIX + USERNAME);
        }
    }

    @Nested
    @DisplayName("refresh")
    class RefreshTests {

        @Test
        @DisplayName("should return new tokens on valid refresh token")
        void refresh_validToken_returnsNewTokens() {
            AdminUserDTO adminDTO = createAdminDTO(false);

            when(refreshTokenService.rotateRefreshToken(SubjectType.ADMIN, REFRESH_TOKEN))
.thenReturn(rotation(REFRESH_TOKEN, ADMIN_ID));
            when(adminUserFeignClient.getPermissionsByUserId(ADMIN_ID))
                    .thenReturn(ApiResponse.ok(adminDTO));
            when(jwtProvider.generateAdminAccessToken(any(com.cloudmart.auth.util.JwtProvider.TokenPrincipal.class),
                any(), any(), any())).thenReturn(ACCESS_TOKEN);
            LoginResponse result = adminAuthService.refresh(REFRESH_TOKEN);

            assertThat(result).isNotNull();
            assertThat(result.accessToken()).isEqualTo(ACCESS_TOKEN);
            assertThat(result.refreshToken()).isEqualTo(REFRESH_TOKEN);
        }

        @Test
        @DisplayName("should throw INVALID_REFRESH_TOKEN when token is null")
        void refresh_nullToken_throwsInvalidRefreshToken() {
            when(refreshTokenService.rotateRefreshToken(SubjectType.ADMIN, "invalid-token"))
                    .thenThrow(new BusinessException("INVALID_REFRESH_TOKEN", "invalid"));

            assertThatThrownBy(() -> adminAuthService.refresh("invalid-token"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("INVALID_REFRESH_TOKEN");
        }

        @Test
        @DisplayName("should throw TOKEN_REUSE_DETECTED when token reuse is detected")
        void refresh_reusedToken_throwsTokenReuseDetected() {
            when(refreshTokenService.rotateRefreshToken(SubjectType.ADMIN, REFRESH_TOKEN))
                    .thenThrow(new BusinessException("TOKEN_REUSE_DETECTED", "reuse"));

            assertThatThrownBy(() -> adminAuthService.refresh(REFRESH_TOKEN))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("TOKEN_REUSE_DETECTED");
        }

        @Test
        @DisplayName("should throw PERMISSION_FETCH_FAILED when user info fetch fails")
        void refresh_userInfoFetchFails_throwsPermissionFetchFailed() {
            when(refreshTokenService.rotateRefreshToken(SubjectType.ADMIN, REFRESH_TOKEN))
.thenReturn(rotation(REFRESH_TOKEN, ADMIN_ID));
            when(adminUserFeignClient.getPermissionsByUserId(ADMIN_ID))
                    .thenThrow(new RuntimeException("Service unavailable"));

            assertThatThrownBy(() -> adminAuthService.refresh(REFRESH_TOKEN))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo("PERMISSION_FETCH_FAILED");
        }
    }

    @Nested
    @DisplayName("logout")
    class LogoutTests {

        @Test
        @DisplayName("should revoke all tokens and remove online user")
        void logout_revokesAllTokens() {
            adminAuthService.logout(ADMIN_ID, "session-1");

            verify(authSessionService).revokeSession("session-1");

            verify(refreshTokenService).revokeAllTokensForSubject(SubjectType.ADMIN, ADMIN_ID);
        }
    }

    /** SEC-02：构造成功轮换返回值 */
    private RefreshTokenService.RotationResult rotation(String tokenValue, Long subjectId) {
        return new RefreshTokenService.RotationResult(SubjectType.ADMIN, subjectId, tokenValue, 600L);
    }
}
