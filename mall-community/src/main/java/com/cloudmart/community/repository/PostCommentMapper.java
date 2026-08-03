package com.cloudmart.community.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.community.entity.PostComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PostCommentMapper extends BaseMapper<PostComment> {

    /**
     * 原子更新评论点赞数（用于 MQ 异步消费）。
     * 使用 GREATEST(0, like_count + delta) 防止负数。
     */
    @Update("UPDATE post_comments SET like_count = GREATEST(0, like_count + #{delta}), updated_at = NOW() WHERE id = #{commentId}")
    int updateLikeCount(@Param("commentId") Long commentId, @Param("delta") int delta);
}
