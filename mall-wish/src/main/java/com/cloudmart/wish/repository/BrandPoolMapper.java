package com.cloudmart.wish.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.wish.entity.BrandPool;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface BrandPoolMapper extends BaseMapper<BrandPool> {

    /** WISH-03：发布（DRAFT→ACTIVE，冻结规则版本；CAS 防并发双发布） */
    @Update("UPDATE wish_brand_pool SET status = 'ACTIVE', rule_version = 1, updated_at = NOW(3) "
            + "WHERE id = #{poolId} AND status = 'DRAFT'")
    int publishDraft(@Param("poolId") Long poolId);

    /** WISH-03：结算者 CAS（ACTIVE→SETTLING——双实例只有一个赢家） */
    @Update("UPDATE wish_brand_pool SET status = 'SETTLING', updated_at = NOW(3) "
            + "WHERE id = #{poolId} AND status = 'ACTIVE'")
    int casActiveToSettling(@Param("poolId") Long poolId);

    /** WISH-03：结算完成（SETTLING→ENDED） */
    @Update("UPDATE wish_brand_pool SET status = 'ENDED', updated_at = NOW(3) "
            + "WHERE id = #{poolId} AND status = 'SETTLING'")
    int casSettlingToEnded(@Param("poolId") Long poolId);

    /** WISH-03：rewardSpec 非法（SETTLING→CONFIG_ERROR——不假成功结束，修复后可重试） */
    @Update("UPDATE wish_brand_pool SET status = 'CONFIG_ERROR', updated_at = NOW(3) "
            + "WHERE id = #{poolId} AND status = 'SETTLING'")
    int casSettlingToConfigError(@Param("poolId") Long poolId);

    /** WISH-03：手动结束（ACTIVE→CANCELLED——未达标无奖励终态） */
    @Update("UPDATE wish_brand_pool SET status = 'CANCELLED', updated_at = NOW(3) "
            + "WHERE id = #{poolId} AND status = 'ACTIVE'")
    int casActiveToCancelled(@Param("poolId") Long poolId);

    /** WISH-03：容量 CAS（current_count < target_count 才递增——防超员） */
    @Update("UPDATE wish_brand_pool SET current_count = current_count + 1, updated_at = NOW(3) "
            + "WHERE id = #{poolId} AND current_count < target_count")
    int incrementWithinTarget(@Param("poolId") Long poolId);
}
