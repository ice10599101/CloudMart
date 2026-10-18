package com.cloudmart.software.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.software.entity.SoftwareSubscriptionDelivery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订阅投递 Mapper（方案 9.4，P1）：状态条件更新（CAS）避免并发下重复标终态；
 * 重复投递由通知服务 eventId+recipientId 去重键兜底，不依赖行级租约。
 */
@Mapper
public interface SoftwareSubscriptionDeliveryMapper extends BaseMapper<SoftwareSubscriptionDelivery> {

    /** 到期待投递（含上次失败重试），按 id 升序有界批量 */
    @Select("""
            SELECT * FROM software_subscription_delivery
            WHERE status = 'PENDING'
              AND (next_retry_at IS NULL OR next_retry_at <= #{now})
            ORDER BY id
            LIMIT #{limit}
            """)
    List<SoftwareSubscriptionDelivery> findDue(@Param("now") LocalDateTime now,
                                               @Param("limit") int limit);

    /** 标记已投递（仅 PENDING） */
    @Update("""
            UPDATE software_subscription_delivery
            SET status = 'SENT', lease_owner = NULL, lease_until = NULL, updated_at = #{now}
            WHERE id = #{id} AND status = 'PENDING'
            """)
    int markSent(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** 退避重试或转 FAILED（仅 PENDING；人工重试保留同一通知去重键） */
    @Update("""
            UPDATE software_subscription_delivery
            SET status = #{status}, attempts = #{attempts}, next_retry_at = #{nextRetryAt},
                updated_at = #{now}
            WHERE id = #{id} AND status = 'PENDING'
            """)
    int markRetry(@Param("id") Long id, @Param("status") String status,
                  @Param("attempts") int attempts,
                  @Param("nextRetryAt") LocalDateTime nextRetryAt,
                  @Param("now") LocalDateTime now);

    /** 软件下架：取消本次未发投递（不投递已下架内容） */
    @Update("""
            UPDATE software_subscription_delivery
            SET status = 'CANCELLED', updated_at = #{now}
            WHERE software_id = #{softwareId} AND publication_seq = #{publicationSeq}
              AND status = 'PENDING'
            """)
    int cancelPending(@Param("softwareId") Long softwareId,
                      @Param("publicationSeq") Integer publicationSeq,
                      @Param("now") LocalDateTime now);

    /** SW10：旧发布已被新发布取代——未发投递显式 SUPERSEDED，由最新事件接续（已发送事实保留） */
    @Update("""
            UPDATE software_subscription_delivery
            SET status = 'SUPERSEDED', updated_at = #{now}
            WHERE software_id = #{softwareId} AND publication_seq = #{publicationSeq}
              AND status = 'PENDING'
            """)
    int markSuperseded(@Param("softwareId") Long softwareId,
                       @Param("publicationSeq") Integer publicationSeq,
                       @Param("now") LocalDateTime now);

    /** SW10：重试时核对提醒偏好——已退订/关闭提醒的未发行取消（不新增事后订阅者） */
    @Update("""
            <script>UPDATE software_subscription_delivery
            SET status = 'CANCELLED', updated_at = #{now}
            WHERE software_id = #{softwareId} AND publication_seq = #{publicationSeq}
              AND status = 'PENDING'
              AND user_id IN
              <foreach collection="userIds" item="uid" open="(" separator="," close=")">#{uid}</foreach>
            </script>""")
    int cancelMuted(@Param("softwareId") Long softwareId,
                    @Param("publicationSeq") Integer publicationSeq,
                    @Param("userIds") java.util.Collection<Long> userIds,
                    @Param("now") LocalDateTime now);

    /** SW10：按任务聚合投递结果（运维页签） */
    @Select("""
            SELECT status, COUNT(*) AS cnt FROM software_subscription_delivery
            WHERE software_id = #{softwareId} AND publication_seq = #{publicationSeq}
            GROUP BY status
            """)
    java.util.List<java.util.Map<String, Object>> countByStatusGroup(
            @Param("softwareId") Long softwareId,
            @Param("publicationSeq") Integer publicationSeq);
}
