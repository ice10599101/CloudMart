package com.cloudmart.notification.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.notification.converter.NotificationConverter;
import com.cloudmart.notification.dto.NotificationDTO;
import com.cloudmart.notification.dto.SendNotificationRequest;
import com.cloudmart.notification.dto.UnreadCountDTO;
import com.cloudmart.notification.entity.Notification;
import com.cloudmart.notification.repository.NotificationMapper;
import com.cloudmart.notification.service.NotificationService;
import com.cloudmart.notification.websocket.WebSocketSessionManager;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;
    private final NotificationConverter notificationConverter;
    private final WebSocketSessionManager sessionManager;

    public NotificationServiceImpl(NotificationMapper notificationMapper,
                                   NotificationConverter notificationConverter,
                                   WebSocketSessionManager sessionManager) {
        this.notificationMapper = notificationMapper;
        this.notificationConverter = notificationConverter;
        this.sessionManager = sessionManager;
    }

    @Override
    @Transactional
    public NotificationDTO sendNotification(SendNotificationRequest request) {
        Notification entity = new Notification();
        entity.setUserId(request.userId());
        entity.setType(request.type());
        entity.setTitle(request.title());
        entity.setContent(request.content());
        entity.setBizId(request.bizId());
        entity.setBizType(request.bizType());
        entity.setIsRead(0);
        notificationMapper.insert(entity);

        NotificationDTO dto = notificationConverter.toDTO(entity);
        sessionManager.sendMessageToUser(request.userId(), dto);

        return dto;
    }

    @Override
    @Transactional
    public void sendNotificationToUser(Long userId, String type, String title, String content, Long bizId, String bizType) {
        SendNotificationRequest request = new SendNotificationRequest(userId, type, title, content, bizId, bizType);
        sendNotification(request);
    }

    @Override
    @Transactional
    public void broadcastNotification(String type, String title, String content) {
        log.info("Broadcasting notification: type={}, title={}", type, title);
    }

    @Override
    @SentinelResource(value = "getNotifications", fallback = "getNotificationsFallback")
    public List<NotificationDTO> listNotifications(Long userId, Integer page, Integer pageSize) {
        Page<Notification> pageParam = new Page<>(page, pageSize);
        Page<Notification> result = notificationMapper.selectPage(pageParam,
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .orderByDesc(Notification::getCreatedAt)
        );
        return notificationConverter.toDTOList(result.getRecords());
    }

    @Override
    public List<NotificationDTO> listNotificationsByType(Long userId, String type, Integer page, Integer pageSize) {
        Page<Notification> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId);

        if ("interaction".equalsIgnoreCase(type)) {
            wrapper.in(Notification::getType, List.of("LIKE", "COMMENT", "COLLECT", "SHARE", "MENTION", "TAG_NEW_POST"));
        } else if ("follow".equalsIgnoreCase(type)) {
            wrapper.eq(Notification::getType, "FOLLOW");
        } else if ("system".equalsIgnoreCase(type)) {
            wrapper.in(Notification::getType, List.of("SYSTEM", "BADGE", "ACCOUNT", "LEVEL_UP", "CHECK_IN"));
        } else {
            wrapper.eq(Notification::getType, type.toUpperCase());
        }

        wrapper.orderByDesc(Notification::getCreatedAt);
        Page<Notification> result = notificationMapper.selectPage(pageParam, wrapper);
        return notificationConverter.toDTOList(result.getRecords());
    }

    @Override
    public List<NotificationDTO> listAllNotifications(Long userId, String type, Integer page, Integer pageSize) {
        Page<Notification> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
                .orderByDesc(Notification::getCreatedAt);
        if (userId != null) {
            wrapper.eq(Notification::getUserId, userId);
        }
        if (type != null && !type.isBlank()) {
            wrapper.eq(Notification::getType, type);
        }
        Page<Notification> result = notificationMapper.selectPage(pageParam, wrapper);
        return notificationConverter.toDTOList(result.getRecords());
    }

    @Override
    public UnreadCountDTO getUnreadCount(Long userId) {
        Long count = notificationMapper.selectCount(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getIsRead, 0)
        );
        return new UnreadCountDTO(count);
    }

    @Override
    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        Notification entity = notificationMapper.selectById(notificationId);
        if (entity == null) {
            throw new BusinessException("NOTIFICATION_NOT_FOUND", "通知不存在");
        }
        if (!entity.getUserId().equals(userId)) {
            throw new BusinessException("NOTIFICATION_ACCESS_DENIED", "无权操作此通知");
        }
        entity.setIsRead(1);
        notificationMapper.updateById(entity);
    }

    @Override
    @Transactional
    public void markAllAsRead(Long userId) {
        notificationMapper.update(
                new LambdaUpdateWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getIsRead, 0)
                        .set(Notification::getIsRead, 1)
        );
    }

    public List<NotificationDTO> getNotificationsFallback(Long userId, Integer page, Integer pageSize, Throwable throwable) {
        log.warn("getNotifications fallback triggered, userId={}: {}", userId, throwable.getMessage());
        return List.of();
    }
}
