package com.cloudmart.software.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.software.entity.Software;
import com.cloudmart.software.entity.SoftwareFanoutTask;
import com.cloudmart.software.entity.SoftwareSubscriptionDelivery;
import com.cloudmart.software.entity.SoftwareUpdateRead;
import com.cloudmart.software.enums.SoftwareLifecycle;
import com.cloudmart.software.repository.SoftwareFanoutTaskMapper;
import com.cloudmart.software.repository.SoftwareMapper;
import com.cloudmart.software.repository.SoftwareReleaseMapper;
import com.cloudmart.software.repository.SoftwareRevisionMapper;
import com.cloudmart.software.repository.SoftwareSubscriptionDeliveryMapper;
import com.cloudmart.software.repository.SoftwareSubscriptionMapper;
import com.cloudmart.software.repository.SoftwareUpdateReadMapper;
import com.cloudmart.software.vo.software.SoftwareCardVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;

/**
 * UX05 单测：已读幂等/批量上限/游标分页/下架软件可见性标注。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SoftwareUpdateFeedServiceTest {

    @Mock
    private SoftwareSubscriptionMapper subscriptionMapper;
    @Mock
    private SoftwareSubscriptionDeliveryMapper deliveryMapper;
    @Mock
    private SoftwareFanoutTaskMapper fanoutTaskMapper;
    @Mock
    private SoftwareRevisionMapper revisionMapper;
    @Mock
    private SoftwareReleaseMapper releaseMapper;
    @Mock
    private SoftwareUpdateReadMapper readMapper;
    @Mock
    private SoftwareMapper softwareMapper;
    @Mock
    private com.cloudmart.software.service.SoftwareQueryService softwareQueryService;

    private SoftwareUpdateFeedServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SoftwareUpdateFeedServiceImpl(subscriptionMapper, deliveryMapper,
                fanoutTaskMapper, revisionMapper, releaseMapper, readMapper, softwareMapper,
                softwareQueryService,
                Clock.fixed(LocalDateTime.of(2026, 10, 20, 0, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        initDeliveryTableInfo();
        initReadTableInfo();
    }

    private static void initDeliveryTableInfo() {
        com.baomidou.mybatisplus.core.MybatisConfiguration configuration =
                new com.baomidou.mybatisplus.core.MybatisConfiguration();
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(configuration, ""),
                SoftwareSubscriptionDelivery.class);
    }

    private static void initReadTableInfo() {
        com.baomidou.mybatisplus.core.MybatisConfiguration configuration =
                new com.baomidou.mybatisplus.core.MybatisConfiguration();
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(configuration, ""),
                SoftwareUpdateRead.class);
    }

    private SoftwareSubscriptionDelivery delivery(long id) {
        SoftwareSubscriptionDelivery d = new SoftwareSubscriptionDelivery();
        d.setId(id);
        d.setSoftwareId(7L);
        d.setPublicationSeq(1);
        d.setUserId(8906L);
        d.setStatus("SENT");
        return d;
    }

    @Test
    void markRead_empty_returnsZero() {
        assertThat(service.markRead(1L, List.of())).isZero();
    }

    @Test
    void markRead_overBatchLimit_rejected() {
        List<Long> tooMany = java.util.stream.LongStream.rangeClosed(1, 51)
                .boxed().toList();
        assertThatThrownBy(() -> service.markRead(1L, tooMany))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("50");
    }

    @Test
    void markRead_duplicate_idempotent() {
        // 已读台账判重：输入顺序确定（List.of(11,12) 顺序迭代），
        // 第一次判重（11）命中已读台账，第二次（12）未读。
        SoftwareUpdateRead existing = new SoftwareUpdateRead();
        existing.setUserId(1L);
        existing.setDeliveryId(11L);
        java.util.concurrent.atomic.AtomicInteger call = new java.util.concurrent.atomic.AtomicInteger();
        lenient().when(readMapper.selectOne(any())).thenAnswer(inv ->
                call.incrementAndGet() == 1 ? existing : null);

        int inserted = service.markRead(1L, List.of(11L, 12L));
        assertThat(inserted).isEqualTo(1);
        verify_inserted(1);
    }

    private void verify_inserted(int expected) {
        org.mockito.Mockito.verify(readMapper, times(expected)).insert(any(SoftwareUpdateRead.class));
    }

    @Test
    void updates_cursorPagination_hasMore() {
        lenient().when(deliveryMapper.selectList(any()))
                .thenReturn(List.of(delivery(30), delivery(29), delivery(28), delivery(27)));
        lenient().when(readMapper.selectList(any())).thenReturn(List.of());
        lenient().when(fanoutTaskMapper.selectOne(any())).thenReturn(null);
        Software active = new Software();
        active.setId(7L);
        active.setLifecycle(SoftwareLifecycle.ACTIVE);
        active.setPublishedRevisionId(5L);
        lenient().when(softwareMapper.selectById(7L)).thenReturn(active);

        var result = service.myUpdates(8906L, 0, 3);
        assertThat(result.get("hasMore")).isEqualTo(true);
        // 游标=本页最后一条 id（28），下一页取 <28
        assertThat(result.get("nextCursor")).isEqualTo("28");
        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> items = (List<java.util.Map<String, Object>>) result.get("items");
        assertThat(items).hasSize(3);
        assertThat(items.get(0).get("eventId")).isEqualTo("30");
    }

    @Test
    void updates_unlistedSoftware_markedInvisible() {
        lenient().when(deliveryMapper.selectList(any()))
                .thenReturn(List.of(delivery(30)));
        lenient().when(readMapper.selectList(any())).thenReturn(List.of());
        lenient().when(fanoutTaskMapper.selectOne(any())).thenReturn(null);
        Software banned = new Software();
        banned.setId(7L);
        banned.setLifecycle(SoftwareLifecycle.BANNED);
        lenient().when(softwareMapper.selectById(7L)).thenReturn(banned);

        var result = service.myUpdates(8906L, 0, 10);
        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> items = (List<java.util.Map<String, Object>>) result.get("items");
        assertThat(items.get(0).get("softwareVisible")).isEqualTo(false); // 历史保留，标注不可用
    }

    @Test
    void subscriptions_list_withUnreadCount() {
        com.cloudmart.software.entity.SoftwareSubscription sub =
                new com.cloudmart.software.entity.SoftwareSubscription();
        sub.setSoftwareId(7L);
        sub.setUserId(8906L);
        sub.setPlatforms("WINDOWS");
        sub.setChannels("STABLE");
        sub.setCreatedAt(LocalDateTime.now());
        lenient().when(subscriptionMapper.selectList(any())).thenReturn(List.of(sub));
        lenient().when(softwareQueryService.cardsByIds(anyList()))
                .thenReturn(List.of(new SoftwareCardVO(7L, "软件A", "s", 1L, 1L, "效率", "FREE",
                        List.of("WINDOWS"), null, 0, 0, 0, null)));
        lenient().when(deliveryMapper.selectList(any())).thenReturn(
                List.of(delivery(1), delivery(2))); // 2 条 SENT
        lenient().when(readMapper.selectCount(any())).thenReturn(1L); // 已读 1

        var rows = service.mySubscriptions(8906L);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("unreadCount")).isEqualTo(1L);
        assertThat(rows.get(0).get("visible")).isEqualTo(true);
    }
}
