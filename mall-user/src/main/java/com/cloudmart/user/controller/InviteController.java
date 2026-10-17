package com.cloudmart.user.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.user.service.InviteService;
import com.cloudmart.user.vo.InviteInfoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 邀请裂变（N-3）：邀请码与绑定。
 */
@RestController
@RequestMapping("/invites")
@RequiredArgsConstructor
@Tag(name = "邀请裂变", description = "一人一码；绑定后双向发星光奖励")
public class InviteController {

    private final InviteService inviteService;

    @GetMapping("/my")
    @Operation(summary = "我的邀请信息", description = "码（首查生成）+ 已邀请人数 + 每人奖励额")
    public ApiResponse<InviteInfoVO> myInvites(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(inviteService.myInvites(userId));
    }

    public record BindRequest(String code) {}

    @PostMapping("/bind")
    @Operation(summary = "绑定邀请码", description = "受邀人调用；非自邀、一人一次；成功双向发奖（幂等）")
    public ApiResponse<Map<String, Object>> bind(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @RequestBody BindRequest request) {
        Long relationId = inviteService.bind(userId, request.code());
        return ApiResponse.ok(Map.of("relationId", relationId));
    }

    /** WISH-04：资格检查回调（SEC-04：仅服务令牌，mall-wish 签到事务提交后调用） */
    @org.springframework.web.bind.annotation.PostMapping("/invite/internal/eligibility-check")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "WISH-04：邀请资格检查", description = "受邀人签到达标回调；达到资格仅发一次（GRANTED 幂等）")
    public ApiResponse<java.util.Map<String, Object>> eligibilityCheck(
            @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Object> body) {
        final Long userId = Long.valueOf(String.valueOf(body.get("userId")));
        final int days = Integer.parseInt(String.valueOf(body.getOrDefault("distinctSigninDays", "0")));
        return ApiResponse.ok(inviteService.eligibilityCheck(userId, days));
    }

    /** WISH-04：邀请关系台账（SEC-04：仅服务令牌——后台查询邀请关系/资格/奖励状态/限制原因） */
    @GetMapping("/invite/internal/relations")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "WISH-04：邀请关系台账", description = "资格状态/限制原因/规则版本（后台不直接编辑累计邀请数）")
    public ApiResponse<java.util.Map<String, Object>> relations(
            @org.springframework.web.bind.annotation.RequestParam(required = false) String rewardStatus,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "1") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int size) {
        var result = inviteService.pageRelationsForOps(rewardStatus, page, Math.min(size, 100));
        return ApiResponse.ok(result);
    }
}
