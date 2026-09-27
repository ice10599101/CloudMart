package com.cloudmart.auth.service.impl;

import com.cloudmart.auth.dto.LoginRequest;
import com.cloudmart.auth.dto.LoginResponse;
import com.cloudmart.auth.dto.RefreshRequest;
import com.cloudmart.auth.dto.UserDTO;
import com.cloudmart.auth.dto.ValidateRequest;
import com.cloudmart.auth.feign.UserFeignClient;
import com.cloudmart.auth.service.AuthService;
import com.cloudmart.auth.service.AuthSessionService;
import com.cloudmart.auth.service.RefreshTokenService;
import com.cloudmart.auth.service.RefreshTokenService.RotationResult;
import com.cloudmart.auth.service.SubjectType;
import com.cloudmart.auth.util.JwtProvider.TokenPrincipal;
import com.cloudmart.auth.util.JwtProvider;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserFeignClient userFeignClient;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;
    private final AuthSessionService authSessionService;
    private final long accessTokenExpiration;

    public AuthServiceImpl(UserFeignClient userFeignClient,
                           JwtProvider jwtProvider,
                           RefreshTokenService refreshTokenService,
                           AuthSessionService authSessionService,
                           @Value("${auth.jwt.access-token-expiration:900}") long accessTokenExpiration) {
        this.userFeignClient = userFeignClient;
        this.jwtProvider = jwtProvider;
        this.refreshTokenService = refreshTokenService;
        this.authSessionService = authSessionService;
        this.accessTokenExpiration = accessTokenExpiration;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        ValidateRequest validateRequest = new ValidateRequest(request.account(), request.password());
        ApiResponse<UserDTO> response = userFeignClient.validateUser(validateRequest);

        if (!response.success() || response.data() == null) {
            throw new BusinessException("AUTH_FAILED", "账号或密码错误");
        }

        UserDTO user = response.data();
        // SEC-03：签发可撤销会话，令牌携带 sid + 当前认证状态版本
        AuthSessionService.IssuedSession session =
                authSessionService.issueSession(SubjectType.USER, user.id());
        String accessToken = jwtProvider.generateUserAccessToken(
                new TokenPrincipal(SubjectType.USER, user.id(), session.sid(), session.authVersion()));
        String refreshToken = refreshTokenService.createRefreshToken(SubjectType.USER, user.id());

        return new LoginResponse(accessToken, refreshToken, "Bearer", accessTokenExpiration, null);
    }

    @Override
    public LoginResponse refresh(RefreshRequest request) {
        // SEC-02：仅接受用户域（u:）令牌；原子轮换在家族内推进，不再整发新家族
        //（杜绝持续轮换无限续期）；重放/跨域错误由 RefreshTokenService 抛出稳定错误码。
        RotationResult rotation =
                refreshTokenService.rotateRefreshToken(SubjectType.USER, request.refreshToken());

        // SEC-03：刷新签发新会话并携带最新认证状态版本（权限软失效后自动拿到新版本）
        AuthSessionService.IssuedSession session =
                authSessionService.issueSession(SubjectType.USER, rotation.subjectId());
        String accessToken = jwtProvider.generateUserAccessToken(
                new TokenPrincipal(SubjectType.USER, rotation.subjectId(), session.sid(), session.authVersion()));

        return new LoginResponse(accessToken, rotation.tokenValue(), "Bearer", accessTokenExpiration, null);
    }

    @Override
    public void logout(Long userId, String sid) {
        // SEC-03：撤销当前会话（访问令牌立即失效）+ 全部刷新令牌家族
        authSessionService.revokeSession(sid);
        refreshTokenService.revokeAllTokensForSubject(SubjectType.USER, userId);
    }
}
