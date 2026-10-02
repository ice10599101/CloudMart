package com.cloudmart.community.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.community.entity.PostComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PostCommentMapper extends BaseMapper<PostComment> {

    /**
     * C04：一次查询统计各评论线程的可见回复数（替代逐线程 COUNT 的 N+1）。
     *
     * @return [{PARENT_ID: id, CNT: count}, ...]
     */
    @org.apache.ibatis.annotations.Select("<script>"
            + "SELECT parent_id AS parentId, COUNT(*) AS cnt FROM post_comment "
            + "WHERE parent_id IN "
            + "<foreach collection='parentIds' item='pid' open='(' separator=',' close=')'>#{pid}</foreach> "
            + "AND status = 0 AND review_status = 1 "
            + "GROUP BY parent_id"
            + "</script>")
    java.util.List<java.util.Map<String, Object>> countRepliesByParentIds(
            @org.apache.ibatis.annotations.Param("parentIds") java.util.Collection<Long> parentIds);


    /**
     * 原子更新评论点赞数（用于 MQ 异步消费）。
     * 使用 GREATEST(0, like_count + delta) 防止负数。
     */
    @Update("UPDATE post_comments SET like_count = GREATEST(0, like_count + #{delta}), updated_at = NOW() WHERE id = #{commentId}")
    int updateLikeCount(@Param("commentId") Long commentId, @Param("delta") int delta);
}
