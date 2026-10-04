package com.cloudmart.payment.service;

import com.cloudmart.common.async.EventEnvelope;
import com.cloudmart.common.async.outbox.OutboxService;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.payment.entity.PaymentAttempt;
import com.cloudmart.payment.entity.RefundOrder;
import com.cloudmart.payment.repository.PaymentAttemptMapper;
import com.cloudmart.payment.repository.RefundOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
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
     * <p>T03 修复语义：</p>
     * <ul>
     *   <li>支付尝试行锁内完成"同号重放判定 → 可退余额核算 → 落库"——锁等待方的
     *       同号重放不会被已占用余额误报成超退（QA07 锁竞争场景）；</li>
     *   <li>落库一律使用权威解析的 {@code attempt.getId()}——旧实现透传调用方可空的
     *       attemptId，严格 SQL 模式下 INSERT IGNORE 写入默认 0，按真实 attempt 汇总
     *       的超退保护失效（可重复全额退款）；</li>
     *   <li>去掉吞数据错误的 INSERT IGNORE，改为普通 INSERT + uk 冲突显式重放判定，
     *       异常 SQL 不再被当成功；</li>
     *   <li>历史 refund 行 payment_attempt_id 为 0 的，重放时按原订单与渠道事实
     *       自愈回填权威 attemptId（不删记录、不猜金额）。</li>
     * </ul>
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

        // 锁原成功支付尝试行（并发退款串行化），核对支付事实；
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

        // T03：支付尝试锁内再次查询同 refundNo——先处理相同请求重放（并发锁等待后
        // 首请求已落库，此处必须先命中重放返回原单，再谈可退余额，否则误报超退）
        RefundOrder existing = refundMapper.findByRefundNo(refundNo);
        if (existing != null) {
            assertSameFact(existing, orderId, amount, currency);
            backfillLegacyAttemptAssociation(refundNo, existing, attempt);
            log.info("[T03] 退款重放返回原结果, refundNo={}, status={}", refundNo, existing.getStatus());
            return existing;
        }

        // 超退核算：累计占用 = 成功 + 在途（FAILED 已确认不执行不占用）
        BigDecimal activeRefunded = refundMapper.sumActiveRefundAmount(attempt.getId());
        if (activeRefunded.add(amount).compareTo(attempt.getAmount()) > 0) {
            throw new BusinessException("REFUND_AMOUNT_EXCEEDS",
                    "累计退款将超过实收（实收 " + attempt.getAmount() + "，在途/已退 " + activeRefunded + "）");
        }

        // T03：普通 INSERT——uk(refund_no) 冲突由下方显式重放判定，不再 INSERT IGNORE
        // 吞掉数据错误；落库 attemptId 恒为权威 attempt.getId()
        try {
            refundMapper.insertRefund(refundNo, attempt.getId(), orderId, amount, currency, reasonCode);
        } catch (DuplicateKeyException ex) {
            // 并发同号兜底（极端竞态：锁内重检与插入之间被其他实例抢先）
            RefundOrder winner = refundMapper.findByRefundNo(refundNo);
            if (winner == null) {
                throw new BusinessException("REFUND_CREATE_FAILED", "退款单创建失败");
            }
            assertSameFact(winner, orderId, amount, currency);
            log.info("[T03] 并发同号退款返回胜者, refundNo={}, status={}", refundNo, winner.getStatus());
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
        log.info("[T02] 退款成功入账, refundNo={}, orderId={}, amount={}, attemptId={}, providerRefundNo={}",
                refundNo, orderId, amount, attempt.getId(), providerRefundNo);
        return refundMapper.findByRefundNo(refundNo);
    }

    /** 按退款号查询渠道处理状态（内部查询；UNKNOWN/PROCESSING 由查单任务收敛） */
    public RefundOrder findByRefundNo(String refundNo) {
        return refundMapper.findByRefundNo(refundNo);
    }

    /**
     * 同号异参拒绝（幂等冲突）。attemptId 不参与比对：订单侧通常不持有 attemptId，
     * 历史行的关联值亦可能为默认 0——订单/金额/币种一致即同一退款事实。
     */
    private void assertSameFact(RefundOrder existing, Long orderId, BigDecimal amount, String currency) {
        boolean same = existing.getOrderId().equals(orderId)
                && existing.getRefundAmount().compareTo(amount) == 0
                && existing.getCurrency().equals(currency);
        if (!same) {
            throw new BusinessException("REFUND_NO_CONFLICT", "退款号已绑定不同退款事实（幂等冲突）");
        }
    }

    /**
     * T03 历史数据自愈：旧行 payment_attempt_id 缺失/为 0 时，在持有支付尝试锁、
     * 订单与币种事实核验通过的前提下回填权威 attemptId——后续按 attempt 汇总的
     * 超退核算才能看到该笔退款。指向其他非零 attempt 的异常行不自动改写（人工核查）。
     */
    private void backfillLegacyAttemptAssociation(String refundNo, RefundOrder existing, PaymentAttempt attempt) {
        Long existingAttemptId = existing.getPaymentAttemptId();
        if (existingAttemptId != null && existingAttemptId != 0L) {
            if (!existingAttemptId.equals(attempt.getId())) {
                log.warn("[T03] 退款单关联了不同的支付尝试，需人工核查 refundNo={} bound={} authoritative={}",
                        refundNo, existingAttemptId, attempt.getId());
            }
            return;
        }
        int repaired = refundMapper.backfillAttemptId(refundNo, attempt.getId());
        if (repaired == 1) {
            existing.setPaymentAttemptId(attempt.getId());
            log.info("[T03] 历史退款单支付关联自愈 refundNo={} attemptId={}", refundNo, attempt.getId());
        }
    }
}
