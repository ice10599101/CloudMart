package com.cloudmart.community.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.community.entity.BrowseHistory;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface BrowseHistoryMapper extends BaseMapper<BrowseHistory> {

    /**
     * 幂等写入足迹：命中 uk(user_id, target_type, target_id) 时仅刷新标题/封面快照与浏览时间，
     * 避免并发重复上报产生重复记录。
     */
    @Insert("INSERT INTO browse_histories (user_id, target_type, target_id, title, cover, viewed_at) "
            + "VALUES (#{userId}, #{targetType}, #{targetId}, #{title}, #{cover}, NOW()) AS new "
            + "ON DUPLICATE KEY UPDATE title = new.title, cover = new.cover, viewed_at = new.viewed_at")
    int upsert(BrowseHistory history);

    @Select("SELECT * FROM browse_histories WHERE user_id = #{userId} "
            + "ORDER BY viewed_at DESC, id DESC LIMIT #{offset}, #{size}")
    List<BrowseHistory> selectPageByUser(@Param("userId") Long userId,
                                         @Param("offset") long offset,
                                         @Param("size") long size);

    @Select("SELECT COUNT(*) FROM browse_histories WHERE user_id = #{userId}")
    long countByUser(@Param("userId") Long userId);

    /**
     * 删除超出最近 limit 条之外的历史足迹（外层派生表包装以规避 MySQL 无法
     * 在 DELETE 中直接引用目标表子查询的限制）。
     */
    @Delete("DELETE FROM browse_histories WHERE user_id = #{userId} AND id NOT IN ("
            + "SELECT id FROM (SELECT id FROM browse_histories WHERE user_id = #{userId} "
            + "ORDER BY viewed_at DESC, id DESC LIMIT #{limit}) latest)")
    int pruneBeyondLimit(@Param("userId") Long userId, @Param("limit") int limit);
}