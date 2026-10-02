package com.cloudmart.seckill.service;

import com.cloudmart.seckill.entity.SeckillRequest;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 秒杀请求事实服务（T09）：购买资格占用的权威层。
 *
 * <p>DB 唯一键 {@code (activity_id, product_id, user_id)} 是购买限额事实，
 * {@code available_stock} 原子预减是库存权威——Redis 只做流量预筛。
 * 全部状态迁移为 CAS 条件更新：并发重发起、重复消费、恢复任务竞争下
 * 只有一方生效，重复投递无害。</p>
 */
public interface SeckillRequestService {

    /** 请求已存在（他方持有有效资格）时抛出，携带既有事实 */
    class SeatExistsException extends RuntimeException {
        private final SeckillRequest existing;

        public SeatExistsException(SeckillRequest existing) {
            super("seckill seat exists: " + existing.getRequestId());
            this.existing = existing;
        }

        public SeckillRequest existing() {
            return existing;
        }
    }

    /** DB 口径售罄（available_stock 不足） */
    class SeatSoldOutException extends RuntimeException {
        public SeatSoldOutException(Long productId) {
            super("seckill sold out (db): productId=" + productId);
        }
    }

    /**
     * 占用购买资格（事务内：库存原子预减 + 请求事实落库，价格/数量冻结快照）。
     *
     * <p>同用户已有 PENDING/SUCCESS 资格 → 抛 {@link SeatExistsException}
     * （只有终态失败可重新发起）；FAILED 则复用原行换新 requestId 重新排队；
     * 库存不足 → 抛 {@link SeatSoldOutException}。并发插入冲突走回滚重读。</p>
     */
    SeckillRequest holdSeat(Long userId, Long activityId, Long productId, Long skuId,
                            java.math.BigDecimal seckillPrice, int quantity);

    /** 按用户+商品查既有请求（无则 null） */
    SeckillRequest findByUser(Long userId, Long activityId, Long productId);

    /** 按 requestId 查（无则 null） */
    SeckillRequest findByRequestId(String requestId);

    /** CAS PENDING→SUCCESS；返回是否本次生效 */
    boolean settleSuccess(String requestId, Long orderId);

    /** CAS PENDING→FAILED；返回是否本次生效（调用方据此决定是否释放占用） */
    boolean settleFailed(String requestId, String failReason);

    /** 终态失败后释放 DB 库存占用（0 = 已达总量上界，重复释放无害） */
    boolean releaseSeat(Long productId);

    /** 恢复调度：登记下次动作时间（0 = 请求已终态） */
    boolean scheduleRetry(String requestId, int attempts, LocalDateTime nextRetryAt);

    /** 恢复扫描：到期且仍排队的请求 */
    List<SeckillRequest> findPendingDue(LocalDateTime dueBefore, int limit);
}
