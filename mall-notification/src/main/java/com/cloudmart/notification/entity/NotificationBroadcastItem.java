package com.cloudmart.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知群发明细（ADMIN-03）：按 (job,user) 唯一；
 * notification_id 为投递成功事实，重试时据此幂等跳过。
 */
@Data
@TableName("notification_broadcast_item")
public class NotificationBroadcastItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long jobId;

    private Long userId;

    /** DELIVERED / FAILED / SKIPPED */
    private String status;

    /** 投递成功后的通知 ID（重试幂等依据） */
    private Long notificationId;

    /** 失败原因 */
    private String error;

    private LocalDateTime createdAt;
}
