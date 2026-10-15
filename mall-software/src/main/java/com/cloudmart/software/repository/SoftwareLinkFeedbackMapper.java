package com.cloudmart.software.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.software.entity.SoftwareLinkFeedback;

/** 链接失效反馈 Mapper */
public interface SoftwareLinkFeedbackMapper extends BaseMapper<SoftwareLinkFeedback> {

    /** 24 小时内同指纹不同账号的失效反馈数（5.3：3 人标 SUSPECTED 阈值） */
    @org.apache.ibatis.annotations.Select(
            "SELECT COUNT(DISTINCT reporter_id) FROM software_link_feedback "
            + "WHERE software_id = #{softwareId} AND source_key = #{sourceKey} "
            + "AND source_fingerprint = #{fingerprint} "
            + "AND status IN ('OPEN', 'VERIFYING') AND created_at >= #{since}")
    long countRecentDistinctReporters(@org.apache.ibatis.annotations.Param("softwareId") Long softwareId,
                                      @org.apache.ibatis.annotations.Param("sourceKey") String sourceKey,
                                      @org.apache.ibatis.annotations.Param("fingerprint") String fingerprint,
                                      @org.apache.ibatis.annotations.Param("since") java.time.LocalDateTime since);
}
