package com.cloudmart.admin.controller;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import com.cloudmart.admin.feign.InviteFeignClient;
import com.cloudmart.common.annotation.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * T15：后台邀请台账（/admin/business/invites）。
 * 台账只读 + HOLD 复核释放（重新核验资格，原因必填、审计留痕）；
 * 不提供直接编辑累计邀请数/发放标记的入口（防绕额度）。
 */
@RestController
@RequestMapping("/admin/business/invites")
@RequiredArgsConstructor
@Tag(name = "邀请台账", description = "T15 邀请关系/资格状态/限制原因台账 + HOLD 复核释放")
public class AdminInviteController {

    private final InviteFeignClient inviteFeignClient;

    @GetMapping
    @RequiresPermission("business:member:list")
    @Operation(summary = "邀请关系台账",
            description = "rewardStatus 筛选（PENDING_ELIGIBILITY/ELIGIBLE/GRANTED/HOLD/REJECTED_LIMIT）；原因台账可查")
    public ApiResponse<Object> relations(
            @RequestParam(value = "rewardStatus", required = false) String rewardStatus,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return inviteFeignClient.relations(rewardStatus, page, size);
    }

    @PostMapping("/{relationId}/release-hold")
    @RequiresPermission("business:member:edit")
    @Operation(summary = "HOLD 复核释放",
            description = "账号状态恢复后放行；重新核验资格（额度原子预占/发奖幂等不变）；原因必填留痕，不直接改发放标记")
    public ApiResponse<Object> releaseHold(
            @PathVariable("relationId") Long relationId,
            @org.springframework.web.bind.annotation.RequestHeader(
                    value = "X-Admin-Username", required = false) String adminUsername,
            @RequestBody Map<String, Object> body) {
        Object reasonObj = body == null ? null : body.get("reason");
        String reason = reasonObj == null ? "" : String.valueOf(reasonObj);
        if (reason.isBlank()) {
            throw new com.cloudmart.common.exception.BusinessException(
                    "INVITE_AUDIT_REQUIRED", "复核释放必须填写原因");
        }
        String operator = adminUsername == null || adminUsername.isBlank()
                ? "admin" : adminUsername;
        return inviteFeignClient.releaseHold(relationId, Map.of(
                "operator", operator, "reason", reason));
    }
}
