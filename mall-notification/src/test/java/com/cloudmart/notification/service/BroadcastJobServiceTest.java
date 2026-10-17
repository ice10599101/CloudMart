package com.cloudmart.notification.service;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.notification.converter.NotificationConverter;
import com.cloudmart.notification.dto.BroadcastJobRequest;
import com.cloudmart.notification.dto.NotificationDTO;
import com.cloudmart.notification.entity.Notification;
import com.cloudmart.notification.entity.NotificationBroadcastItem;
import com.cloudmart.notification.entity.NotificationBroadcastJob;
import com.cloudmart.notification.feign.UserFeignClient;
import com.cloudmart.notification.repository.NotificationBroadcastItemMapper;
import com.cloudmart.notification.repository.NotificationBroadcastJobMapper;
import com.cloudmart.notification.repository.NotificationMapper;
import com.cloudmart.notification.websocket.WebSocketSessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ADMIN-03：群发批次任务——requestId 幂等、禁用账号跳过、失败记录可重试
 * （notification_id 幂等，重复重试不重复投递）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BroadcastJobService 群发批次")
class BroadcastJobServiceTest {

    @Mock
    private NotificationBroadcastJobMapper jobMapper;
    @Mock
    private NotificationBroadcastItemMapper itemMapper;
    @Mock
    private NotificationMapper notificationMapper;
    @Mock
    private UserFeignClient userFeignClient;
    @Mock
    private WebSocketSessionManager sessionManager;
    @Mock
    private NotificationConverter notificationConverter;

    private TransactionTemplate txTemplate;
    private BroadcastJobService service;

    @BeforeEach
    void setUp() {
        txTemplate = mock(TransactionTemplate.class);
        when(txTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> callback = inv.getArgument(0);
            return callback.doInTransaction(new SimpleTransactionStatus());
        });
        org.mockito.Mockito.doAnswer(inv -> {
            Consumer<TransactionStatus> consumer = inv.getArgument(0);
            consumer.accept(new SimpleTransactionStatus());
            return null;
        }).when(txTemplate).executeWithoutResult(any());
        service = new BroadcastJobService(jobMapper, itemMapper, notificationMapper, userFeignClient,
                sessionManager, notificationConverter, txTemplate);
        when(notificationConverter.toDTO(any())).thenReturn(
                new NotificationDTO(900L, 1L, "SYSTEM", "测试公告", "内容", false, null, null, null, null));
        // insert 回填自增 ID（后续明细记录 notification_id）
        when(notificationMapper.insert(any(Notification.class))).thenAnswer(inv -> {
            inv.getArgument(0, Notification.class).setId(900L);
            return 1;
        });
        when(itemMapper.insert(any(NotificationBroadcastItem.class))).thenReturn(1);
        when(jobMapper.insert(any(NotificationBroadcastJob.class))).thenReturn(1);
    }

    private BroadcastJobRequest request(String requestId) {
        return new BroadcastJobRequest("SYSTEM", "测试公告", "内容", requestId);
    }

    private Map<String, Object> user(long id, int status) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", id);
        row.put("status", status);
        return row;
    }

    private NotificationBroadcastJob job(long id, String jobNo, String requestId) {
        NotificationBroadcastJob entity = new NotificationBroadcastJob();
        entity.setId(id);
        entity.setJobNo(jobNo);
        entity.setRequestId(requestId);
        entity.setType("SYSTEM");
        entity.setTitle("测试公告");
        entity.setContent("内容");
        return entity;
    }

    @Test
    @DisplayName("提交幂等：同 requestId 返回原批次，不重复投递")
    void start_idempotentByRequestId() {
        when(jobMapper.selectOne(any())).thenReturn(job(1L, "BRD1", "req-0001"));
        when(jobMapper.selectCount(any())).thenReturn(0L);

        BroadcastJobService.BroadcastJobSummary summary = service.start(request("req-0001"), 9001L);

        assertThat(summary.jobNo()).isEqualTo("BRD1");
        assertThat(summary.target()).isZero();
        verify(notificationMapper, never()).insert(any(Notification.class));
        verify(itemMapper, never()).insert(any(NotificationBroadcastItem.class));
    }

    @Test
    @DisplayName("群发：禁用账号记 SKIPPED，正常用户逐人投递并记 notification_id")
    void start_deliversEnabledSkipsDisabled() {
        when(jobMapper.selectOne(any())).thenReturn(null);
        when(userFeignClient.listUsers(1, 500))
                .thenReturn(ApiResponse.ok(List.of(user(1L, 1), user(2L, 0), user(3L, 1))));

        BroadcastJobService.BroadcastJobSummary summary = service.start(request("req-0002"), 9001L);

        assertThat(summary.target()).isEqualTo(3);
        assertThat(summary.delivered()).isEqualTo(2);
        assertThat(summary.skipped()).isEqualTo(1);
        assertThat(summary.failed()).isZero();
        assertThat(summary.status()).isEqualTo("COMPLETED");

        org.mockito.ArgumentCaptor<NotificationBroadcastItem> captor =
                org.mockito.ArgumentCaptor.forClass(NotificationBroadcastItem.class);
        verify(itemMapper, times(3)).insert(captor.capture());
        // 先记禁用账号 SKIPPED，再按启用用户顺序投递
        assertThat(captor.getAllValues())
                .extracting(NotificationBroadcastItem::getStatus)
                .containsExactly("SKIPPED", "DELIVERED", "DELIVERED");
        // 每次投递都绑定通知 ID（重试幂等依据）
        assertThat(captor.getAllValues().stream()
                .filter(i -> "DELIVERED".equals(i.getStatus()))
                .allMatch(i -> i.getNotificationId() != null)).isTrue();
        verify(sessionManager, times(2)).sendMessageToUser(any(), any(NotificationDTO.class));
    }

    @Test
    @DisplayName("单用户失败不影响整体：记录 FAILED 明细，批次 PARTIAL")
    void start_userFailure_recordedAndContinues() {
        when(jobMapper.selectOne(any())).thenReturn(null);
        when(userFeignClient.listUsers(1, 500))
                .thenReturn(ApiResponse.ok(List.of(user(7L, 1), user(8L, 1))));
        when(notificationMapper.insert(any(Notification.class)))
                .thenAnswer(inv -> {
                    inv.getArgument(0, Notification.class).setId(901L);
                    return 1;
                })
                .thenThrow(new RuntimeException("db down"));

        BroadcastJobService.BroadcastJobSummary summary = service.start(request("req-0003"), 9001L);

        assertThat(summary.delivered()).isEqualTo(1);
        assertThat(summary.failed()).isEqualTo(1);
        assertThat(summary.status()).isEqualTo("PARTIAL");
        // 两个用户各一条明细：DELIVERED + FAILED（失败明细经 executeWithoutResult 补记）
        verify(itemMapper, times(2)).insert(any(NotificationBroadcastItem.class));
    }

    @Test
    @DisplayName("重试：仅认领 FAILED 项（CAS），按明细重算计数；重复重试不重复投递")
    void retry_onlyFailedItems() {
        when(jobMapper.selectOne(any())).thenReturn(job(5L, "BRD5", "req-0005"));
        NotificationBroadcastItem failedItem = new NotificationBroadcastItem();
        failedItem.setId(77L);
        failedItem.setJobId(5L);
        failedItem.setUserId(9L);
        failedItem.setStatus("FAILED");
        when(itemMapper.selectList(any())).thenReturn(List.of(failedItem));
        when(itemMapper.markDelivering(5L, 9L)).thenReturn(1);
        when(itemMapper.markDelivered(any(), any())).thenReturn(1);
        when(itemMapper.selectCount(any())).thenReturn(3L, 0L, 1L); // DELIVERED/FAILED/SKIPPED

        BroadcastJobService.BroadcastJobSummary summary = service.retry("BRD5");

        verify(notificationMapper, times(1)).insert(any(Notification.class));
        verify(itemMapper).markDelivering(5L, 9L);
        verify(itemMapper).markDelivered(77L, 900L);
        assertThat(summary.status()).isEqualTo("COMPLETED");
        assertThat(summary.target()).isEqualTo(4);
        assertThat(summary.failed()).isZero();
    }

}
