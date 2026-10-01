package com.cloudmart.payment.service;

import com.cloudmart.common.async.EventEnvelope;
import com.cloudmart.common.async.outbox.OutboxService;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.payment.entity.PaymentAttempt;
import com.cloudmart.payment.entity.RefundOrder;
import com.cloudmart.payment.repository.PaymentAttemptMapper;
import com.cloudmart.payment.repository.RefundOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 退款单服务（T02）：渠道退款事实的唯一权威。
 *
 * <p>核心规则：</p>
 * <ul>
 *   <li>refundNo 幂等：同号同参重放返回原结果，同号异参 409；</li>
 *   <li>超退核算：锁原成功支付尝试行后计算"已成功 + 处理中"退款总额，
 *       禁止超过实收（QA07：并发部分退款总额不超实收）；</li>
 *   <li>状态分离：审批只推进 PROCESSING，渠道确认才 SUCCEEDED——本服务返回的
 *       状态是渠道事实，不是审批结果；订单侧不得把审批成功当退款成功；</li>
 *   <li>MOCK 渠道（dev/test）完成同构流程：REQUESTED → PROCESSING → SUCCEEDED
 *       + REFUND_SUCCEEDED Outbox 同一事务；真实渠道未接入明确失败，
 *       不产生"看似已退款"的记录；</li>
 *   <li>退款成功不改支付尝试的 SUCCESS 原事实（可追溯）。</li>
 * </ul>
 */
@Slf4j
@Service
public class RefundService {

    private final RefundOrderMapper refundMapper;
    private final PaymentAttemptMapper attemptMapper;
    private final OutboxService outboxService;

    public RefundService(RefundOrderMapper refundMapper,
                         PaymentAttemptMapper attemptMapper,
                         OutboxService outboxService) {
        this.refundMapper = refundMapper;
        this.attemptMapper = attemptMapper;
        this.outboxService = outboxService;
    }

    /**
     * 创建并提交退款（订单服务审批后经内部接口调用；refundNo 幂等键）。
     *
     * @return 退款单视图（status 为渠道事实：MOCK 同步 SUCCEEDED）
     */
    @Transactional
    public RefundOrder createAndSubmit(String refundNo, Long orderId, Long paymentAttemptId,
                                       BigDecimal amount, String currency, String reasonCode) {
        if (refundNo == null || refundNo.isBlank() || refundNo.length() > 64) {
            throw new BusinessException("REFUND_NO_INVALID", "退款号缺失或非法");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException("REFUND_AMOUNT_INVALID", "退款金额必须为正数");
        }

        // 同号重放/冲突判定：先查既有单（幂等事实；attemptId 未提供时不参与比对）
        RefundOrder existing = refundMapper.findByRefundNo(refundNo);
        if (existing != null) {
            assertSameFact(existing, orderId, paymentAttemptId, amount, currency);
            log.info("[T02] 退款重放返回原结果, refundNo={}, status={}", refundNo, existing.getStatus());
            return existing;
        }

        // 锁原成功支付尝试行（并发退款串行化），核对支付事实与累计退款；
        // 订单侧不持有 attemptId 时由本服务按订单权威解析最近一笔成功支付
        PaymentAttempt attempt = paymentAttemptId == null
                ? attemptMapper.selectLatestSuccessByOrderForUpdate(orderId)
                : attemptMapper.selectByIdForUpdate(paymentAttemptId);
        if (attempt == null || !"SUCCESS".equals(attempt.getStatus())) {
            throw new BusinessException("REFUND_ATTEMPT_INVALID", "原支付尝试不存在或未成功，不能退款");
        }
        if (!attempt.getOrderId().equals(orderId)) {
            throw new BusinessException("REFUND_ATTEMPT_MISMATCH", "支付尝试与订单不匹配");
        }
        if (attempt.getCurrency() != null && !attempt.getCurrency().equals(currency)) {
            throw new BusinessException("REFUND_CURRENCY_MISMATCH", "退款币种与原支付不一致");
        }
        if (attempt.getChannel() == null || !"MOCK".equals(attempt.getChannel())) {
            // T02：真实渠道未接入必须明确失败，不产生"看似可退款"的记录
            throw new BusinessException("REFUND_CHANNEL_UNAVAILABLE",
                    "渠道退款链路未接入（当前渠道 " + attempt.getChannel() + "），请人工核查处理");
        }
        BigDecimal activeRefunded = refundMapper.sumActiveRefundAmount(attempt.getId());
        if (activeRefunded.add(amount).compareTo(attempt.getAmount()) > 0) {
            throw new BusinessException("REFUND_AMOUNT_EXCEEDS",
                    "累计退款将超过实收（实收 " + attempt.getAmount() + "，在途/已退 " + activeRefunded + "）");
        }

        // 幂等插入（并发同号由 uk 兜底）
        int inserted = refundMapper.insertRefund(refundNo, paymentAttemptId, orderId,
                amount, currency, reasonCode);
        if (inserted == 0) {
            RefundOrder winner = refundMapper.findByRefundNo(refundNo);
            if (winner == null) {
                throw new BusinessException("REFUND_CREATE_FAILED", "退款单创建失败");
            }
            assertSameFact(winner, orderId, paymentAttemptId, amount, currency);
            return winner;
        }

        // 审批推进：REQUESTED → PROCESSING（渠道提交）
        if (refundMapper.markProcessing(refundNo) == 0) {
            throw new BusinessException("REFUND_STATE_CONFLICT", "退款单状态已变更，请按原退款号查询");
        }

        // 渠道确认（MOCK 同构：渠道即时确认）→ SUCCEEDED，与 REFUND_SUCCEEDED Outbox 同一事务
        String providerRefundNo = "MOCKRFND" + System.currentTimeMillis();
        if (refundMapper.markSucceeded(refundNo, providerRefundNo) == 0) {
            throw new BusinessException("REFUND_STATE_CONFLICT", "退款确认失败，状态已变更");
        }
        outboxService.record(EventEnvelope.of("REFUND_SUCCEEDED", 1, String.valueOf(orderId), 1, null,
                "{\"orderId\":" + orderId
                        + ",\"refundNo\":\"" + refundNo
                        + "\",\"amount\":\"" + amount.toPlainString()
                        + "\",\"currency\":\"" + currency + "\"}"));
        log.info("[T02] 退款成功入账, refundNo={}, orderId={}, amount={}, providerRefundNo={}",
                refundNo, orderId, amount, providerRefundNo);
        return refundMapper.findByRefundNo(refundNo);
    }

    /** 按退款号查询渠道处理状态（内部查询；UNKNOWN/PROCESSING 由查单任务收敛） */
    public RefundOrder findByRefundNo(String refundNo) {
        return refundMapper.findByRefundNo(refundNo);
    }

    /** 同号异参拒绝（幂等冲突） */
    private void assertSameFact(RefundOrder existing, Long orderId, Long attemptId,
                                BigDecimal amount, String currency) {
        boolean same = existing.getOrderId().equals(orderId)
                && existing.getRefundAmount().compareTo(amount) == 0
                && existing.getCurrency().equals(currency);
        if (attemptId != null) {
            same = same && existing.getPaymentAttemptId().equals(attemptId);
        }
        if (!same) {
            throw new BusinessException("REFUND_NO_CONFLICT", "退款号已绑定不同退款事实（幂等冲突）");
        }
    }
}
