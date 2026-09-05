package com.cloudmart.wish.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.wish.entity.WishAccountDeletion;
import com.cloudmart.wish.service.AccountDeletionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * 账号注销宽限期 Controller（合规 34.2 / API 2.13，四AB A1）。
 *
 * <p>契约：POST /my/account-deletion（验证码申请注销）、
 * POST /my/account/cancel（撤回，EXECUTED 409）、GET /my/account-deletion
 * （状态回显）。宽限期 30 天，到期由 data-export 同款内部任务执行。</p>
 */
@RestController
@RequestMapping("/my")
@Tag(name = "账号注销", description = "注销申请/撤回/状态（宽限期 30 天）")
@RequiredArgsConstructor
public class AccountDeletionController {

    private final AccountDeletionService accountDeletionService;

    @PostMapping("/account-deletion/code")
    @Operation(summary = "发送注销验证码", description = "6 位数字，Redis 存哈希 5 分钟有效；"
            + "生产环境经短信/邮件通道下发，echo-code 配置仅供开发/测试回显")
    public ApiResponse<Map<String, Object>> sendCode(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        final String code = accountDeletionService.sendDeletionCode(userId);
        return ApiResponse.ok(Map.of(
                "sent", true,
                "expiresInSeconds", 300,
                "devCode", code == null ? "" : code));
    }

    @PostMapping("/account-deletion")
    @Operation(summary = "申请注销", description = "验证码校验后进入 30 天宽限期；"
            + "errors: 400 WISH_CONFIRM_CODE_INVALID / 409 WISH_DELETION_PENDING / WISH_DELETION_EXECUTED")
    public ApiResponse<Map<String, Object>> apply(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @RequestBody Map<String, String> body) {
        final WishAccountDeletion deletion =
                accountDeletionService.apply(userId, body.get("confirmCode"), body.get("reason"));
        return ApiResponse.ok(Map.of(
                "userId", deletion.getUserId(),
                "executeAfter", deletion.getExecuteAfter().truncatedTo(ChronoUnit.SECONDS).toString(),
                "canCancel", true,
                "cancelDeadline", deletion.getExecuteAfter().truncatedTo(ChronoUnit.SECONDS).toString()));
    }

    @PostMapping("/account/cancel")
    @Operation(summary = "取消注销", description = "宽限期内撤回；errors: 409 WISH_DELETION_EXECUTED")
    public ApiResponse<Map<String, Object>> cancel(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        final WishAccountDeletion deletion = accountDeletionService.cancel(userId);
        return ApiResponse.ok(Map.of(
                "userId", deletion.getUserId(),
                "cancelled", true,
                "cancelledAt", (deletion.getCanceledAt() == null ? "" : deletion.getCanceledAt().truncatedTo(ChronoUnit.SECONDS).toString())));
    }

    @GetMapping("/account-deletion")
    @Operation(summary = "注销状态回显", description = "无申请记录返回 data=null")
    public ApiResponse<WishAccountDeletion> status(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(accountDeletionService.getStatus(userId));
    }

    /** 文档 1.6 旧客户端别名：DELETE /my/account 与 DELETE /my/account-deletion 均为撤回 */
    @DeleteMapping({"/account", "/account-deletion"})
    @Operation(summary = "撤回注销（旧别名）", description = "与 POST /my/account/cancel 等价")
    public ApiResponse<Map<String, Object>> cancelAlias(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        final WishAccountDeletion deletion = accountDeletionService.cancel(userId);
        return ApiResponse.ok(Map.of(
                "userId", deletion.getUserId(),
                "cancelled", true,
                "cancelledAt", (deletion.getCanceledAt() == null ? "" : deletion.getCanceledAt().truncatedTo(ChronoUnit.SECONDS).toString())));
    }
}
