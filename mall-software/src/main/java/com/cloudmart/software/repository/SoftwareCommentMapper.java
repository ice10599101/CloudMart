package com.cloudmart.software.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.software.entity.SoftwareComment;

/** 软件评论 Mapper */
public interface SoftwareCommentMapper extends BaseMapper<SoftwareComment> {

    /** 点赞数原子递增（唯一键插入成功后同事务调用） */
    @org.apache.ibatis.annotations.Update(
            "UPDATE software_comment SET like_count = like_count + 1 WHERE id = #{commentId}")
    int incrementLikeCount(@org.apache.ibatis.annotations.Param("commentId") Long commentId);

    /** 点赞数原子递减且不为负（6.8 计数不为负） */
    @org.apache.ibatis.annotations.Update(
            "UPDATE software_comment SET like_count = GREATEST(like_count - 1, 0) WHERE id = #{commentId}")
    int decrementLikeCount(@org.apache.ibatis.annotations.Param("commentId") Long commentId);

    /** SW09：带原状态条件的原子迁移（唯一写入路径）；返回 1=本次真正迁移 */
    @org.apache.ibatis.annotations.Update(
            "UPDATE software_comment SET status = #{to}, lock_version = lock_version + 1, "
            + "updated_at = NOW(3) WHERE id = #{commentId} AND status = #{from}")
    int transitionIfFrom(@org.apache.ibatis.annotations.Param("commentId") Long commentId,
                         @org.apache.ibatis.annotations.Param("from") String from,
                         @org.apache.ibatis.annotations.Param("to") String to);
}
