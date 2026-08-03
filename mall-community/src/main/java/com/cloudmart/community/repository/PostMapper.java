package com.cloudmart.community.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.community.entity.Post;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PostMapper extends BaseMapper<Post> {

    /**
     * 原子更新帖子点赞数（防止负数）。
     *
     * @param postId 帖子ID
     * @param delta  增量（正数加，负数减）
     * @return 受影响行数
     */
    @Update("UPDATE posts SET like_count = GREATEST(0, like_count + #{delta}), updated_at = NOW() WHERE id = #{postId}")
    int updateLikeCount(@Param("postId") Long postId, @Param("delta") int delta);
}
