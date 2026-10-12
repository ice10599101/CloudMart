package com.cloudmart.community.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.community.entity.ExpLog;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ExpLogMapper extends BaseMapper<ExpLog> {

    /**
     * R22：统计某 UTC 时刻（用户当前业务日 0 点 UTC）之后用户实际入账经验总和。
     * 跨时区切换后即使配额行落在不同 business_date，也以此事实总和兜底封顶，防跨区超额。
     */
    @Select("SELECT COALESCE(SUM(exp_change), 0) FROM exp_logs "
            + "WHERE user_id = #{userId} AND created_at >= #{dayStartUtc}")
    int sumCreditedSinceUtc(@Param("userId") Long userId,
                            @Param("dayStartUtc") LocalDateTime dayStartUtc);
}
