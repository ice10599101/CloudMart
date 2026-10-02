package com.cloudmart.seckill.mq;

import com.cloudmart.common.async.EventEnvelope;
import com.cloudmart.common.async.inbox.InboxService;
import com.cloudmart.seckill.config.RocketMQConfig;
import com.cloudmart.seckill.entity.SeckillRequest;
import com.cloudmart.seckill.service.SeckillRequestService;
import com.cloudmart.seckill.support.SeckillRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 秒杀结果回写消费者（T09 结果闭环）：mall-order 建单成功/业务失败经 Outbox
 * 发布 SECKILL_RESULT，本消费者以 Inbox 幂等消费——100 次重复消息一份结果。
 *
 * <p>成功：CAS PENDING→SUCCESS 关联订单；失败：CAS PENDING→FAILED 并释放
 * DB/Redis 占用（库存回归可售）。系统异常重抛触发 MQ 重试，重试耗尽进入
 * 死信由运营处置（请求行保持 PENDING，恢复任务会按事实重新对账）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = RocketMQConfig.ORDER_TOPIC,
        consumerGroup = RocketMQConfig.CG_SECKILL_RESULT,
        selectorExpression = RocketMQConfig.ORDER_TAG_SECKILL_RESULT
)
public class SeckillResultConsumer implements RocketMQListener<Map<String, Object>> {

    static final String INBOX_CONSUMER = "seckill-request-result";
    static final String EVENT_TYPE = "SECKILL_RESULT";

    private final InboxService inboxService;
    private final SeckillRequestService requestService;
    private final StringRedisTemplate redisTemplate;

    @Override
    @Transactional
    public void onMessage(Map<String, Object> message) {
        String eventId = (String) message.get("eventId");
        if (eventId == null || eventId.isBlank()) {
            // LC03：生产者全经 Outbox 信封——缺 eventId 的旧形状拒绝消费，不绕过幂等
            log.warn("[T09] 秒杀结果事件缺少 eventId（旧形状），拒绝消费");
            return;
        }
        Object eventType = message.get("eventType");
        if (!EVENT_TYPE.equals(eventType)) {
            log.warn("[T09] 秒杀结果消费者收到未知事件类型 {} eventId={}", eventType, eventId);
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) message.get("payload");
        String requestId = payload == null || payload.get("requestId") == null
                ? null : String.valueOf(payload.get("requestId"));
        if (requestId == null || requestId.isBlank()) {
            log.warn("[T09] 秒杀结果事件缺少 requestId eventId={}", eventId);
            return;
        }
        boolean success = Boolean.TRUE.equals(payload.get("success"));
        Long orderId = payload.get("orderId") == null ? null : ((Number) payload.get("orderId")).longValue();
        String reason = payload.get("reason") == null ? null : String.valueOf(payload.get("reason"));

        if (inboxService.beginConsume(INBOX_CONSUMER,
                new EventEnvelope(eventId, EVENT_TYPE, 1, requestId, 1, 0L, requestId, null))
                == InboxService.ConsumeDecision.SKIP) {
            return;
        }
        try {
            settle(requestId, success, orderId, reason);
            inboxService.completeConsume(INBOX_CONSUMER,
                    new EventEnvelope(eventId, EVENT_TYPE, 1, requestId, 1, 0L, requestId, null));
        } catch (Exception e) {
            inboxService.failConsume(INBOX_CONSUMER,
                    new EventEnvelope(eventId, EVENT_TYPE, 1, requestId, 1, 0L, requestId, null), e.getMessage());
            throw e;
        }
    }

    private void settle(String requestId, boolean success, Long orderId, String reason) {
        SeckillRequest request = requestService.findByRequestId(requestId);
        if (request == null) {
            log.warn("[T09] 秒杀结果事件找不到请求事实 requestId={}（可能已被清理），忽略", requestId);
            return;
        }
        if (success) {
            if (requestService.settleSuccess(requestId, orderId)) {
                log.info("[T09] 秒杀成功落终态 requestId={} orderId={}", requestId, orderId);
            }
            return;
        }
        if (requestService.settleFailed(requestId, reason == null ? "秒杀失败" : reason)) {
            // 本次 CAS 生效方负责释放占用（重复消费方不重复释放）
            requestService.releaseSeat(request.getProductId());
            releaseRedisProjection(request);
            log.info("[T09] 秒杀失败落终态并释放占用 requestId={} reason={}", requestId, reason);
        }
    }

    /** Redis 投影释放（尽力而为）：库存计数 +1、用户集合移除；故障由执行路径的陈旧集合自愈与重建兜底 */
    private void releaseRedisProjection(SeckillRequest request) {
        try {
            String stockKey = SeckillRedisKeys.stockKey(request.getActivityId(), request.getProductId());
            if (Boolean.TRUE.equals(redisTemplate.hasKey(stockKey))) {
                redisTemplate.opsForValue().increment(stockKey);
            }
            redisTemplate.opsForSet().remove(
                    SeckillRedisKeys.userSetKey(request.getActivityId(), request.getProductId()),
                    String.valueOf(request.getUserId()));
        } catch (Exception e) {
            log.warn("[T09] Redis 投影释放失败（DB 事实为准，投影可重建）requestId={}: {}",
                    request.getRequestId(), e.getMessage());
        }
    }
}
