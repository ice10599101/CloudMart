package com.cloudmart.software.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 榜单聚合 Mapper（方案 4.5，P1）：按事实表聚合最近窗口并 LIMIT 候选，
 * 结果由服务层缓存（不得每请求扫描全量日事实/评分表）；最终展示仍按
 * 实时公开可见谓词过滤（A26）。
 *
 * <p>只返回有序软件 ID：指标仅参与排序，不参与构造器映射（避免多列/可选列
 * 映射歧义——曾因 record 构造器缺列导致 500）。</p>
 */
@Mapper
public interface SoftwareRankingMapper {

    /** 获取链接榜：最近 7 天去重人数（发布者本人/管理员预览落库时已排除） */
    @Select("""
            SELECT software_id FROM software_link_access_daily
            WHERE access_date >= #{fromDate}
            GROUP BY software_id
            ORDER BY COUNT(DISTINCT user_id) DESC, software_id DESC
            LIMIT #{limit}
            """)
    List<Long> topIdsByAccess7d(@Param("fromDate") LocalDate fromDate,
                                @Param("limit") int limit);

    /** 收藏榜：最近 7 天新增收藏数（趋势值，不等于收藏总量） */
    @Select("""
            SELECT software_id FROM software_favorite
            WHERE created_at >= #{from}
            GROUP BY software_id
            ORDER BY COUNT(*) DESC, software_id DESC
            LIMIT #{limit}
            """)
    List<Long> topIdsByFavorite7d(@Param("from") LocalDateTime from,
                                  @Param("limit") int limit);

    /**
     * 评分榜：至少 5 人评分，贝叶斯平滑
     * (v/(v+10))*R + (10/(v+10))*C（R=均分、v=票数、C=总体均值，冷启动 3.5）。
     */
    @Select("""
            SELECT s.id FROM software s
            WHERE s.deleted_at IS NULL AND s.lifecycle = 'ACTIVE'
              AND s.published_revision_id IS NOT NULL AND s.rating_count >= 5
            ORDER BY ((s.rating_count / (s.rating_count + 10.0))
                        * (s.rating_sum / s.rating_count)
                      + (10.0 / (s.rating_count + 10.0)) * #{overallMean}) DESC,
                     s.rating_count DESC, s.id DESC
            LIMIT #{limit}
            """)
    List<Long> topIdsByRating(@Param("overallMean") double overallMean,
                              @Param("limit") int limit);

    /** 站内评分总体均值（无有效评分时回落冷启动 3.5） */
    @Select("SELECT COALESCE(AVG(score), 3.5) FROM software_rating")
    Double overallRatingMean();
}
