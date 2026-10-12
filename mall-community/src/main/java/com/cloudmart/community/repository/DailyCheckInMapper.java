package com.cloudmart.community.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.community.entity.DailyCheckIn;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DailyCheckInMapper extends BaseMapper<DailyCheckIn> {

    /**
     * R22 防跨时区双签：按 UTC 发生时刻统计"当前用户业务日 0 点 UTC 之后"的签到数。
     * 用户怎么改时区，只要在当前业务日界之后已有成功签到，就拒绝第二次（不依赖 check_in_date 归属值）。
     */
    @Select("SELECT COUNT(*) FROM daily_check_ins "
            + "WHERE user_id = #{userId} AND created_at >= #{dayStartUtc}")
    long countCheckedInSinceUtc(@Param("userId") Long userId,
                                @Param("dayStartUtc") LocalDateTime dayStartUtc);
}
