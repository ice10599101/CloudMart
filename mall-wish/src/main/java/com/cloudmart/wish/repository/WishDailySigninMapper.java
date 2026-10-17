package com.cloudmart.wish.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.wish.entity.WishDailySignin;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

@Mapper
public interface WishDailySigninMapper extends BaseMapper<WishDailySignin> {

    /**
     * 统计截至 anchor（含）结束的连续签到天数（gaps-and-islands）。
     *
     * <p>按 signin_date 倒序编号 rn，连续段内的行满足
     * {@code signin_date + rn 天 = anchor + 1 天}，计数即以 anchor 为终点的连续天数。
     * 空记录返回 0。</p>
     *
     * @param userId 用户 ID
     * @param anchor 连续段终点日期（含）
     * @return 连续签到天数
     */
    @Select("""
            SELECT COUNT(*) FROM (
                SELECT signin_date, ROW_NUMBER() OVER (ORDER BY signin_date DESC) AS rn
                FROM wish_daily_signin
                WHERE user_id = #{userId} AND signin_date <= #{anchor}
            ) t
            WHERE DATE_ADD(t.signin_date, INTERVAL t.rn DAY) = DATE_ADD(#{anchor}, INTERVAL 1 DAY)
            """)
    int countConsecutiveDays(@Param("userId") Long userId, @Param("anchor") LocalDate anchor);

    /** WISH-04：受邀人不同业务日正常签到天数（邀请资格事实，跨日由 uk 保证） */
    @org.apache.ibatis.annotations.Select(
            "SELECT COUNT(DISTINCT signin_date) FROM wish_daily_signin WHERE user_id = #{userId}")
    int countDistinctSigninDays(@org.apache.ibatis.annotations.Param("userId") Long userId);
}