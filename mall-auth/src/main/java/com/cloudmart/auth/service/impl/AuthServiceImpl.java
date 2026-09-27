package com.cloudmart.auth.service.impl;

import com.cloudmart.auth.dto.LoginRequest;
import com.cloudmart.auth.dto.LoginResponse;
import com.cloudmart.auth.dto.RefreshRequest;
import com.cloudmart.auth.dto.UserDTO;
import com.cloudmart.auth.dto.ValidateRequest;
import com.cloudmart.auth.feign.UserFeignClient;
import com.cloudmart.auth.service.AuthService;
import com.cloudmart.auth.service.RefreshTokenService;
import com.cloudmart.auth.service.RefreshTokenService.RotationResult;
import com.cloudmart.auth.service.SubjectType;
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
    private final long accessTokenExpiration;

    public AuthServiceImpl(UserFeignClient userFeignClient,
                           JwtProvider jwtProvider,
                           RefreshTokenService refreshTokenService,
                           @Value("${auth.jwt.access-token-expiration:900}") long accessTokenExpiration) {
        this.userFeignClient = userFeignClient;
        this.jwtProvider = jwtProvider;
        this.refreshTokenService = refreshTokenService;
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
        String accessToken = jwtProvider.generateAccessToken(user.id(), "user");
        String refreshToken = refreshTokenService.createRefreshToken(SubjectType.USER, user.id());

        return new LoginResponse(accessToken, refreshToken, "Bearer", accessTokenExpiration, null);
    }

    @Override
    public LoginResponse refresh(RefreshRequest request) {
        // SEC-02：仅接受用户域（u:）令牌；原子轮换在家族内推进，不再整发新家族
        //（杜绝持续轮换无限续期）；重放/跨域错误由 RefreshTokenService 抛出稳定错误码。
        RotationResult rotation =
                refreshTokenService.rotateRefreshToken(SubjectType.USER, request.refreshToken());

        String accessToken = jwtProvider.generateAccessToken(rotation.subjectId(), "user");

        return new LoginResponse(accessToken, rotation.tokenValue(), "Bearer", accessTokenExpiration, null);
    }

    @Override
    public void logout(Long userId) {
        refreshTokenService.revokeAllTokensForSubject(SubjectType.USER, userId);
    }
}
