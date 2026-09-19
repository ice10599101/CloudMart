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
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.notification.converter.NotificationConverter;
import com.cloudmart.notification.dto.NotificationDTO;
import com.cloudmart.notification.dto.SendNotificationRequest;
import com.cloudmart.notification.dto.UnreadCountDTO;
import com.cloudmart.notification.entity.Notification;
import com.cloudmart.notification.feign.UserFeignClient;
import com.cloudmart.notification.repository.NotificationMapper;
import com.cloudmart.notification.service.NotificationService;
import com.cloudmart.notification.websocket.WebSocketSessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class NotificationServiceImpl implements NotificationService {

    /** 广播枚举用户的分页大小：权衡 Feign 调用次数与单页负载 */
    private static final int BROADCAST_USER_PAGE_SIZE = 500;
    /** 广播最大枚举页数（500/页 × 200 页 = 10 万用户）：超出视为异常终止，防止分页契约异常时死循环 */
    private static final int BROADCAST_MAX_PAGES = 200;

    private final NotificationMapper notificationMapper;
    private final NotificationConverter notificationConverter;
    private final WebSocketSessionManager sessionManager;
    private final UserFeignClient userFeignClient;

    public NotificationServiceImpl(NotificationMapper notificationMapper,
                                   NotificationConverter notificationConverter,
                                   WebSocketSessionManager sessionManager,
                                   UserFeignClient userFeignClient) {
        this.notificationMapper = notificationMapper;
        this.notificationConverter = notificationConverter;
        this.sessionManager = sessionManager;
        this.userFeignClient = userFeignClient;
    }

    @Override
    @Transactional
    public NotificationDTO sendNotification(SendNotificationRequest request) {
        Notification entity = buildNotification(request.userId(), request.type(), request.title(),
                request.content(), request.bizId(), request.bizType());
        entity.setActorId(request.actorId());
        notificationMapper.insert(entity);

        NotificationDTO dto = notificationConverter.toDTO(entity);
        sessionManager.sendMessageToUser(request.userId(), dto);

        return dto;
    }

    @Override
    @Transactional
    public void sendNotificationToUser(Long userId, String type, String title, String content, Long bizId, String bizType) {
        sendNotificationToUser(userId, type, title, content, bizId, bizType, null);
    }

    @Override
    @Transactional
    public void sendNotificationToUser(Long userId, String type, String title, String content,
                                       Long bizId, String bizType, Long actorId) {
        SendNotificationRequest request = new SendNotificationRequest(userId, type, title, content, bizId, bizType, actorId);
        sendNotification(request);
    }

    /** 统一组装通知实体：单发与广播共用，保证落库字段语义一致（isRead 初始为未读） */
    private Notification buildNotification(Long userId, String type, String title, String content, Long bizId, String bizType) {
        Notification entity = new Notification();
        entity.setUserId(userId);
        entity.setType(type);
        entity.setTitle(title);
        entity.setContent(content);
        entity.setBizId(bizId);
        entity.setBizType(bizType);
        entity.setIsRead(0);
        return entity;
    }

    @Override
    public void deleteNotification(Long notificationId) {
        if (notificationMapper.selectById(notificationId) == null) {
            throw new BusinessException("NOTIFICATION_NOT_FOUND", "通知不存在");
        }
        notificationMapper.deleteById(notificationId);
    }

    /**
     * 全站广播：枚举全量会员后逐用户落库 + WS 实时推送。
     *
     * <p>设计约束：
     * <ul>
     *   <li>用户枚举（外部 Feign 依赖）先于任何写入执行——mall-user 不可用时在产生任何
     *       通知前 fail-fast，避免"部分用户已收到，重试又全量重发"的不一致；</li>
     *   <li>不做单一大事务包裹：全站量级下巨型事务会长时间锁表，逐条落库失败仅影响
     *       单个用户，且每条写入与既有单发路径（sendNotification）语义一致；</li>
     *   <li>不经过 this.sendNotificationToUser（自调用绕过事务代理，语义含糊），
     *       直接复用其落库+推送的原始步骤。</li>
     * </ul>
     */
    @Override
    public void broadcastNotification(String type, String title, String content) {
        List<Long> userIds = listAllUserIds();
        for (Long userId : userIds) {
            Notification entity = buildNotification(userId, type, title, content, null, null);
            notificationMapper.insert(entity);
            sessionManager.sendMessageToUser(userId, notificationConverter.toDTO(entity));
        }
        log.info("Broadcast completed: type={}, title={}, recipients={}", type, title, userIds.size());
    }

    /** 分页枚举全量会员 ID；页数超过上限视为 mall-user 异常（防御分页契约被破坏导致死循环） */
    private List<Long> listAllUserIds() {
        List<Long> userIds = new ArrayList<>();
        int page = 1;
        while (true) {
            ApiResponse<List<Map<String, Object>>> response = userFeignClient.listUsers(page, BROADCAST_USER_PAGE_SIZE);
            if (response == null || !response.success() || response.data() == null) {
                throw new BusinessException("USER_SERVICE_ERROR", "获取用户列表失败，广播已取消");
            }
            List<Map<String, Object>> records = response.data();
            for (Map<String, Object> record : records) {
                Object id = record.get("id");
                if (id instanceof Number number) {
                    userIds.add(number.longValue());
                }
            }
            if (records.size() < BROADCAST_USER_PAGE_SIZE) {
                return userIds;
            }
            page++;
            if (page > BROADCAST_MAX_PAGES) {
                throw new BusinessException("BROADCAST_SCALE_EXCEEDED", "用户规模超出广播上限，广播已取消");
            }
        }
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
    public long countUnreadByType(Long userId, String type) {
        Long count = notificationMapper.selectCount(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getIsRead, 0)
                        .eq(type != null && !type.isBlank(), Notification::getType, type)
        );
        return count != null ? count : 0L;
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
