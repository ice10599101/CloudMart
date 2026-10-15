package com.cloudmart.software.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.software.entity.Software;

/** 软件主体 Mapper */
public interface SoftwareMapper extends BaseMapper<Software> {

    /** 日获取去重事实首次落库成功后的累计计数原子递增（6.9；仅同事务调用） */
    @org.apache.ibatis.annotations.Update(
            "UPDATE software SET link_access_count = link_access_count + 1 WHERE id = #{softwareId}")
    int incrementLinkAccessCount(@org.apache.ibatis.annotations.Param("softwareId") Long softwareId);

    /** 公开评论计数原子增减（VISIBLE 增 / 删除或隐藏减；6.8 唯一口径） */
    @org.apache.ibatis.annotations.Update(
            "UPDATE software SET comment_count = GREATEST(CAST(comment_count AS SIGNED) + #{delta}, 0) "
            + "WHERE id = #{softwareId}")
    int incrementCommentCount(@org.apache.ibatis.annotations.Param("softwareId") Long softwareId,
                              @org.apache.ibatis.annotations.Param("delta") int delta);

    /** 收藏计数原子增减且不为负（唯一关系计数，可重建） */
    @org.apache.ibatis.annotations.Update(
            "UPDATE software SET favorite_count = GREATEST(CAST(favorite_count AS SIGNED) + #{delta}, 0) "
            + "WHERE id = #{softwareId}")
    int incrementFavoriteCount(@org.apache.ibatis.annotations.Param("softwareId") Long softwareId,
                               @org.apache.ibatis.annotations.Param("delta") int delta);

    /** 评分人数/星数和原子增减且不为负（P1：与评分行同事务，唯一口径） */
    @org.apache.ibatis.annotations.Update(
            "UPDATE software SET rating_count = GREATEST(CAST(rating_count AS SIGNED) + #{countDelta}, 0), "
            + "rating_sum = GREATEST(CAST(rating_sum AS SIGNED) + #{sumDelta}, 0) "
            + "WHERE id = #{softwareId}")
    int incrementRating(@org.apache.ibatis.annotations.Param("softwareId") Long softwareId,
                        @org.apache.ibatis.annotations.Param("countDelta") int countDelta,
                        @org.apache.ibatis.annotations.Param("sumDelta") int sumDelta);
}
