package com.cloudmart.coupon.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.coupon.entity.ExchangeCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 兑换码 Mapper
 * <p>
 * 提供基于状态匹配的原子更新方法，避免并发场景下的状态错乱。
 * </p>
 */
@Mapper
public interface ExchangeCodeMapper extends BaseMapper<ExchangeCode> {

    /**
     * 原子地标记兑换码为已兑换（CAS 语义）
     * <p>
     * 仅当当前状态为 UNUSED 时才能更新为 EXCHANGED，避免并发重复兑换。
     * </p>
     *
     * @param code        兑换码
     * @param userId      兑换用户ID
     * @param exchangedAt 兑换时间
     * @return 受影响行数，0 表示状态不匹配
     */
    @Update("UPDATE exchange_codes SET status = 'EXCHANGED', user_id = #{userId}, "
            + "exchanged_at = #{exchangedAt}, updated_at = NOW() "
            + "WHERE code = #{code} AND status = 'UNUSED'")
    int markExchangedIfUnused(@Param("code") String code,
                              @Param("userId") Long userId,
                              @Param("exchangedAt") java.time.LocalDateTime exchangedAt);

    /**
     * 原子地作废兑换码
     *
     * @param code 兑换码
     * @return 受影响行数
     */
    @Update("UPDATE exchange_codes SET status = 'DISABLED', updated_at = NOW() "
            + "WHERE code = #{code} AND status = 'UNUSED'")
    int disableIfUnused(@Param("code") String code);

    /**
     * 回滚兑换状态：将已兑换的码恢复为未兑换（补偿操作）
     * <p>
     * 用于兑换后续环节（如优惠券领取）失败时的补偿回滚。
     * </p>
     *
     * @param code 兑换码
     * @return 受影响行数
     */
    @Update("UPDATE exchange_codes SET status = 'UNUSED', user_id = NULL, exchanged_at = NULL, updated_at = NOW() "
            + "WHERE code = #{code} AND status = 'EXCHANGED'")
    int rollbackExchanged(@Param("code") String code);
}
