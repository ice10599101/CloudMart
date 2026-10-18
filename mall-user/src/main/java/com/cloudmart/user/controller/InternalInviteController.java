package com.cloudmart.user.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.user.service.InviteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 邀请内部接口（T14）：统一收敛到 {@code /internal/invites/**}。
 *
 * <p>此前资格回调与关系台账挂在 {@code /invites/invite/internal/**}，
 * 与 mall-wish 的 {@code /users/invite/internal/**}、配置里的
 * {@code /invites/internal}（重复且不匹配真实路径）三处不一致。
 * 资格回调只允许 mall-wish（user:internal），关系台账只允许 mall-admin（user:admin），
 * 按最长具体前缀分域配置。</p>
 */
@RestController
@RequestMapping("/internal/invites")
@RequiredArgsConstructor
@Tag(name = "邀请内部接口", description = "资格回调 + 关系台账（仅内部服务）")
public class InternalInviteController {

    private final InviteService inviteService;

    /** 资格检查请求：主体与签到天数必须来自受信事实，不接受客户端自行填写 */
    public record EligibilityCheckRequest(
            @NotNull(message = "userId 不能为空") Long userId,
            @Min(value = 0, message = "签到天数不能为负") int distinctSigninDays) {}

    @PostMapping("/eligibility-check")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "邀请资格检查", description = "WISH-04：受邀人签到达标回调；达到资格仅发一次（GRANTED 幂等）")
    public ApiResponse<Map<String, Object>> eligibilityCheck(
            @Valid @RequestBody EligibilityCheckRequest request) {
        return ApiResponse.ok(inviteService.eligibilityCheck(request.userId(), request.distinctSigninDays()));
    }

    @GetMapping("/relations")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "邀请关系台账", description = "资格状态/限制原因/规则版本（后台不直接编辑累计邀请数）")
    public ApiResponse<Map<String, Object>> relations(
            @RequestParam(required = false) String rewardStatus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(inviteService.pageRelationsForOps(rewardStatus, page, Math.min(size, 100)));
    }
}
