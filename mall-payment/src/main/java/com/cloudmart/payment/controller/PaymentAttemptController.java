package com.cloudmart.payment.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.payment.channel.MockChannelSigner;
import com.cloudmart.payment.service.PaymentAttemptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 支付尝试与渠道回调（PAY-01）。
 *
 * <p>尝试创建：登录用户针对订单发起——只收 orderId + channel，归属/可支付状态/
 * 金额全部服务端判定（客户端金额不参与）；回调：按渠道验签，验签/重放/金额不符
 * 一律拒绝且留痕。MOCK 渠道仅测试环境启用（生产 profile 由启动自检拒绝）。</p>
 */
@RestController
@RequestMapping("/payment-attempts")
@RequiredArgsConstructor
@Tag(name = "支付尝试", description = "PAY-01 支付尝试与回调")
public class PaymentAttemptController {

    private final PaymentAttemptService attemptService;
    private final org.springframework.core.env.Environment environment;

    public record CreateAttemptRequest(@NotNull Long orderId,
                                       @NotBlank String channel) {
    }

    @PostMapping
    @Operation(summary = "创建支付尝试", description = "单订单单活动尝试；归属/金额服务端判定；"
            + "返回商户支付号与 mock 回调载荷（仅 MOCK 渠道）")
    public ApiResponse<Map<String, Object>> create(
            @Parameter(description = "用户 ID（已验签令牌主体）", hidden = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @RequestBody CreateAttemptRequest request) {
        var attempt = attemptService.createAttempt(userId, request.orderId(), request.channel());
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("merchantPaymentNo", attempt.getMerchantPaymentNo());
        result.put("status", attempt.getStatus());
        result.put("amount", attempt.getAmount());
        result.put("expiresAt", attempt.getExpiresAt() == null ? null : attempt.getExpiresAt().toString());
        if ("MOCK".equals(attempt.getChannel())) {
            MockChannelSigner.SignedNotification notification = attemptService.buildMockNotification(attempt);
            result.put("mockCallback", Map.of(
                    "merchantPaymentNo", attempt.getMerchantPaymentNo(),
                    "amount", notification.amount(),
                    "providerTxnNo", notification.providerTxnNo(),
                    "notificationId", notification.notificationId(),
                    "signature", notification.signature()));
        }
        return ApiResponse.ok(result);
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
