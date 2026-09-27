package com.cloudmart.auth.controller;

import com.cloudmart.auth.service.AuthSessionService;
import com.cloudmart.auth.service.RefreshTokenService;
import com.cloudmart.auth.service.SubjectType;
import com.cloudmart.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部令牌撤销接口（SEC-02）：mall-admin 踢人/禁用管理员时经服务令牌调用，
 * 撤销目标主体名下全部刷新令牌家族，替代 mall-admin 直写 mall-auth Redis 键的
 * 旧做法（键结构是 mall-auth 的私有实现，跨服务直写阻碍令牌模型演进）。
 *
 * <p>访问控制：mall-admin 的 admin:auth 服务令牌（ServiceTokenAuthenticationFilter
 * 建立 ROLE_INTERNAL）+ 本控制器方法级 @PreAuthorize 二次确认。</p>
 */
@RestController
@RequestMapping("/internal/tokens")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('INTERNAL')")
@Tag(name = "内部-令牌撤销", description = "mall-admin 踢人/禁用时撤销主体全部刷新令牌（服务令牌可达）")
public class InternalTokenRevocationController {

    private final RefreshTokenService refreshTokenService;
    private final AuthSessionService authSessionService;

    /** 撤销请求体 */
    public record RevokeSubjectRequest(@NotBlank String subjectType, @NotNull Long subjectId) {
    }

    @PostMapping("/revoke-subject")
    @Operation(summary = "撤销主体全部刷新令牌", description = "按身份域（USER/ADMIN）撤销目标主体名下全部令牌家族")
    public ApiResponse<Void> revokeSubject(@RequestBody RevokeSubjectRequest request) {
        SubjectType subjectType = SubjectType.valueOf(request.subjectType());
        refreshTokenService.revokeAllTokensForSubject(subjectType, request.subjectId());
        return ApiResponse.ok(null);
    }

    /** 认证状态失效请求体 */
    public record InvalidateStateRequest(@NotBlank String subjectType, @NotNull Long subjectId,
                                         boolean revokeRefreshTokens) {
    }

    @PostMapping("/invalidate-state")
    @Operation(summary = "使主体认证状态失效", description = "递增认证状态版本使存量访问令牌秒级失效；"
            + "revokeRefreshTokens=true 时同时撤销全部刷新令牌家族（禁用/改密硬失效）")
    public ApiResponse<Void> invalidateState(@RequestBody InvalidateStateRequest request) {
        SubjectType subjectType = SubjectType.valueOf(request.subjectType());
        authSessionService.invalidate(subjectType, request.subjectId(), request.revokeRefreshTokens());
        return ApiResponse.ok(null);
    }
}
