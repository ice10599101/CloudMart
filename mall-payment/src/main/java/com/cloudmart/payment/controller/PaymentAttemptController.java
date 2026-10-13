package com.cloudmart.payment.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.payment.channel.MockChannelSigner;
import com.cloudmart.payment.channel.PaymentScene;
import com.cloudmart.payment.service.PaymentAttemptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 支付尝试与渠道回调（PAY-01/R09）。
 *
 * <p>尝试创建：登录用户针对订单发起——只收 orderId + channel + scene + requestKey，
 * 归属/可支付状态/金额全部服务端判定（客户端金额不参与）；同渠道复用、换渠道关闭
 * 旧尝试并显式告知；返回 attemptId/status/action/expiresAt，端侧只按 action 解释
 * 收单参数，禁止猜测 payParams。回调：按渠道验签，验签/重放/金额不符一律拒绝且
 * 留痕。MOCK 渠道仅测试环境启用（生产 profile 由启动自检拒绝）。</p>
 */
@RestController
@RequestMapping("/payment-attempts")
@RequiredArgsConstructor
@Tag(name = "支付尝试", description = "PAY-01/R09 支付尝试与回调")
public class PaymentAttemptController {

    private final PaymentAttemptService attemptService;
    private final org.springframework.core.env.Environment environment;

    public record CreateAttemptRequest(@NotNull Long orderId,
                                       @NotBlank String channel,
                                       @NotBlank String scene,
                                       @NotBlank @Size(max = 64) String requestKey,
                                       @Size(max = 500) String returnUrl) {
    }

    @PostMapping
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    @Operation(summary = "创建支付尝试", description = "R09：单订单单活动尝试；归属/金额服务端判定；"
            + "同渠道复用、换渠道关闭旧尝试（replacedPrevious=true 明示）；"
            + "returnUrl 必须命中服务端白名单；返回 attemptId/status/action/expiresAt 及 mock 回调载荷（仅 MOCK）")
    public ApiResponse<Map<String, Object>> create(
            @Parameter(description = "用户 ID（已验签令牌主体）", hidden = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @RequestBody CreateAttemptRequest request) {
        var result = attemptService.createAttempt(userId, request.orderId(), request.channel(),
                PaymentScene.fromCode(request.scene()), request.requestKey(), request.returnUrl());
        Map<String, Object> body = attemptService.toAttemptView(result.attempt());
        body.put("replacedPrevious", result.replacedPrevious());
        body.put("previousAttemptId", result.previousAttemptId());
        if ("MOCK".equals(result.attempt().getChannel())) {
            MockChannelSigner.SignedNotification notification = attemptService.buildMockNotification(result.attempt());
            body.put("mockCallback", Map.of(
                    "merchantPaymentNo", result.attempt().getMerchantPaymentNo(),
                    "amount", notification.amount(),
                    "providerTxnNo", notification.providerTxnNo(),
                    "notificationId", notification.notificationId(),
                    "signature", notification.signature()));
        }
        return ApiResponse.ok(body);
    }

    @GetMapping("/order/{orderId}")
    @Operation(summary = "查询订单支付尝试状态", description = "收银台轮询真值源（attempts 台账独立于旧 payment 表）；"
            + "返回该订单最近一次尝试；归属经订单服务权威校验")
    public ApiResponse<Map<String, Object>> getByOrder(
            @Parameter(description = "用户 ID（已验签令牌主体）", hidden = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "订单ID", required = true) @PathVariable("orderId") Long orderId) {
        return ApiResponse.ok(attemptService.findViewByOrder(userId, orderId));
    }

    public record MockNotifyRequest(
            @NotBlank String merchantPaymentNo,
            @NotBlank String amount,
            @NotBlank String providerTxnNo,
            @NotBlank String notificationId,
            @NotBlank String signature) {
    }

    @PostMapping("/mock-callbacks")
    @Operation(summary = "MOCK 渠道回调", description = "HMAC 验签 + 重放防护 + 金额核对；"
            + "仅测试环境启用（生产由启动自检拒绝 MOCK 渠道）")
    public ApiResponse<Map<String, Object>> mockCallback(@Valid @RequestBody MockNotifyRequest request) {
        // PAY-01：生产环境拒绝 MOCK 渠道回调（真实渠道回调由验签 adapter 处理）
        if (environment.acceptsProfiles(org.springframework.core.env.Profiles.of("prod"))) {
            throw new BusinessException("MOCK_CHANNEL_FORBIDDEN", "生产环境不允许 MOCK 渠道回调");
        }
        String result = attemptService.handleMockNotify(request.merchantPaymentNo(),
                request.amount(), request.providerTxnNo(), request.notificationId(), request.signature());
        return ApiResponse.ok(Map.of("result", result));
    }
}
