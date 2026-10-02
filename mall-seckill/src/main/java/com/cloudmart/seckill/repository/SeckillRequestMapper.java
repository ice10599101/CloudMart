package com.cloudmart.seckill.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.seckill.entity.SeckillRequest;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 秒杀请求事实 Mapper（T09）：状态迁移全部 CAS 条件更新——
 * 并发重发/重复消费/恢复任务竞争下只有一方生效，重复投递无害。
 */
public interface SeckillRequestMapper extends BaseMapper<SeckillRequest> {

    /**
     * 权威库存占用：DB 原子预减（Redis 只是预筛）。
     *
     * @return 0 = DB 口径已售罄
     */
    @Update("UPDATE seckill_products SET available_stock = available_stock - 1 "
            + "WHERE id = #{productId} AND available_stock >= 1")
    int holdStock(@Param("productId") Long productId);

    /** 释放占用（终态失败）：available_stock 不得超过 total_stock 兜底 */
    @Update("UPDATE seckill_products SET available_stock = available_stock + 1 "
            + "WHERE id = #{productId} AND available_stock < total_stock")
    int releaseStock(@Param("productId") Long productId);

    /** CAS PENDING → SUCCESS（结果回写闭环；0 = 已终态，重复消费无害） */
    @Update("UPDATE seckill_request SET status = 'SUCCESS', order_id = #{orderId} "
            + "WHERE request_id = #{requestId} AND status = 'PENDING'")
    int markSuccess(@Param("requestId") String requestId, @Param("orderId") Long orderId);

    /** CAS PENDING → FAILED（终态失败留痕；0 = 已终态） */
    @Update("UPDATE seckill_request SET status = 'FAILED', fail_reason = #{failReason} "
            + "WHERE request_id = #{requestId} AND status = 'PENDING'")
    int markFailed(@Param("requestId") String requestId, @Param("failReason") String failReason);

    /**
     * 终态失败重发起：同 row 复用（购买限额事实不删），换新 requestId 重新排队。
     *
     * @return 0 = 状态已不是 FAILED（他方先行）
     */
    @Update("UPDATE seckill_request SET request_id = #{requestId}, status = 'PENDING', "
            + "seckill_price = #{seckillPrice}, quantity = #{quantity}, sku_id = #{skuId}, "
            + "order_id = NULL, fail_reason = NULL, send_attempts = 0, next_retry_at = #{nextRetryAt} "
            + "WHERE id = #{id} AND status = 'FAILED'")
    int reinitiate(SeckillRequest row);

    /** 恢复调度：登记下次重发时间（0 = 请求已终态，放弃调度） */
    @Update("UPDATE seckill_request SET send_attempts = #{attempts}, next_retry_at = #{nextRetryAt} "
            + "WHERE request_id = #{requestId} AND status = 'PENDING'")
    int scheduleRetry(@Param("requestId") String requestId, @Param("attempts") int attempts,
                      @Param("nextRetryAt") LocalDateTime nextRetryAt);

    /** 恢复扫描：到期且仍排队的请求（发送未知/掉电窗口统一由此收口） */
    @Select("SELECT * FROM seckill_request WHERE status = 'PENDING' AND next_retry_at <= #{dueBefore} "
            + "ORDER BY id LIMIT #{limit}")
    List<SeckillRequest> selectPendingDue(@Param("dueBefore") LocalDateTime dueBefore,
                                          @Param("limit") int limit);
}
