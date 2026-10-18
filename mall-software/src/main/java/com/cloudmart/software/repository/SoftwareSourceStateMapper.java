package com.cloudmart.software.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.software.entity.SoftwareSourceState;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/** 来源核实状态 Mapper */
public interface SoftwareSourceStateMapper extends BaseMapper<SoftwareSourceState> {

    /**
     * SW07：自动阈值抬升必须带状态条件的原子更新——只有 UNKNOWN/AVAILABLE 允许迁移为
     * SUSPECTED；人工 INVALID/DISABLED 结论优先，绝不被反馈降级。返回影响行数：
     * 1=本次真正迁移（应通知作者一次），0=已是 SUSPECTED 或人工处置（仅累计统计）。
     */
    @Update("""
            UPDATE software_source_state
               SET availability = 'SUSPECTED',
                   checked_fingerprint = #{fingerprint},
                   checked_at = #{now},
                   reason = #{reason},
                   lock_version = lock_version + 1,
                   updated_at = #{now}
             WHERE software_id = #{softwareId}
               AND source_key = #{sourceKey}
               AND availability IN ('UNKNOWN', 'AVAILABLE')
            """)
    int raiseSuspectedIfEligible(@Param("softwareId") Long softwareId,
                                 @Param("sourceKey") String sourceKey,
                                 @Param("fingerprint") String fingerprint,
                                 @Param("now") LocalDateTime now,
                                 @Param("reason") String reason);
}
