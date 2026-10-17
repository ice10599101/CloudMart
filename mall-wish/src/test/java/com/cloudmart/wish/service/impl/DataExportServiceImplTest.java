package com.cloudmart.wish.service.impl;

import com.cloudmart.wish.entity.DataExport;
import com.cloudmart.wish.repository.DataExportMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OPS-02 验收：持续 worker 分页消化积压；租约被接管时旧执行者结果作废；
 * 失败不覆盖接管者状态。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DataExportServiceImplTest {

    @Mock
    private DataExportMapper exportMapper;

    private DataExportServiceImpl service;

    private DataExport task(long id, long userId, String status) {
        DataExport t = new DataExport();
        t.setId(id);
        t.setUserId(userId);
        t.setStatus(status);
        t.setLeaseOwner("other-instance");
        t.setLeaseUntil(LocalDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(1));
        return t;
    }

    @BeforeEach
    void setUp() {
        com.cloudmart.wish.config.WishCryptoProperties props = new com.cloudmart.wish.config.WishCryptoProperties();
        service = new DataExportServiceImpl(
                exportMapper,
                new com.cloudmart.wish.util.ContentCipher(props),
                null, null, null, null, null,
                org.mockito.Mockito.mock(com.cloudmart.wish.repository.WishUserStatMapper.class),
                new ObjectMapper());
    }

    @Test
    @DisplayName("持续 worker：分页循环消化积压（120 个也能多轮消化，不死循环）")
    void scan_pendingTasks_paginatesUntilEmpty() {
        // 第一页 100 个（id 1..100），第二页 20 个（101..120），第三页空
        List<DataExport> page1 = new java.util.ArrayList<>();
        for (long i = 1; i <= 100; i++) {
            page1.add(task(i, 1L, "PENDING"));
        }
        List<DataExport> page2 = new java.util.ArrayList<>();
        for (long i = 101; i <= 120; i++) {
            page2.add(task(i, 1L, "PENDING"));
        }
        when(exportMapper.selectList(any())).thenReturn(page1, page2, List.of());

        service.scanPendingTasks();

        // 分页推进：3 次查询后终止
        verify(exportMapper, times(3)).selectList(any());
    }

    @Test
    @DisplayName("generate：PENDING 认领失败且无过期租约可接管 → 直接跳过，不产出结果")
    void generate_leaseHeldByOther_skips() throws Exception {
        when(exportMapper.claimPending(anyLong(), anyString(), any())).thenReturn(0);
        when(exportMapper.takeoverExpiredLease(anyLong(), anyString(), any())).thenReturn(0);

        var method = DataExportServiceImpl.class.getDeclaredMethod("generate", Long.class, Long.class);
        method.setAccessible(true);
        method.invoke(service, 9L, 100L);

        verify(exportMapper, never()).completeByOwner(anyLong(), anyString(), anyString(), anyString());
        verify(exportMapper, never()).failByOwner(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("generate：认领成功但聚合前任务行消失 → 静默返回（不 NPE 不覆盖）")
    void generate_taskGone_silentReturn() throws Exception {
        when(exportMapper.claimPending(anyLong(), anyString(), any())).thenReturn(1);
        when(exportMapper.selectById(9L)).thenReturn(null);

        var method = DataExportServiceImpl.class.getDeclaredMethod("generate", Long.class, Long.class);
        method.setAccessible(true);
        method.invoke(service, 9L, 100L);

        verify(exportMapper, never()).completeByOwner(anyLong(), anyString(), anyString(), anyString());
    }
}
