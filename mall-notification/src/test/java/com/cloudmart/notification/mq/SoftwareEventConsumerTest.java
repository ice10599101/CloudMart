package com.cloudmart.notification.mq;

import com.cloudmart.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 9.4 软件域事件消费单测：事件→站内信映射、幂等字段透传、畸形事件防御。
 */
@ExtendWith(MockitoExtension.class)
class SoftwareEventConsumerTest {

    @Mock
    private NotificationService notificationService;

    private SoftwareEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new SoftwareEventConsumer(notificationService);
    }

    private Map<String, Object> message(String eventId, String eventType, Map<String, Object> payload) {
        Map<String, Object> message = new HashMap<>();
        message.put("eventId", eventId);
        message.put("eventType", eventType);
        message.put("payload", payload);
        return message;
    }

    @Test
    @DisplayName("审核通过事件 → 作者通知 SOFTWARE_REVIEW_APPROVED（bizId=softwareId）")
    void reviewApprovedMapsToAuthorNotification() {
        consumer.onMessage(message("evt-1", "SOFTWARE_REVIEW_APPROVED", Map.of(
                "softwareId", "100", "revisionId", "501", "authorId", 42L)));

        ArgumentCaptor<Long> userIdCaptor = ArgumentCaptor.forClass(Long.class);
        verify(notificationService).sendSoftwareEventNotification(userIdCaptor.capture(),
                org.mockito.ArgumentMatchers.eq("evt-1"),
                org.mockito.ArgumentMatchers.eq("SOFTWARE_REVIEW_APPROVED"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(100L),
                org.mockito.ArgumentMatchers.eq("SOFTWARE"),
                org.mockito.ArgumentMatchers.eq(null));
        assertThat(userIdCaptor.getValue()).isEqualTo(42L);
    }

    @Test
    void directPublishNotificationDoesNotClaimManualReview() {
        consumer.onMessage(message("evt-direct", "SOFTWARE_REVIEW_APPROVED", Map.of(
                "softwareId", "100", "revisionId", "501", "authorId", 42L, "directPublish", true)));
        verify(notificationService).sendSoftwareEventNotification(42L, "evt-direct",
                "SOFTWARE_REVIEW_APPROVED", "软件发布成功",
                "你的软件已直接发布，可在「我的软件」中查看与继续维护", 100L, "SOFTWARE", null);
    }

    @Test
    @DisplayName("评论可见事件 → recipientId 收通知；来源疑似失效 → 作者收提示")
    void commentAndSourceEventsMap() {
        consumer.onMessage(message("evt-2", "SOFTWARE_COMMENT_VISIBLE", Map.of(
                "softwareId", 100L, "commentId", 9001L,
                "recipientId", 7L, "authorId", 42L)));

        verify(notificationService).sendSoftwareEventNotification(
                org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq("evt-2"),
                org.mockito.ArgumentMatchers.eq("SOFTWARE_COMMENT_VISIBLE"),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(100L),
                org.mockito.ArgumentMatchers.eq("SOFTWARE_COMMENT"),
                org.mockito.ArgumentMatchers.eq(null));

        consumer.onMessage(message("evt-3", "SOFTWARE_SOURCE_REPORTED", Map.of(
                "softwareId", "100", "authorId", "7", "sourceKey", "key-1",
                "distinctReporters", "3")));
        verify(notificationService).sendSoftwareEventNotification(
                org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq("evt-3"),
                org.mockito.ArgumentMatchers.eq("SOFTWARE_SOURCE_REPORTED"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.contains("3 位"),
                org.mockito.ArgumentMatchers.eq(100L),
                org.mockito.ArgumentMatchers.eq("SOFTWARE_SOURCE"),
                org.mockito.ArgumentMatchers.eq(null));
    }

    @Test
    @DisplayName("SF03：发布资格申请结果 → 申请人收通知；拒绝带公开原因")
    void publisherApplicationResultMaps() {
        consumer.onMessage(message("evt-app-1", "SOFTWARE_PUBLISHER_APPLICATION", Map.of(
                "userId", "42", "status", "APPROVED", "reason", "资料完整")));
        verify(notificationService).sendSoftwareEventNotification(42L, "evt-app-1",
                "SOFTWARE_PUBLISHER_APPLICATION", "发布资格申请已通过",
                "你的软件发布资格申请已通过，现在可以创建软件并提交审核",
                null, "SOFTWARE_PUBLISHER", null);

        consumer.onMessage(message("evt-app-2", "SOFTWARE_PUBLISHER_APPLICATION", Map.of(
                "userId", 42L, "status", "REJECTED", "reason", "来源说明不足")));
        verify(notificationService).sendSoftwareEventNotification(42L, "evt-app-2",
                "SOFTWARE_PUBLISHER_APPLICATION", "发布资格申请未通过",
                "你的发布资格申请未通过，理由：来源说明不足；修改后可重新申请",
                null, "SOFTWARE_PUBLISHER", null);
    }

    @Test
    @DisplayName("畸形事件防御：缺 eventId/payload/recipient 拒绝且不抛出；未知类型跳过")
    void malformedEventsIgnored() {
        Map<String, Object> noEventId = new HashMap<>();
        noEventId.put("eventType", "SOFTWARE_REVIEW_APPROVED");
        noEventId.put("payload", Map.of("authorId", 42L));
        assertThatCode(() -> consumer.onMessage(noEventId)).doesNotThrowAnyException();

        assertThatCode(() -> consumer.onMessage(message("evt-4", "SOFTWARE_REVIEW_APPROVED", null)))
                .doesNotThrowAnyException();
        assertThatCode(() -> consumer.onMessage(message("evt-5", "SOFTWARE_REVIEW_APPROVED",
                Map.of("softwareId", "100"))))
                .doesNotThrowAnyException();
        assertThatCode(() -> consumer.onMessage(message("evt-6", "UNKNOWN_EVENT",
                Map.of("authorId", 42L)))).doesNotThrowAnyException();

        verify(notificationService, never()).sendSoftwareEventNotification(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
