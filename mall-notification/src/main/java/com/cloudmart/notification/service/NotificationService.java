package com.cloudmart.notification.service;

import com.cloudmart.notification.dto.NotificationDTO;
import com.cloudmart.notification.dto.SendNotificationRequest;
import com.cloudmart.notification.dto.UnreadCountDTO;

import java.util.List;

public interface NotificationService {

    NotificationDTO sendNotification(SendNotificationRequest request);

    void sendNotificationToUser(Long userId, String type, String title, String content, Long bizId, String bizType);

    void broadcastNotification(String type, String title, String content);

    List<NotificationDTO> listNotifications(Long userId, Integer page, Integer pageSize);

    List<NotificationDTO> listNotificationsByType(Long userId, String type, Integer page, Integer pageSize);

    List<NotificationDTO> listAllNotifications(Long userId, String type, Integer page, Integer pageSize);

    UnreadCountDTO getUnreadCount(Long userId);

    void markAsRead(Long userId, Long notificationId);

    void markAllAsRead(Long userId);
}
