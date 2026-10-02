package com.cloudmart.seckill.mq;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.seckill.dto.SeckillMessage;
import com.cloudmart.seckill.entity.SeckillRequest;
import com.cloudmart.seckill.feign.OrderQueryFeignClient;
import com.cloudmart.seckill.service.SeckillRequestService;
import com.cloudmart.seckill.support.SeckillRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 秒杀请求恢复任务（T09）：发送未知不直接回补——所有发送失败/掉电窗口
 * 期间停留 PENDING 的请求，统一按请求事实收口：
 * <ol>
 *   <li>重发窗口（attempts &lt; 3）：按快照重发下单消息，消费侧 request_key 幂等；</li>
 *   <li>对账窗口（attempts ≥ 3）：先查订单是否已建（requestId=订单 request_key）——
 *       已建补落 SUCCESS，未建才终态失败并释放 DB/Redis 占用。</li>
 * </ol>
 * 状态迁移全部 CAS，多实例并发调度无害。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillRequestRecovery {

    /** 单请求最大重发次数，超过进入订单对账 */
    static final int MAX_SEND_ATTEMPTS = 3;
    private static final Duration[] RETRY_BACKOFFS = {
            Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofSeconds(90)
    };
    private static final int SCAN_LIMIT = 200;

    private final SeckillRequestService requestService;
    private final SeckillMQProducer mqProducer;
    private final OrderQueryFeignClient orderQueryFeignClient;
    private final StringRedisTemplate redisTemplate;

    @Scheduled(fixedDelay = 5000)
    public void recoverPendingRequests() {
        List<SeckillRequest> due = requestService.findPendingDue(LocalDateTime.now(), SCAN_LIMIT);
        for (SeckillRequest request : due) {
            try {
                recover(request);
            } catch (Exception e) {
                log.warn("[T09] 秒杀请求恢复失败（下轮重试）requestId={}: {}", request.getRequestId(), e.getMessage());
            }
        }
    }

    private void recover(SeckillRequest request) {
        int attempts = request.getSendAttempts() == null ? 0 : request.getSendAttempts();
        if (attempts < MAX_SEND_ATTEMPTS) {
            resend(request, attempts);
            return;
        }
        reconcileWithOrder(request);
    }

    private void resend(SeckillRequest request, int attempts) {
        int nextAttempts = attempts + 1;
        Duration backoff = RETRY_BACKOFFS[Math.min(nextAttempts - 1, RETRY_BACKOFFS.length - 1)];
        boolean scheduled = requestService.scheduleRetry(request.getRequestId(), nextAttempts,
                LocalDateTime.now().plus(backoff));
        if (!scheduled) {
            return;
        }
        SeckillMessage message = new SeckillMessage(request.getRequestId(), request.getUserId(),
                request.getActivityId(), request.getProductId(), request.getSkuId(),
                request.getSeckillPrice(), request.getQuantity());
        try {
            mqProducer.sendSeckillMessage(message);
            log.info("[T09] 秒杀请求重发完成 requestId={} attempts={}", request.getRequestId(), nextAttempts);
        } catch (Exception e) {
            log.warn("[T09] 秒杀请求重发未知结果 requestId={} attempts={}: {}",
                    request.getRequestId(), nextAttempts, e.getMessage());
        }
    }

    /** 对账窗口：订单存在补 SUCCESS；确认未建才终态失败并释放占用 */
    private void reconcileWithOrder(SeckillRequest request) {
        Long orderId;
        try {
            orderId = orderQueryFeignClient.findOrderIdByRequestId(request.getRequestId()).data();
        } catch (BusinessException e) {
            // fail-closed：查不清不动终态，顺延到下轮
            log.warn("[T09] 对账查询不可用，顺延 requestId={}", request.getRequestId());
            return;
        }
        if (orderId != null) {
            if (requestService.settleSuccess(request.getRequestId(), orderId)) {
                log.info("[T09] 对账补落成功 requestId={} orderId={}", request.getRequestId(), orderId);
            }
            return;
        }
        if (requestService.settleFailed(request.getRequestId(), "排队超时，请重新发起")) {
            requestService.releaseSeat(request.getProductId());
            releaseRedisProjection(request);
            log.info("[T09] 排队超时终态失败并释放占用 requestId={}", request.getRequestId());
        }
    }

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
            log.warn("[T09] 恢复释放 Redis 投影失败 requestId={}: {}", request.getRequestId(), e.getMessage());
        }
    }
}
