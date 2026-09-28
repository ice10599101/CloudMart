package com.cloudmart.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.async.EventEnvelope;
import com.cloudmart.common.async.outbox.OutboxService;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.payment.channel.MockChannelSigner;
import com.cloudmart.payment.dto.OrderInternalInfoDTO;
import com.cloudmart.payment.entity.PaymentAttempt;
import com.cloudmart.payment.feign.OrderFeignClient;
import com.cloudmart.payment.repository.PaymentAttemptMapper;
import com.cloudmart.payment.repository.PaymentNotifyLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 支付尝试服务（PAY-01）：渠道尝试台账 + 通知验签/幂等。
 *
 * <ul>
 *   <li>创建尝试：只收 orderId + channel——归属与应付额从订单服务权威获取
 *       （客户端金额不参与）；单订单单活动尝试（DB 条件插入保证）；
 *       商户支付号唯一，有效期 15 分钟（与订单超时对齐）；</li>
 *   <li>通知处理：验签（HMAC）→ 通知唯一登记（重放拒绝）→ 金额核对 →
 *       CAS PENDING→SUCCESS（金额一致）→ PAYMENT_SUCCEEDED Outbox 与确认同事务；</li>
 *   <li>签名密钥未配置时 fail-closed：拒绝签发与处理。</li>
 * </ul>
 */
@Slf4j
@Service
public class PaymentAttemptService {

    private final PaymentAttemptMapper attemptMapper;
    private final PaymentNotifyLogMapper notifyLogMapper;
    private final OutboxService outboxService;
    private final OrderFeignClient orderFeignClient;
    private final MockChannelSigner signer;

    public PaymentAttemptService(PaymentAttemptMapper attemptMapper,
                                 PaymentNotifyLogMapper notifyLogMapper,
                                 OutboxService outboxService,
                                 OrderFeignClient orderFeignClient,
                                 @Value("${payment.mock-channel-secret:${CLOUDMART_SERVICE_TOKEN_SECRET:}}") String secret) {
        this.attemptMapper = attemptMapper;
        this.notifyLogMapper = notifyLogMapper;
        this.outboxService = outboxService;
        this.orderFeignClient = orderFeignClient;
        this.signer = new MockChannelSigner(secret);
    }

    /** 创建支付尝试（PAY-01）：只收 orderId + channel——归属/状态/金额全部服务端判定。 */
    @Transactional
    public PaymentAttempt createAttempt(Long userId, Long orderId, String channel) {
        // 归属 + 可支付状态 + 服务端金额（权威）
        ApiResponse<OrderInternalInfoDTO> orderResp = orderFeignClient.getOrderInfo(orderId);
        if (orderResp == null || !orderResp.success() || orderResp.data() == null) {
            throw new BusinessException("PAYMENT_NOT_FOUND", "订单不存在");
        }
        OrderInternalInfoDTO order = orderResp.data();
        if (!userId.equals(order.userId())) {
            throw new BusinessException("PAYMENT_FORBIDDEN", "无权为该订单创建支付");
        }
        if (!"PENDING_PAYMENT".equals(order.status())) {
            throw new BusinessException("PAYMENT_STATUS_ERROR", "订单当前状态不允许支付");
        }
        if (order.payAmount() == null || order.payAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("PAYMENT_AMOUNT_INVALID", "订单应付金额异常");
        }

        PaymentAttempt existing = findActiveByOrder(orderId);
        if (existing != null) {
            return existing;
        }
        String merchantPaymentNo = "MP" + System.currentTimeMillis()
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        int inserted = attemptMapper.insertSingleActive(orderId, merchantPaymentNo,
                channel, order.payAmount(), "CNY");
        if (inserted == 0) {
            // 并发窗口：另一请求已创建活动尝试，返回既有
            PaymentAttempt winner = findActiveByOrder(orderId);
            if (winner == null) {
                throw new BusinessException("PAYMENT_ATTEMPT_FAILED", "支付尝试创建失败");
            }
            return winner;
        }
        PaymentAttempt attempt = attemptMapper.findByMerchantPaymentNo(merchantPaymentNo);
        attempt.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        attemptMapper.updateById(attempt);
        log.info("[PAY01] 支付尝试已创建 orderId={} no={} amount={}", orderId, merchantPaymentNo, attempt.getAmount());
        return attempt;
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

    private PaymentAttempt findActiveByOrder(Long orderId) {
        return attemptMapper.selectOne(new LambdaQueryWrapper<PaymentAttempt>()
                .eq(PaymentAttempt::getOrderId, orderId)
                .eq(PaymentAttempt::getStatus, "PENDING")
                .last("LIMIT 1"));
    }
}
