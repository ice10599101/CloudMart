package com.cloudmart.community.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.community.entity.ExpDailyQuota;
import java.time.LocalDate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ExpDailyQuotaMapper extends BaseMapper<ExpDailyQuota> {

    /**
     * R21：按 (用户, 业务日) 取当日配额行并加行锁——必须在事务内调用。
     * 同用户当天的并发发奖在此串行，granted 读取为锁内权威值。
     */
    @Select("SELECT * FROM community_exp_daily_quota "
            + "WHERE user_id = #{userId} AND business_date = #{businessDate} FOR UPDATE")
    ExpDailyQuota selectForUpdate(@Param("userId") Long userId,
                                  @Param("businessDate") LocalDate businessDate);

    /** R21：锁内原子累加已占用额度（仅累加调用方算出的 credited，不会超过日上限） */
    @Update("UPDATE community_exp_daily_quota SET granted = granted + #{credited} "
            + "WHERE user_id = #{userId} AND business_date = #{businessDate}")
    int addGranted(@Param("userId") Long userId,
                   @Param("businessDate") LocalDate businessDate,
                   @Param("credited") int credited);
}
