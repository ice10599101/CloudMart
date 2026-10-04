package com.cloudmart.order.mq;

import com.cloudmart.common.async.EventEnvelope;
import com.cloudmart.common.async.inbox.InboxService;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.order.config.RocketMQConfig;
import com.cloudmart.order.entity.AfterSaleCase;
import com.cloudmart.order.feign.RefundFeignClient;
import com.cloudmart.order.repository.AfterSaleCaseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 售后退款提交消费者（T04）：AFTER_SALE_REFUND_SUBMIT Outbox 事件驱动案件退款
 * 提交——审批（仅退款）/质检通过（退货退款）同事务登记，本消费者在事务外调用
 * 支付内部退款接口（refundNo=RFC{caseId}，金额/订单/币种以案件事实为准），
 * 远程付款不嵌在审批长事务里。
 *
 * <p>幂等：支付侧 refundNo 唯一键 + Inbox 去重——重复消费/重试不重复发款；
 * 退款失败（渠道未接入/超退拒绝）抛出重试，重试耗尽进死信由运营在案件上
 * 人工处置（案件保持 APPROVED，可查可重试）。受理不等于到账：订单状态由
 * REFUND_SUCCEEDED 事件按已退累计推进。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = RocketMQConfig.ORDER_TOPIC,
        consumerGroup = RocketMQConfig.CG_ORDER_AFTER_SALE_REFUND,
        selectorExpression = RocketMQConfig.TAG_AFTER_SALE
)
public class AfterSaleRefundSubmitConsumer implements RocketMQListener<Map<String, Object>> {

    static final String CONSUMER = "order-after-sale-refund-submit";
    static final String EVENT_TYPE = "AFTER_SALE_REFUND_SUBMIT";

    private final InboxService inboxService;
    private final AfterSaleCaseMapper caseMapper;
    private final RefundFeignClient refundFeignClient;

    @Override
    @Transactional
    public void onMessage(Map<String, Object> message) {
        String eventId = (String) message.get("eventId");
        if (eventId == null || eventId.isBlank()) {
            log.warn("[T04] 售后退款提交事件缺少 eventId，拒绝消费");
            return;
        }
        if (!EVENT_TYPE.equals(message.get("eventType"))) {
            log.warn("[T04] 售后退款提交消费者收到未知事件类型 {}", message.get("eventType"));
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) message.get("payload");
        Long caseId = payload == null || payload.get("caseId") == null
                ? null : ((Number) payload.get("caseId")).longValue();
        if (caseId == null) {
            log.warn("[T04] 售后退款提交事件缺少 caseId eventId={}", eventId);
            return;
        }
        EventEnvelope envelope = new EventEnvelope(eventId, EVENT_TYPE, 1,
                String.valueOf(caseId), 1, 0L, null, null);
        if (inboxService.beginConsume(CONSUMER, envelope) == InboxService.ConsumeDecision.SKIP) {
            log.info("[T04] 售后退款提交事件已消费（幂等跳过） eventId={} caseId={}", eventId, caseId);
            return;
        }
        try {
            AfterSaleCase entity = caseMapper.selectById(caseId);
            if (entity == null) {
                log.warn("[T04] 售后退款提交：案件不存在 caseId={}", caseId);
                inboxService.completeConsume(CONSUMER, envelope);
                return;
            }
            if (!AfterSaleCase.STATUS_APPROVED.equals(entity.getStatus())
                    || entity.getRefundNo() == null || entity.getRefundAmount() == null) {
                // 已被回填 REFUNDED（重试竞态）或非受理态：幂等吸收，不再发款
                log.info("[T04] 案件非可提交状态，幂等跳过 caseId={} status={} refundNo={}",
                        caseId, entity.getStatus(), entity.getRefundNo());
                inboxService.completeConsume(CONSUMER, envelope);
                return;
            }
            submitCaseRefund(entity);
            inboxService.completeConsume(CONSUMER, envelope);
            log.info("[T04] 售后退款已提交支付侧 caseNo={} refundNo={} amount={}",
                    entity.getCaseNo(), entity.getRefundNo(), entity.getRefundAmount());
        } catch (Exception e) {
            inboxService.failConsume(CONSUMER, envelope, e.getMessage());
            throw e;
        }
    }

    /** 提交支付内部退款（订单/金额/币种以案件事实为准；refundNo 服务端派生） */
    private void submitCaseRefund(AfterSaleCase entity) {
        Map<String, Object> refundRequest = new HashMap<>();
        refundRequest.put("refundNo", entity.getRefundNo());
        refundRequest.put("orderId", entity.getOrderId());
        refundRequest.put("amount", entity.getRefundAmount());
        refundRequest.put("currency", "CNY");
        refundRequest.put("reasonCode", "AFTER_SALE_" + entity.getType());
        ApiResponse<Map<String, Object>> refundResp = refundFeignClient.createRefund(refundRequest);
        if (refundResp == null || !refundResp.success() || refundResp.data() == null) {
            throw new BusinessException("REFUND_SUBMIT_FAILED",
                    "退款提交失败（caseNo=" + entity.getCaseNo() + "），请稍后重试");
        }
        String channelStatus = String.valueOf(refundResp.data().get("status"));
        // SUCCEEDED/PROCESSING/UNKNOWN 均为已受理；渠道最终事实由 REFUND_SUCCEEDED
        // 事件回填案件与订单汇总（受理不等于到账）
        log.info("[T04] 支付侧受理退款 caseNo={} refundNo={} channelStatus={}",
                entity.getCaseNo(), entity.getRefundNo(), channelStatus);
    }
}
