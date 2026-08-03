package com.cloudmart.notification.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.notification.converter.NotificationConverter;
import com.cloudmart.notification.dto.NotificationDTO;
import com.cloudmart.notification.dto.SendNotificationRequest;
import com.cloudmart.notification.dto.UnreadCountDTO;
import com.cloudmart.notification.entity.Notification;
import com.cloudmart.notification.repository.NotificationMapper;
import com.cloudmart.notification.websocket.WebSocketSessionManager;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        if (TableInfoHelper.getTableInfo(Notification.class) == null) {
            MybatisConfiguration configuration = new MybatisConfiguration();
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
            assistant.setCurrentNamespace(NotificationMapper.class.getName());
            TableInfoHelper.initTableInfo(assistant, Notification.class);
        }
    }

    @Mock
    private NotificationMapper notificationMapper;

    @Mock
    private NotificationConverter notificationConverter;

    @Mock
    private WebSocketSessionManager sessionManager;

    private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationServiceImpl(notificationMapper, notificationConverter, sessionManager);
    }

    private static final Long USER_ID = 1L;
    private static final Long NOTIFICATION_ID = 100L;

    @Nested
    @DisplayName("sendNotification")
    class SendNotificationTests {

        @Test
        @DisplayName("should create notification and send via WebSocket")
        void sendNotification_success_returnsDTO() {
            SendNotificationRequest request = new SendNotificationRequest(
                    USER_ID, "ORDER", "订单通知", "您的订单已发货", 200L, "ORDER");
            NotificationDTO expectedDTO = new NotificationDTO(
                    NOTIFICATION_ID, USER_ID, "ORDER", "订单通知", "您的订单已发货",
                    false, 200L, "ORDER", null);

            when(notificationMapper.insert(any(Notification.class))).thenReturn(1);
            when(notificationConverter.toDTO(any(Notification.class))).thenReturn(expectedDTO);

            NotificationDTO result = notificationService.sendNotification(request);

            assertThat(result).isNotNull();
            assertThat(result.userId()).isEqualTo(USER_ID);
            assertThat(result.type()).isEqualTo("ORDER");
            verify(sessionManager).sendMessageToUser(USER_ID, expectedDTO);
        }
    }

    @Nested
    @DisplayName("listNotifications")
    class ListNotificationsTests {

        @Test
        @DisplayName("should return paginated notification list")
        void listNotifications_returnsDTOList() {
            Notification entity = new Notification();
            entity.setId(NOTIFICATION_ID);
            entity.setUserId(USER_ID);
            entity.setType("ORDER");
            entity.setTitle("订单通知");
            entity.setIsRead(0);

            Page<Notification> pageResult = new Page<>(1, 10, 1);
            pageResult.setRecords(List.of(entity));

            NotificationDTO dto = new NotificationDTO(
                    NOTIFICATION_ID, USER_ID, "ORDER", "订单通知", "内容",
                    false, null, null, null);

            when(notificationMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(pageResult);
            when(notificationConverter.toDTOList(List.of(entity))).thenReturn(List.of(dto));

            List<NotificationDTO> result = notificationService.listNotifications(USER_ID, 1, 10);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).id()).isEqualTo(NOTIFICATION_ID);
        }
    }

    @Nested
    @DisplayName("getUnreadCount")
    class GetUnreadCountTests {

        @Test
        @DisplayName("should return unread count for user")
        void getUnreadCount_returnsCount() {
            when(notificationMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(5L);

            UnreadCountDTO result = notificationService.getUnreadCount(USER_ID);

            assertThat(result.count()).isEqualTo(5L);
        }

        @Test
        @DisplayName("should return zero when no unread notifications")
        void getUnreadCount_noUnread_returnsZero() {
            when(notificationMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

            UnreadCountDTO result = notificationService.getUnreadCount(USER_ID);

            assertThat(result.count()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("markAsRead")
    class MarkAsReadTests {

        @Test
        @DisplayName("should mark notification as read")
        void markAsRead_existingNotification_marksAsRead() {
            Notification entity = new Notification();
            entity.setId(NOTIFICATION_ID);
            entity.setUserId(USER_ID);
            entity.setIsRead(0);

            when(notificationMapper.selectById(NOTIFICATION_ID)).thenReturn(entity);
            when(notificationMapper.updateById(entity)).thenReturn(1);

            notificationService.markAsRead(USER_ID, NOTIFICATION_ID);

            assertThat(entity.getIsRead()).isEqualTo(1);
            verify(notificationMapper).updateById(entity);
        }

        @Test
        @DisplayName("should throw when notification not found")
        void markAsRead_nonExistentNotification_throwsException() {
            when(notificationMapper.selectById(NOTIFICATION_ID)).thenReturn(null);

            assertThatThrownBy(() -> notificationService.markAsRead(USER_ID, NOTIFICATION_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo("NOTIFICATION_NOT_FOUND");
        }

        @Test
        @DisplayName("should throw when notification belongs to another user")
        void markAsRead_otherUsersNotification_throwsException() {
            Notification entity = new Notification();
            entity.setId(NOTIFICATION_ID);
            entity.setUserId(999L);
            entity.setIsRead(0);

            when(notificationMapper.selectById(NOTIFICATION_ID)).thenReturn(entity);

            assertThatThrownBy(() -> notificationService.markAsRead(USER_ID, NOTIFICATION_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo("NOTIFICATION_ACCESS_DENIED");
        }
    }

    @Nested
    @DisplayName("markAllAsRead")
    class MarkAllAsReadTests {

        @Test
        @DisplayName("should update all unread notifications for user")
        void markAllAsRead_updatesAllUnread() {
            notificationService.markAllAsRead(USER_ID);

            verify(notificationMapper).update(any(LambdaUpdateWrapper.class));
        }
    }
}
