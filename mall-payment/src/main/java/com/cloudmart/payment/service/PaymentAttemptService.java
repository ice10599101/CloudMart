package com.cloudmart.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.async.EventEnvelope;
import com.cloudmart.common.async.outbox.OutboxService;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.payment.channel.ChannelRegistry;
import com.cloudmart.payment.channel.MockChannelSigner;
import com.cloudmart.payment.channel.PaymentAction;
import com.cloudmart.payment.channel.PaymentScene;
import com.cloudmart.payment.dto.OrderInternalInfoDTO;
import com.cloudmart.payment.entity.PaymentAttempt;
import com.cloudmart.payment.feign.OrderFeignClient;
import com.cloudmart.payment.repository.PaymentAttemptMapper;
import com.cloudmart.payment.repository.PaymentNotifyLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 支付尝试服务（PAY-01/R09）：渠道尝试台账 + 通知验签/幂等。
 *
 * <ul>
 *   <li>创建尝试：只收 orderId + channel + scene + requestKey——归属与应付额从订单
 *       服务权威获取（客户端金额不参与）；渠道必须在白名单且支持该场景；
 *       returnUrl 必须命中服务端前缀白名单；</li>
 *   <li>同单活动尝试唯一：MySQL 命名锁串行化 + uk_attempt_active_order 数据库硬约束；
 *       同渠道同场景复用，换渠道/换场景关闭旧尝试并明确告知（replacedPrevious），
 *       绝不静默返回另一渠道的活动尝试；</li>
 *   <li>requestKey 稳定幂等：同键返回同一笔；同键异参 409 IDEMPOTENCY_KEY_REUSED；</li>
 *   <li>通知处理：验签（HMAC）→ 通知唯一登记（重放拒绝）→ 金额核对 →
 *       CAS PENDING→SUCCESS（金额一致）→ PAYMENT_SUCCEEDED Outbox 与确认同事务；</li>
 *   <li>签名密钥未配置时 fail-closed：拒绝签发与处理。</li>
 * </ul>
 */
@Slf4j
@Service
public class PaymentAttemptService {

    /** 同订单并发创建时命名锁等待秒数；超时让客户端稍后重试，不无限等待 */
    private static final int ORDER_LOCK_TIMEOUT_SECONDS = 5;

    private final PaymentAttemptMapper attemptMapper;
    private final PaymentNotifyLogMapper notifyLogMapper;
    private final OutboxService outboxService;
    private final OrderFeignClient orderFeignClient;
    private final MockChannelSigner signer;
    private final ChannelRegistry channelRegistry;
    private final PaymentChannelQueryService channelQueryService;
    private final List<String> allowedReturnUrlPrefixes;

    public PaymentAttemptService(PaymentAttemptMapper attemptMapper,
                                 PaymentNotifyLogMapper notifyLogMapper,
                                 OutboxService outboxService,
                                 OrderFeignClient orderFeignClient,
                                 ChannelRegistry channelRegistry,
                                 PaymentChannelQueryService channelQueryService,
                                 @Value("${payment.mock-channel-secret:${CLOUDMART_SERVICE_TOKEN_SECRET:}}") String secret,
                                 @Value("${payment.return-url-allowed-prefixes:}") String allowedReturnUrlPrefixesCsv) {
        this.attemptMapper = attemptMapper;
        this.notifyLogMapper = notifyLogMapper;
        this.outboxService = outboxService;
        this.orderFeignClient = orderFeignClient;
        this.channelRegistry = channelRegistry;
        this.channelQueryService = channelQueryService;
        this.signer = new MockChannelSigner(secret);
        String[] prefixTokens = allowedReturnUrlPrefixesCsv == null
                ? new String[0] : allowedReturnUrlPrefixesCsv.split(",");
        this.allowedReturnUrlPrefixes = Arrays.stream(prefixTokens)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    /** 创建尝试结果：attempt 为权威台账行；replacedPrevious/previousAttemptId 明示渠道切换。 */
    public record CreateAttemptResult(PaymentAttempt attempt, boolean replacedPrevious, Long previousAttemptId) {
    }

    /**
     * 创建支付尝试（R09）。
     *
     * @param channelCode 渠道编码（白名单校验）
     * @param scene       H5/MINIAPP/APP（场景能力校验）
     * @param requestKey  客户端幂等键（必填，≤64；同键稳定返回同一笔）
     * @param returnUrl   H5 回跳地址（可空；非空必须命中服务端前缀白名单）
     */
    @Transactional
    public CreateAttemptResult createAttempt(Long userId, Long orderId, String channelCode,
                                             PaymentScene scene, String requestKey, String returnUrl) {
        String channel = normalizeRequest(channelCode, scene, requestKey, returnUrl);

        // 归属 + 可支付状态 + 服务端金额（权威）
        OrderInternalInfoDTO order = requireOwnedOrder(userId, orderId);
        if (!"PENDING_PAYMENT".equals(order.status())) {
            throw new BusinessException("PAYMENT_STATUS_ERROR", "订单当前状态不允许支付");
        }
        if (order.payAmount() == null || order.payAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("PAYMENT_AMOUNT_INVALID", "订单应付金额异常");
        }

        Integer lockResult = attemptMapper.acquireOrderLock(orderId, ORDER_LOCK_TIMEOUT_SECONDS);
        if (lockResult == null || lockResult != 1) {
            throw new BusinessException("PAYMENT_ATTEMPT_BUSY", "支付创建处理中，请稍后重试");
        }
        try {
            // requestKey 稳定幂等：同键必须同参，否则 409（与 R25 端上终态语义一致）
            PaymentAttempt keyed = attemptMapper.findByRequestKey(requestKey);
            if (keyed != null) {
                if (orderId.equals(keyed.getOrderId())
                        && channel.equals(keyed.getChannel())
                        && scene.name().equals(keyed.getScene())) {
                    return new CreateAttemptResult(keyed, false, null);
                }
                throw new BusinessException("IDEMPOTENCY_KEY_REUSED", "幂等键已用于其他支付请求");
            }

            PaymentAttempt latest = attemptMapper.selectLatestByOrderForUpdate(orderId);
            Long previousAttemptId = null;
            if (latest != null && "PENDING".equals(latest.getStatus())) {
                if (channel.equals(latest.getChannel()) && scene.name().equals(latest.getScene())) {
                    // 同渠道同场景：复用原活动尝试（幂等提交/重复点击不换号）
                    return new CreateAttemptResult(latest, false, null);
                }
                // 换渠道/换场景：CAS 关闭旧尝试，明确告知，不静默返回他渠道尝试
                int closed = attemptMapper.closeActiveById(latest.getId());
                if (closed == 0) {
                    throw new BusinessException("PAYMENT_ATTEMPT_BUSY", "原支付尝试状态变更中，请稍后重试");
                }
                previousAttemptId = latest.getId();
                log.info("[R09] 换渠道关闭原支付尝试 orderId={} oldAttemptId={} {}({}) -> {}({})",
                        orderId, previousAttemptId, latest.getChannel(), latest.getScene(), channel, scene);
            }

            String merchantPaymentNo = "MP" + System.currentTimeMillis()
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            try {
                attemptMapper.insertAttempt(orderId, merchantPaymentNo, channel, scene.name(),
                        requestKey, order.payAmount(), "CNY");
            } catch (DuplicateKeyException ex) {
                // uk_attempt_active_order/uk_attempt_request_key 兜底并发
                throw new BusinessException("PAYMENT_ATTEMPT_BUSY", "支付创建处理中，请稍后重试");
            }
            PaymentAttempt attempt = attemptMapper.findByMerchantPaymentNo(merchantPaymentNo);
            attempt.setExpiresAt(LocalDateTime.now().plusMinutes(15));
            attemptMapper.updateById(attempt);
            log.info("[PAY01] 支付尝试已创建 orderId={} no={} channel={} scene={} amount={} replacedPrev={}",
                    orderId, merchantPaymentNo, channel, scene, attempt.getAmount(), previousAttemptId != null);
            return new CreateAttemptResult(attempt, previousAttemptId != null, previousAttemptId);
        } finally {
            attemptMapper.releaseOrderLock(orderId);
        }
    }

    /** 入参归一化与 fail-closed 校验，返回大写渠道编码。 */
    private String normalizeRequest(String channelCode, PaymentScene scene, String requestKey, String returnUrl) {
        if (scene == null) {
            throw new BusinessException("PAYMENT_SCENE_UNSUPPORTED", "支付场景不能为空");
        }
        if (requestKey == null || requestKey.isBlank()) {
            throw new BusinessException("VALIDATION_ERROR", "requestKey 不能为空");
        }
        String trimmedKey = requestKey.trim();
        if (trimmedKey.length() > 64) {
            throw new BusinessException("VALIDATION_ERROR", "requestKey 长度不能超过 64");
        }
        if (channelCode == null || channelCode.isBlank()) {
            throw new BusinessException("VALIDATION_ERROR", "支付渠道不能为空");
        }
        String channel = channelCode.trim().toUpperCase();
        // 渠道白名单（凭据）前置，场景能力次之——顺序不影响拒绝结果，错误码各自精确
        if (!channelRegistry.isChannelAllowed(channel)) {
            throw new BusinessException("PAYMENT_CHANNEL_UNAVAILABLE", "支付渠道不可用或未接入: " + channel);
        }
        PaymentAction action = channelQueryService.actionOf(channel, scene)
                .orElseThrow(() -> new BusinessException("PAYMENT_SCENE_UNSUPPORTED",
                        "渠道 " + channel + " 不支持场景 " + scene));
        validateReturnUrl(channel, scene, action, returnUrl);
        return channel;
    }

    /** R09 回跳地址服务端白名单：任何非空 returnUrl 必须命中显式配置前缀，默认空=全拒绝。 */
    private void validateReturnUrl(String channel, PaymentScene scene, PaymentAction action, String returnUrl) {
        if (returnUrl == null || returnUrl.isBlank()) {
            return;
        }
        String candidate = returnUrl.trim();
        boolean allowed = allowedReturnUrlPrefixes.stream().anyMatch(candidate::startsWith);
        if (!allowed) {
            log.warn("[R09] returnUrl 未命中白名单 channel={} scene={} returnUrl={}", channel, scene, candidate);
            throw new BusinessException("PAYMENT_RETURN_URL_FORBIDDEN", "非法的支付回跳地址");
        }
    }

    /**
     * 按订单查最近一次支付尝试视图（收银台轮询真值源）。
     *
     * <p>attempts 台账独立于旧 payment 表——入账后旧视图查不到，客户端必须轮询本端点；
     * 归属经订单服务权威校验（payment_attempt 不冗余 user_id 列）。</p>
     */
    public java.util.Map<String, Object> findViewByOrder(Long userId, Long orderId) {
        requireOwnedOrder(userId, orderId);
        PaymentAttempt attempt = attemptMapper.selectOne(new LambdaQueryWrapper<PaymentAttempt>()
                .eq(PaymentAttempt::getOrderId, orderId)
                .orderByDesc(PaymentAttempt::getId)
                .last("LIMIT 1"));
        if (attempt == null) {
            throw new BusinessException("PAYMENT_ATTEMPT_NOT_FOUND", "该订单暂无支付尝试");
        }
        return toAttemptView(attempt);
    }

    /** R09 尝试视图：attemptId/status/action/expiresAt——action 由目录按渠道+场景给出。 */
    public java.util.Map<String, Object> toAttemptView(PaymentAttempt attempt) {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("attemptId", attempt.getId());
        result.put("merchantPaymentNo", attempt.getMerchantPaymentNo());
        result.put("channel", attempt.getChannel());
        result.put("scene", attempt.getScene());
        result.put("action", resolveAction(attempt));
        result.put("status", attempt.getStatus());
        result.put("amount", attempt.getAmount());
        result.put("expiresAt", attempt.getExpiresAt() == null ? null : attempt.getExpiresAt().toString());
        return result;
    }

    private String resolveAction(PaymentAttempt attempt) {
        if (attempt.getChannel() == null || attempt.getScene() == null) {
            return PaymentAction.NONE.name();
        }
        try {
            return channelQueryService.actionOf(attempt.getChannel(), PaymentScene.valueOf(attempt.getScene()))
                    .map(Enum::name)
                    .orElse(PaymentAction.NONE.name());
        } catch (IllegalArgumentException ex) {
            return PaymentAction.NONE.name();
        }
    }

    /** 归属校验（订单服务权威）：订单不存在/非本人一律拒绝 */
    private OrderInternalInfoDTO requireOwnedOrder(Long userId, Long orderId) {
        ApiResponse<OrderInternalInfoDTO> orderResp = orderFeignClient.getOrderInfo(orderId);
        if (orderResp == null || !orderResp.success() || orderResp.data() == null) {
            throw new BusinessException("PAYMENT_NOT_FOUND", "订单不存在");
        }
        OrderInternalInfoDTO order = orderResp.data();
        if (!userId.equals(order.userId())) {
            throw new BusinessException("PAYMENT_FORBIDDEN", "无权操作该订单的支付");
        }
        return order;
    }

    /**
     * 处理渠道回调（PAY-01）：验签 → 通知唯一登记 → 金额核对 → CAS 确认。
     *
     * @return "SUCCESS" 入账 / "DUPLICATE" 重放或已入账 / "REJECTED" 验签或金额不符
     */
    @Transactional
    public String handleMockNotify(String merchantPaymentNo, String amount,
                                   String providerTxnNo, String notificationId, String signature) {
        boolean signatureValid = signer.verify(merchantPaymentNo, amount, providerTxnNo, notificationId, signature);
        if (!signatureValid) {
            notifyLogMapper.record("MOCK", notificationId == null ? "null" : notificationId,
                    false, "REJECTED", "验签失败");
            log.warn("[PAY01] 回调验签失败，拒绝 no={} notificationId={}", merchantPaymentNo, notificationId);
            return "REJECTED";
        }
        // 通知唯一登记：0 行 = 重放通知（同 channel+notificationId 已处理过）
        if (notifyLogMapper.record("MOCK", notificationId, true, "SUCCESS", "验证通过") == 0) {
            notifyLogMapper.record("MOCK", notificationId, true, "DUPLICATE", "重放通知");
            log.warn("[PAY01] 重放通知被拒绝 no={} notificationId={}", merchantPaymentNo, notificationId);
            return "DUPLICATE";
        }
        PaymentAttempt attempt = attemptMapper.findByMerchantPaymentNo(merchantPaymentNo);
        if (attempt == null) {
            throw new BusinessException("PAYMENT_ATTEMPT_NOT_FOUND", "支付尝试不存在");
        }
        // 金额核对：渠道回调金额必须与服务端应付一致
        if (attempt.getAmount() == null || attempt.getAmount().compareTo(new BigDecimal(amount)) != 0) {
            log.warn("[PAY01] 回调金额不符 no={} expected={} got={}", merchantPaymentNo, attempt.getAmount(), amount);
            return "REJECTED";
        }
        // CAS PENDING → SUCCESS（幂等一次性；重放已在通知登记层拒绝）
        int confirmed = attemptMapper.confirmSuccess(merchantPaymentNo, attempt.getAmount(), providerTxnNo);
        if (confirmed == 0) {
            return "DUPLICATE";
        }
        // 与确认同事务登记 PAYMENT_SUCCEEDED（下游：订单推进 PAID）
        outboxService.record(EventEnvelope.of("PAYMENT_SUCCEEDED", 2,
                String.valueOf(attempt.getOrderId()), 1, null,
                "{\"orderId\":" + attempt.getOrderId()
                        + ",\"paymentId\":" + attempt.getId()
                        + ",\"merchantPaymentNo\":\"" + attempt.getMerchantPaymentNo()
                        + "\",\"amount\":\"" + attempt.getAmount().toPlainString()
                        + "\",\"currency\":\"CNY\"}"));
        log.info("[PAY01] 支付成功入账 no={} orderId={} amount={}", merchantPaymentNo, attempt.getOrderId(), attempt.getAmount());
        return "SUCCESS";
    }

    /** 为 MOCK 渠道生成签名回调载荷（测试环境回调用；生产不注册 mock 回调入口） */
    public MockChannelSigner.SignedNotification buildMockNotification(PaymentAttempt attempt) {
        return signer.sign(attempt.getMerchantPaymentNo(),
                attempt.getAmount().toPlainString(),
                "MOCKTXN" + attempt.getId());
    }
}
