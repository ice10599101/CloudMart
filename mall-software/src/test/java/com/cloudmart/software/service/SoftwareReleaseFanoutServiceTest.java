package com.cloudmart.software.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.software.config.SoftwareMetrics;
import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwareFanoutTask;
import com.cloudmart.software.entity.SoftwareSubscription;
import com.cloudmart.software.entity.SoftwareSubscriptionDelivery;
import com.cloudmart.software.entity.SoftwareUserPreference;
import com.cloudmart.software.enums.SoftwareLifecycle;
import com.cloudmart.software.feign.SoftwareNotificationFeignClient;
import com.cloudmart.software.repository.SoftwareFanoutTaskMapper;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.repository.SoftwareSubscriptionDeliveryMapper;
import com.cloudmart.software.repository.SoftwareSubscriptionMapper;
import com.cloudmart.software.repository.SoftwareUserPreferenceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P1 订阅广播单测（9.4）：游标批次扫描（非作者/关闭偏好过滤）、
 * 下架取消、投递退避与 FAILED 终态、游标推进 fencing。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SoftwareReleaseFanoutServiceTest {

    private static final Long SOFTWARE_ID = 100L;
    private static final Long OWNER_ID = 7L;

    @Mock
    private SoftwareFanoutTaskMapper fanoutTaskMapper;
    @Mock
    private SoftwareSubscriptionMapper subscriptionMapper;
    @Mock
    private SoftwareSubscriptionDeliveryMapper deliveryMapper;
    @Mock
    private SoftwareUserPreferenceMapper preferenceMapper;
    @Mock
    private SoftwareMapper softwareMapper;
    @Mock
    private com.cloudmart.software.repository.SoftwareReleaseMapper releaseMapper;
    @Mock
    private com.cloudmart.software.repository.SoftwareSourceMapper sourceMapper;
    @Mock
    private SoftwareNotificationFeignClient notificationClient;
    @Mock
    private TransactionTemplate transactionTemplate;

    private SoftwareReleaseFanoutService service;

    @BeforeEach
    void setUp() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null));
        service = new SoftwareReleaseFanoutService(fanoutTaskMapper, releaseMapper, sourceMapper,
                subscriptionMapper,
                deliveryMapper, preferenceMapper, softwareMapper, notificationClient,
                org.mockito.Mockito.mock(SoftwareMetrics.class), transactionTemplate,
                Clock.fixed(Instant.parse("2026-10-16T02:00:00Z"), ZoneId.systemDefault()));
        // insert 回填自增 ID
        doAnswer(inv -> {
            SoftwareSubscriptionDelivery row = inv.getArgument(0);
            row.setId(900L);
            return 1;
        }).when(deliveryMapper).insert(any(SoftwareSubscriptionDelivery.class));
    }

    private SoftwareFanoutTask pendingTask(int seq) {
        SoftwareFanoutTask task = new SoftwareFanoutTask();
        task.setId(1L);
        task.setSoftwareId(SOFTWARE_ID);
        task.setRevisionId(600L);
        task.setPublicationSeq(seq);
        task.setPublishedAt(LocalDateTime.of(2026, 10, 15, 20, 0));
        task.setCursorSubscriptionId(0L);
        task.setStatus("PENDING");
        task.setLeaseVersion(0);
        task.setAttempts(0);
        // SF05：默认业务变化任务（有新版本），平台/渠道集合 WINDOWS/STABLE
        task.setBusinessChange(true);
        task.setPlatforms("WINDOWS");
        task.setChannels("STABLE");
        return task;
    }

    private Software software(SoftwareLifecycle lifecycle, int seq) {
        Software software = new Software();
        software.setId(SOFTWARE_ID);
        software.setOwnerId(OWNER_ID);
        software.setLifecycle(lifecycle);
        software.setPublicationSeq(seq);
        software.setPublishedRevisionId(600L);
        return software;
    }

    private SoftwareSubscription subscription(long id, Long userId) {
        SoftwareSubscription subscription = new SoftwareSubscription();
        subscription.setId(id);
        subscription.setSoftwareId(SOFTWARE_ID);
        subscription.setUserId(userId);
        subscription.setCreatedAt(LocalDateTime.of(2026, 10, 15, 10, 0));
        return subscription;
    }

    @Test
    @DisplayName("登记任务：PENDING、游标 0（唯一键幂等由数据库兜底）")
    void registerTask() {
        service.registerTask(SOFTWARE_ID, 600L, 2, LocalDateTime.of(2026, 10, 16, 1, 0), null);

        ArgumentCaptor<SoftwareFanoutTask> captor =
                ArgumentCaptor.forClass(SoftwareFanoutTask.class);
        verify(fanoutTaskMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("PENDING");
        assertThat(captor.getValue().getCursorSubscriptionId()).isZero();
    }

    @Test
    @DisplayName("A20 风格：游标批次扫描过滤作者与关闭偏好用户，推进游标并投递")
    void runOnce_scansBatchAndDelivers() {
        // 首轮领取，任务完成后不再返回（真实实现按 status 过滤 DONE）
        when(fanoutTaskMapper.findDue(any())).thenReturn(pendingTask(1), null);
        when(fanoutTaskMapper.claim(any(), anyString(), any(), anyInt(), any())).thenReturn(1);
        when(softwareMapper.selectById(SOFTWARE_ID)).thenReturn(
                software(SoftwareLifecycle.ACTIVE, 1));
        when(subscriptionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                subscription(1L, OWNER_ID),   // 作者本人：不通知
                subscription(2L, 42L),        // 正常订阅者
                subscription(3L, 43L)));      // 关闭提醒偏好
        SoftwareUserPreference muted = new SoftwareUserPreference();
        muted.setUserId(43L);
        muted.setUpdateNotifications(false);
        when(preferenceMapper.selectList(any(Wrapper.class))).thenReturn(List.of(muted));
        when(notificationClient.releasePublished(any()))
                .thenReturn(ApiResponse.ok(1));

        service.runOnce();

        // 只插入一名订阅者的投递行，游标推进到本批最后一条（3）
        ArgumentCaptor<SoftwareSubscriptionDelivery> delivery =
                ArgumentCaptor.forClass(SoftwareSubscriptionDelivery.class);
        verify(deliveryMapper).insert(delivery.capture());
        assertThat(delivery.getValue().getUserId()).isEqualTo(42L);
        verify(fanoutTaskMapper).advanceCursor(eq(1L), eq(3L), eq(1), anyString(), any());
        // 投递成功 → SENT；任务 DONE
        verify(deliveryMapper).markSent(eq(delivery.getValue().getId()), any());
        verify(fanoutTaskMapper).finish(eq(1L), eq("DONE"), anyInt(), any(), any(),
                anyInt(), anyString(), any());
    }

    @Test
    @DisplayName("SF05：平台/渠道偏好不匹配的订阅者不生成投递（Windows stable 订阅者不收 macOS beta）")
    void runOnce_filtersByPreference() {
        when(fanoutTaskMapper.findDue(any())).thenReturn(pendingTask(1), null);
        when(fanoutTaskMapper.claim(any(), anyString(), any(), anyInt(), any())).thenReturn(1);
        when(softwareMapper.selectById(SOFTWARE_ID)).thenReturn(
                software(SoftwareLifecycle.ACTIVE, 1));
        // 任务平台/渠道集合 = WINDOWS/STABLE（夹具默认）
        SoftwareSubscription macBeta = subscription(1L, 51L);
        macBeta.setPlatforms("MACOS");
        macBeta.setChannels("BETA");
        SoftwareSubscription windowsStable = subscription(2L, 52L);
        windowsStable.setPlatforms("WINDOWS");
        windowsStable.setChannels("STABLE");
        SoftwareSubscription allPlatforms = subscription(3L, 53L); // null=全部
        when(subscriptionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                macBeta, windowsStable, allPlatforms));
        when(preferenceMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(notificationClient.releasePublished(any())).thenReturn(ApiResponse.ok(1));

        service.runOnce();

        // 只为 WINDOWS/STABLE 匹配者与偏好为空者生成投递；MACOS/BETA 被过滤
        ArgumentCaptor<SoftwareSubscriptionDelivery> delivery =
                ArgumentCaptor.forClass(SoftwareSubscriptionDelivery.class);
        verify(deliveryMapper, org.mockito.Mockito.times(2)).insert(delivery.capture());
        assertThat(delivery.getAllValues())
                .extracting(SoftwareSubscriptionDelivery::getUserId)
                .containsExactlyInAnyOrder(52L, 53L);
    }

    @Test
    @DisplayName("SF05：纯文案修改（无业务版本变化）不生成投递，任务完成留原因")
    void runOnce_skipsWhenNoBusinessChange() {
        SoftwareFanoutTask task = pendingTask(1);
        task.setBusinessChange(false);
        when(fanoutTaskMapper.findDue(any())).thenReturn(task, null);
        when(fanoutTaskMapper.claim(any(), anyString(), any(), anyInt(), any())).thenReturn(1);
        when(softwareMapper.selectById(SOFTWARE_ID)).thenReturn(
                software(SoftwareLifecycle.ACTIVE, 1));
        when(subscriptionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                subscription(1L, 42L)));

        service.runOnce();

        verify(deliveryMapper, never()).insert(any(SoftwareSubscriptionDelivery.class));
        verify(deliveryMapper).cancelPending(eq(SOFTWARE_ID), eq(1), any());
        verify(fanoutTaskMapper).finish(eq(1L), eq("DONE"), anyInt(), any(), any(),
                anyInt(), anyString(), any());
    }

    @Test
    @DisplayName("A26 相邻：软件已下架 → 取消未发投递、不产生通知")
    void runOnce_cancelsWhenSoftwareUnlisted() {
        when(fanoutTaskMapper.findDue(any())).thenReturn(pendingTask(1), null);
        when(fanoutTaskMapper.claim(any(), anyString(), any(), anyInt(), any())).thenReturn(1);
        when(softwareMapper.selectById(SOFTWARE_ID)).thenReturn(
                software(SoftwareLifecycle.UNLISTED, 1));

        service.runOnce();

        verify(deliveryMapper).cancelPending(eq(SOFTWARE_ID), eq(1), any());
        verify(notificationClient, never()).releasePublished(any());
        verify(fanoutTaskMapper).finish(eq(1L), eq("DONE"), anyInt(), any(), any(),
                anyInt(), anyString(), any());
    }

    @Test
    @DisplayName("投递重试：<=12 次退避 PENDING，达到 12 次转 FAILED（保留同一去重键）")
    void retryDueDeliveries_backoffAndTerminal() {
        // 本测试只关心投递重试：无待推进任务，重试用任务冻结的 revisionId
        when(fanoutTaskMapper.findDue(any())).thenReturn(null);
        com.cloudmart.software.entity.SoftwareFanoutTask task =
                new com.cloudmart.software.entity.SoftwareFanoutTask();
        task.setId(7L);
        task.setSoftwareId(SOFTWARE_ID);
        task.setPublicationSeq(2);
        task.setRevisionId(600L);
        when(fanoutTaskMapper.findBySoftwareAndSeq(SOFTWARE_ID, 2)).thenReturn(task);
        when(preferenceMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        SoftwareSubscriptionDelivery tenth = new SoftwareSubscriptionDelivery();
        tenth.setId(11L);
        tenth.setSoftwareId(SOFTWARE_ID);
        tenth.setPublicationSeq(2);
        tenth.setUserId(42L);
        tenth.setAttempts(11);
        SoftwareSubscriptionDelivery fresh = new SoftwareSubscriptionDelivery();
        fresh.setId(12L);
        fresh.setSoftwareId(SOFTWARE_ID);
        fresh.setPublicationSeq(2);
        fresh.setUserId(43L);
        fresh.setAttempts(0);
        when(deliveryMapper.findDue(any(), anyInt())).thenReturn(List.of(tenth, fresh));
        when(softwareMapper.selectById(SOFTWARE_ID)).thenReturn(
                software(SoftwareLifecycle.ACTIVE, 2));
        when(notificationClient.releasePublished(any()))
                .thenThrow(new RuntimeException("notification down"));

        service.runOnce();

        verify(deliveryMapper).markRetry(eq(11L), eq("FAILED"), eq(12), eq(null), any());
        verify(deliveryMapper).markRetry(eq(12L), eq("PENDING"), eq(1), any(), any());
    }

    @Test
    @DisplayName("退避函数：2^n 秒封顶 1 小时（1024s 后触顶）")
    void backoffCap() {
        assertThat(SoftwareReleaseFanoutService.backoff(1)).isEqualTo(Duration.ofSeconds(2));
        assertThat(SoftwareReleaseFanoutService.backoff(10)).isEqualTo(Duration.ofSeconds(1024));
        assertThat(SoftwareReleaseFanoutService.backoff(12)).isEqualTo(Duration.ofSeconds(3600));
        assertThat(SoftwareReleaseFanoutService.backoff(20)).isEqualTo(Duration.ofSeconds(3600));
    }
}
