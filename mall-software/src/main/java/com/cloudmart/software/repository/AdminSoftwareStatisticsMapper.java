package com.cloudmart.software.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 运营统计 Mapper（方案 10.7，P1）：口径全部来自事实表——
 * 严禁出现"外部下载完成率"类口径（本站不掌握外部下载结果）。
 */
@Mapper
public interface AdminSoftwareStatisticsMapper {

    /** 当前在架软件数（已审快照 + ACTIVE） */
    @Select("""
            SELECT COUNT(*) FROM software
            WHERE deleted_at IS NULL AND lifecycle = 'ACTIVE' AND published_revision_id IS NOT NULL
            """)
    long publishedCount();

    /** 待审快照数 */
    @Select("SELECT COUNT(*) FROM software_revision WHERE review_status = 'PENDING'")
    long pendingReviewCount();

    /** 近窗口平均审核时长（分钟，submittedAt → reviewedAt） */
    @Select("""
            SELECT AVG(TIMESTAMPDIFF(MINUTE, submitted_at, reviewed_at))
            FROM software_revision
            WHERE reviewed_at IS NOT NULL AND reviewed_at >= #{from}
            """)
    Double avgReviewMinutes(@Param("from") LocalDateTime from);

    /** 近窗口获取链接次数（去重事实行数） */
    @Select("""
            SELECT COUNT(*) FROM software_link_access_daily WHERE access_date >= #{fromDate}
            """)
    long accessCount(@Param("fromDate") LocalDate fromDate);

    /** 近窗口获取链接去重人数 */
    @Select("""
            SELECT COUNT(DISTINCT user_id) FROM software_link_access_daily
            WHERE access_date >= #{fromDate}
            """)
    long accessUsers(@Param("fromDate") LocalDate fromDate);

    /** 收藏总量（当前关系数） */
    @Select("SELECT COUNT(*) FROM software_favorite")
    long favoriteTotal();

    /** 近窗口新增收藏（按 created_at，趋势值≠总量） */
    @Select("SELECT COUNT(*) FROM software_favorite WHERE created_at >= #{from}")
    long newFavorites(@Param("from") LocalDateTime from);

    /** 近窗口新评分人数 */
    @Select("SELECT COUNT(*) FROM software_rating WHERE created_at >= #{from}")
    long newRatings(@Param("from") LocalDateTime from);

    /** 近窗口新评分均分 */
    @Select("SELECT AVG(score) FROM software_rating WHERE created_at >= #{from}")
    Double newRatingAvg(@Param("from") LocalDateTime from);

    /** 待处理失效反馈（OPEN + VERIFYING） */
    @Select("SELECT COUNT(*) FROM software_link_feedback WHERE status IN ('OPEN','VERIFYING')")
    long pendingFeedbacks();

    /** 待处理举报（OPEN + VERIFYING） */
    @Select("SELECT COUNT(*) FROM software_report WHERE status IN ('OPEN','VERIFYING')")
    long pendingReports();
}
