package com.cloudmart.notification.service;

import com.cloudmart.notification.dto.NotificationDTO;
import com.cloudmart.notification.dto.SendNotificationRequest;
import com.cloudmart.notification.dto.UnreadCountDTO;

import java.util.List;

public interface NotificationService {

    NotificationDTO sendNotification(SendNotificationRequest request);

    void sendNotificationToUser(Long userId, String type, String title, String content, Long bizId, String bizType);

    /**
     * 带操作者的通知：actorId 会随通知下发，前端据此把「谁赞/收藏/关注了我」
     * 中的操作者昵称链接到其个人主页。actorId 可空（系统/广播类通知）。
     */
    void sendNotificationToUser(Long userId, String type, String title, String content,
                                Long bizId, String bizType, Long actorId);

    void broadcastNotification(String type, String title, String content);

    List<NotificationDTO> listNotifications(Long userId, Integer page, Integer pageSize);

    List<NotificationDTO> listNotificationsByType(Long userId, String type, Integer page, Integer pageSize);

    List<NotificationDTO> listAllNotifications(Long userId, String type, Integer page, Integer pageSize);

    UnreadCountDTO getUnreadCount(Long userId);

    void markAsRead(Long userId, Long notificationId);

    void markAllAsRead(Long userId);
}
