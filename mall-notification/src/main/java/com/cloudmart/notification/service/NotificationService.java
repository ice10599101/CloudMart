package com.cloudmart.notification.service;

import com.cloudmart.notification.dto.NotificationDTO;
import com.cloudmart.notification.dto.SendNotificationRequest;
import com.cloudmart.notification.dto.UnreadCountDTO;

import java.util.List;

public interface NotificationService {

    NotificationDTO sendNotification(SendNotificationRequest request);

    void sendNotificationToUser(Long userId, String type, String title, String content, Long bizId, String bizType);

    /**
     * 携带业务事件唯一键发送宠物通知（B19）：eventId 唯一索引 + 幂等落库，
     * 重复事件（MQ at-least-once 重投/并发双消费）不再产生第二条站内信。
     */
    void sendPetEventNotification(Long userId, String eventId, String reminderType,
                                  String title, String content, Long bizId);

    /**
     * 带操作者的通知：actorId 会随通知下发，前端据此把「谁赞/收藏/关注了我」
     * 中的操作者昵称链接到其个人主页。actorId 可空（系统/广播类通知）。
     */
    void sendNotificationToUser(Long userId, String type, String title, String content,
                                Long bizId, String bizType, Long actorId);

    void broadcastNotification(String type, String title, String content);

    /** 管理端删除指定通知（撤回误发内容）；不存在时抛 NOTIFICATION_NOT_FOUND */
    void deleteNotification(Long notificationId);

    List<NotificationDTO> listNotifications(Long userId, Integer page, Integer pageSize);

    List<NotificationDTO> listNotificationsByType(Long userId, String type, Integer page, Integer pageSize);

    List<NotificationDTO> listAllNotifications(Long userId, String type, Integer page, Integer pageSize);

    UnreadCountDTO getUnreadCount(Long userId);

    /** 未读数量（可按类型过滤；type 为空等价于 getUnreadCount） */
    long countUnreadByType(Long userId, String type);

    void markAsRead(Long userId, Long notificationId);

    void markAllAsRead(Long userId);

    /**
     * 按类型全部已读（B19）：只影响指定类型（如 PET），保留全站全部已读接口原语义。
     * 归属校验由 userId 条件天然保证。
     *
     * @return 更新后该类型剩余未读数
     */
    long markAllAsReadByType(Long userId, String type);
}
