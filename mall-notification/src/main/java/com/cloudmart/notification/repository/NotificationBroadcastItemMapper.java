package com.cloudmart.notification.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.notification.entity.NotificationBroadcastItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface NotificationBroadcastItemMapper extends BaseMapper<NotificationBroadcastItem> {

    /** 重试 CAS：仅 FAILED 且未投递成功的项允许重新投递（并发重试只一个成功） */
    @Update("UPDATE notification_broadcast_item SET status = 'DELIVERING', error = NULL "
            + "WHERE job_id = #{jobId} AND user_id = #{userId} "
            + "AND status = 'FAILED' AND notification_id IS NULL")
    int markDelivering(@Param("jobId") Long jobId, @Param("userId") Long userId);

    /** 投递成功：DELIVERING → DELIVERED 并记录通知 ID（幂等依据） */
    @Update("UPDATE notification_broadcast_item SET status = 'DELIVERED', notification_id = #{notificationId}, "
            + "error = NULL WHERE id = #{id} AND status = 'DELIVERING'")
    int markDelivered(@Param("id") Long id, @Param("notificationId") Long notificationId);

    /** 重试失败：DELIVERING → FAILED（保留原因，供再次重试） */
    @Update("UPDATE notification_broadcast_item SET status = 'FAILED', error = #{error} "
            + "WHERE id = #{id} AND status = 'DELIVERING'")
    int markFailedAgain(@Param("id") Long id, @Param("error") String error);
}
