package com.cloudmart.software.repository;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 软件域注销清理 SQL（方案 11.3）：全部语句幂等（重复执行零增量效果），
 * 每阶段一个语句，由 SoftwareErasureService 在独立事务中调度并推进游标。
 * 账号身份列（NOT NULL）保留，读侧经用户资料适配器对已注销账号降级展示。
 */
@Mapper
public interface SoftwareErasureSqlMapper {

    /** 下架本人仍在架的软件（ACTIVE→UNLISTED；BANNED/DELETED 不变） */
    @Update("""
            UPDATE software
               SET lifecycle = 'UNLISTED', lock_version = lock_version + 1
             WHERE owner_id = #{userId} AND lifecycle = 'ACTIVE' AND deleted_at IS NULL
            """)
    int unlistActiveSoftware(@Param("userId") Long userId);

    /** 本人评论软删除（保留占位语义；读侧对 DELETED 不回传原 content） */
    @Update("""
            UPDATE software_comment
               SET status = 'DELETED', deleted_at = NOW(3), lock_version = lock_version + 1
             WHERE user_id = #{userId}
               AND status IN ('PENDING', 'VISIBLE')
               AND deleted_at IS NULL
            """)
    int softDeleteOwnComments(@Param("userId") Long userId);

    /** 清理本人点赞关系（物理删除，幂等） */
    @Delete("DELETE FROM software_comment_like WHERE user_id = #{userId}")
    int deleteOwnCommentLikes(@Param("userId") Long userId);

    /** 清理本人收藏关系（物理删除，幂等） */
    @Delete("DELETE FROM software_favorite WHERE user_id = #{userId}")
    int deleteOwnFavorites(@Param("userId") Long userId);

    /** 清理本人日获取事实（去重事实随账号消失，累计计数不重建） */
    @Delete("DELETE FROM software_link_access_daily WHERE user_id = #{userId}")
    int deleteOwnAccessDaily(@Param("userId") Long userId);

    /** 撤销本人有效付款查看授权（REVOKED；审计历史保留） */
    @Update("""
            UPDATE software_access_grant
               SET status = 'REVOKED', revoked_at = NOW(3), lock_version = lock_version + 1
             WHERE user_id = #{userId} AND status = 'ACTIVE'
            """)
    int revokeOwnAccessGrants(@Param("userId") Long userId);

    /** 关闭本人发布资格（记录保留作最小审计；reason=ACCOUNT_ERASED，updated_by=0 表系统操作） */
    @Update("""
            UPDATE software_publisher_permission
               SET can_publish = 0, reason = 'ACCOUNT_ERASED', updated_by = 0,
                   lock_version = lock_version + 1
             WHERE user_id = #{userId} AND can_publish = 1
            """)
    int disableOwnPublishPermission(@Param("userId") Long userId);

    /** 清理本人失效反馈身份（举报人身份清理：整行删除，计数事实不受影响） */
    @Delete("DELETE FROM software_link_feedback WHERE reporter_id = #{userId}")
    int deleteOwnLinkFeedback(@Param("userId") Long userId);

    /** 清理本人发起的软件举报（举报身份清理；被举报目标处置历史不受影响） */
    @Delete("DELETE FROM software_report WHERE reporter_id = #{userId}")
    int deleteOwnReports(@Param("userId") Long userId);
}
