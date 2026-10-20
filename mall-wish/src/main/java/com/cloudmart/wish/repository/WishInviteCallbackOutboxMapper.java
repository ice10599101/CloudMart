package com.cloudmart.wish.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.wish.entity.WishInviteCallbackOutbox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface WishInviteCallbackOutboxMapper extends BaseMapper<WishInviteCallbackOutbox> {

    // ---------------- T18 邀请回调可观测指标（Grafana/Prometheus 告警口径） ----------------

    /** 待交付积压总量（含退避中） */
    @Select("SELECT COUNT(*) FROM wish_invite_callback_outbox WHERE status = 'PENDING'")
    long countPending();

    /** 到期且可执行积压（与 sweep 同一筛选口径） */
    @Select("SELECT COUNT(*) FROM wish_invite_callback_outbox "
            + "WHERE status = 'PENDING' AND (next_retry_at IS NULL OR next_retry_at <= #{now})")
    long countDuePending(@Param("now") LocalDateTime now);

    /** 达重试上限、待人工处置的回调数 */
    @Select("SELECT COUNT(*) FROM wish_invite_callback_outbox WHERE status = 'FAILED'")
    long countFailed();

    /** 最老待交付行的创建时间（空队列返回 null） */
    @Select("SELECT MIN(created_at) FROM wish_invite_callback_outbox WHERE status = 'PENDING'")
    LocalDateTime oldestPendingCreatedAt();
}
