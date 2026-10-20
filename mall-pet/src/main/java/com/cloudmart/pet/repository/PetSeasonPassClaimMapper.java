package com.cloudmart.pet.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.pet.entity.PetSeasonPassClaim;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/** 通行证逐档领取事实 Mapper（R05 权威 + T18 公平补发调度）。 */
public interface PetSeasonPassClaimMapper extends BaseMapper<PetSeasonPassClaim> {

    /**
     * T18 公平扫描：只取「已到期且可执行」的 CLAIM_PENDING 行——
     * next_retry_at 为空或已到、租约空闲或已过期。失败行被退避到未来后不再占头部。
     */
    @Select("SELECT * FROM pet_season_pass_claim "
            + "WHERE status = 'CLAIM_PENDING' "
            + "AND (next_retry_at IS NULL OR next_retry_at <= #{now}) "
            + "AND (lease_owner IS NULL OR lease_until < #{now}) "
            + "ORDER BY id ASC LIMIT #{limit}")
    List<PetSeasonPassClaim> selectDuePending(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /**
     * T18 租约抢占（CAS）：仅当 CLAIM_PENDING 且已到期且租约空闲/过期时置 owner/until。
     * 返回 1=抢占成功（本实例独占交付）；0=失败（其他实例持有或状态已变）。
     */
    @Update("UPDATE pet_season_pass_claim SET lease_owner = #{owner}, lease_until = #{leaseUntil}, "
            + "last_attempt_at = #{now} "
            + "WHERE id = #{id} AND status = 'CLAIM_PENDING' "
            + "AND (next_retry_at IS NULL OR next_retry_at <= #{now}) "
            + "AND (lease_owner IS NULL OR lease_until < #{now})")
    int tryLease(@Param("id") Long id, @Param("owner") String owner,
                 @Param("leaseUntil") LocalDateTime leaseUntil, @Param("now") LocalDateTime now);

    // ---------------- T18 运营指标（Grafana/Prometheus 告警口径） ----------------

    /** 待补发积压总量（含退避中与租约中） */
    @Select("SELECT COUNT(*) FROM pet_season_pass_claim WHERE status = 'CLAIM_PENDING'")
    long countPending();

    /** 到期且可执行积压（与 selectDuePending 同一筛选口径） */
    @Select("SELECT COUNT(*) FROM pet_season_pass_claim "
            + "WHERE status = 'CLAIM_PENDING' "
            + "AND (next_retry_at IS NULL OR next_retry_at <= #{now}) "
            + "AND (lease_owner IS NULL OR lease_until < #{now})")
    long countDuePending(@Param("now") LocalDateTime now);

    /** 人工处置队列（达重试上限或不可重试） */
    @Select("SELECT COUNT(*) FROM pet_season_pass_claim WHERE status = 'NEEDS_ATTENTION'")
    long countNeedsAttention();

    /** 最老待补发行的创建时间（用于最老等待时长告警；空队列返回 null） */
    @Select("SELECT MIN(created_at) FROM pet_season_pass_claim WHERE status = 'CLAIM_PENDING'")
    LocalDateTime oldestPendingCreatedAt();
}
