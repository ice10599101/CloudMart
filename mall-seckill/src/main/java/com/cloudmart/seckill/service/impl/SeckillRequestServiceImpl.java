package com.cloudmart.seckill.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.seckill.entity.SeckillRequest;
import com.cloudmart.seckill.repository.SeckillRequestMapper;
import com.cloudmart.seckill.service.SeckillRequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 秒杀请求事实服务实现（T09）。占用路径的并发语义：
 * <ul>
 *   <li>先读后写窗口内的并发插入以 DB 唯一键裁决——败者事务整体回滚
 *       （库存预减一并回滚），重读胜者事实后返回既有资格；</li>
 *   <li>FAILED 复用原行换新 requestId 重排队：购买限额事实不物理删除，
 *       Redis 投影可随时由本表重建。</li>
 * </ul>
 */
@Service
public class SeckillRequestServiceImpl implements SeckillRequestService {

    private static final Logger log = LoggerFactory.getLogger(SeckillRequestServiceImpl.class);

    /** 请求出生即调度首次恢复检查：发送未知/进程掉电统一由恢复任务收口 */
    static final java.time.Duration FIRST_RETRY_DELAY = java.time.Duration.ofSeconds(10);

    private final SeckillRequestMapper requestMapper;

    public SeckillRequestServiceImpl(SeckillRequestMapper requestMapper) {
        this.requestMapper = requestMapper;
    }

    @Override
    @Transactional
    public SeckillRequest holdSeat(Long userId, Long activityId, Long productId, Long skuId,
                                   java.math.BigDecimal seckillPrice, int quantity) {
        SeckillRequest existing = findByUser(userId, activityId, productId);
        if (existing != null) {
            if (!SeckillRequest.STATUS_FAILED.equals(existing.getStatus())) {
                throw new SeatExistsException(existing);
            }
            // 终态失败重发起：席位已在终态失败时释放，重发起必须重新预减库存
            // （否则白拿座位）；在事务内执行，唯一键冲突时与预减一并回滚
            if (requestMapper.holdStock(productId) == 0) {
                throw new SeatSoldOutException(productId);
            }
            // CAS 复用原行（并发重发起只有一方生效）
            SeckillRequest reinit = new SeckillRequest();
            reinit.setId(existing.getId());
            reinit.setRequestId(newRequestId());
            reinit.setStatus(SeckillRequest.STATUS_PENDING);
            reinit.setSkuId(skuId);
            reinit.setSeckillPrice(seckillPrice);
            reinit.setQuantity(quantity);
            reinit.setSendAttempts(0);
            reinit.setNextRetryAt(LocalDateTime.now().plus(FIRST_RETRY_DELAY));
            if (requestMapper.reinitiate(reinit) == 0) {
                throw new SeatExistsException(requireByUser(userId, activityId, productId));
            }
            return requireByUser(userId, activityId, productId);
        }

        // 权威库存预减（0 行 = DB 售罄）；随后插入请求事实，
        // 唯一键冲突抛出时整个事务回滚（含上面已成功的预减）
        if (requestMapper.holdStock(productId) == 0) {
            throw new SeatSoldOutException(productId);
        }
        SeckillRequest request = new SeckillRequest();
        request.setRequestId(newRequestId());
        request.setActivityId(activityId);
        request.setProductId(productId);
        request.setSkuId(skuId);
        request.setUserId(userId);
        request.setQuantity(quantity);
        request.setSeckillPrice(seckillPrice);
        request.setStatus(SeckillRequest.STATUS_PENDING);
        request.setSendAttempts(0);
        request.setNextRetryAt(LocalDateTime.now().plus(FIRST_RETRY_DELAY));
        try {
            requestMapper.insert(request);
        } catch (DuplicateKeyException race) {
            SeckillRequest winner = requireByUser(userId, activityId, productId);
            if (!SeckillRequest.STATUS_FAILED.equals(winner.getStatus())) {
                throw new SeatExistsException(winner);
            }
            // 并发重发起败者：本次回滚，让胜者的 PENDING 事实返回给调用方
            throw new SeatExistsException(winner);
        }
        log.info("[T09] 秒杀资格已占用 userId={} activityId={} productId={} requestId={} price={}",
                userId, activityId, productId, request.getRequestId(), seckillPrice);
        return request;
    }

    private SeckillRequest requireByUser(Long userId, Long activityId, Long productId) {
        SeckillRequest row = findByUser(userId, activityId, productId);
        if (row == null) {
            throw new IllegalStateException("seckill request vanished: userId=" + userId
                    + " activityId=" + activityId + " productId=" + productId);
        }
        return row;
    }

    private String newRequestId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    @Override
    public SeckillRequest findByUser(Long userId, Long activityId, Long productId) {
        return requestMapper.selectOne(new LambdaQueryWrapper<SeckillRequest>()
                .eq(SeckillRequest::getUserId, userId)
                .eq(SeckillRequest::getActivityId, activityId)
                .eq(SeckillRequest::getProductId, productId));
    }

    @Override
    public SeckillRequest findByRequestId(String requestId) {
        return requestMapper.selectOne(new LambdaQueryWrapper<SeckillRequest>()
                .eq(SeckillRequest::getRequestId, requestId));
    }

    @Override
    public boolean settleSuccess(String requestId, Long orderId) {
        return requestMapper.markSuccess(requestId, orderId) == 1;
    }

    @Override
    public boolean settleFailed(String requestId, String failReason) {
        return requestMapper.markFailed(requestId, failReason) == 1;
    }

    @Override
    public boolean releaseSeat(Long productId) {
        return requestMapper.releaseStock(productId) == 1;
    }

    @Override
    public boolean scheduleRetry(String requestId, int attempts, LocalDateTime nextRetryAt) {
        return requestMapper.scheduleRetry(requestId, attempts, nextRetryAt) == 1;
    }

    @Override
    public List<SeckillRequest> findPendingDue(LocalDateTime dueBefore, int limit) {
        return requestMapper.selectPendingDue(dueBefore, limit);
    }
}
