package com.cloudmart.software.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.software.entity.SoftwareSourceAppeal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * SF08 补批：来源申诉。处理结论只允许从 PENDING 写一次（CAS），
 * 重复处理由服务层按已落状态幂等返回。
 */
@Mapper
public interface SoftwareSourceAppealMapper extends BaseMapper<SoftwareSourceAppeal> {

    @Update("""
            UPDATE software_source_appeal
            SET status = #{status}, handler_id = #{handlerId}, handle_note = #{note},
                handled_at = #{now}, updated_at = #{now}
            WHERE id = #{id} AND status = 'PENDING'
            """)
    int handle(@Param("id") Long id, @Param("status") String status,
               @Param("handlerId") Long handlerId, @Param("note") String note,
               @Param("now") LocalDateTime now);
}
