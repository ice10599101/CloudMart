package com.cloudmart.coupon.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.coupon.entity.CouponClaimCounter;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CouponClaimCounterMapper extends BaseMapper<CouponClaimCounter> {

    /** 初始化计数行（幂等） */
    @Insert("INSERT IGNORE INTO coupon_claim_counter (user_id, template_id, claimed_count) "
            + "VALUES (#{userId}, #{templateId}, 0)")
    int insertIfAbsent(@Param("userId") Long userId, @Param("templateId") Long templateId);

    /** 原子限领递增：0 行表示已达 per_user_limit（权威判定，不依赖锁） */
    @Update("UPDATE coupon_claim_counter SET claimed_count = claimed_count + 1 "
            + "WHERE user_id = #{userId} AND template_id = #{templateId} AND claimed_count < #{limit}")
    int incrementWithinLimit(@Param("userId") Long userId,
                             @Param("templateId") Long templateId,
                             @Param("limit") int limit);

    /** 补偿回退（领券后续步骤失败时归还名额） */
    @Update("UPDATE coupon_claim_counter SET claimed_count = claimed_count - 1 "
            + "WHERE user_id = #{userId} AND template_id = #{templateId} AND claimed_count > 0")
    int decrement(@Param("userId") Long userId, @Param("templateId") Long templateId);
}
