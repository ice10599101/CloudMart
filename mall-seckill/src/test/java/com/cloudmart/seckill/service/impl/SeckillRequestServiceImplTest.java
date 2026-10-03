package com.cloudmart.seckill.service.impl;

import com.cloudmart.seckill.entity.SeckillRequest;
import com.cloudmart.seckill.repository.SeckillRequestMapper;
import com.cloudmart.seckill.service.SeckillRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T09 请求事实服务测试：购买限额以 DB 唯一键为权威——已有 PENDING/SUCCESS
 * 拒绝重复占用，终态失败换新 requestId 重排队，并发插入败者回滚重读。
 */
class SeckillRequestServiceImplTest {

    private SeckillRequestMapper requestMapper;
    private SeckillRequestServiceImpl requestService;

    private static final Long USER_ID = 1001L;
    private static final Long ACTIVITY_ID = 2001L;
    private static final Long PRODUCT_ID = 3001L;
    private static final Long SKU_ID = 4001L;

    @BeforeEach
    void setUp() {
        requestMapper = mock(SeckillRequestMapper.class);
        requestService = new SeckillRequestServiceImpl(requestMapper);
    }

    private SeckillRequest existingRow(String status) {
        SeckillRequest row = new SeckillRequest();
        row.setId(9L);
        row.setRequestId("req-old");
        row.setActivityId(ACTIVITY_ID);
        row.setProductId(PRODUCT_ID);
        row.setSkuId(SKU_ID);
        row.setUserId(USER_ID);
        row.setQuantity(1);
        row.setSeckillPrice(new BigDecimal("9.90"));
        row.setStatus(status);
        return row;
    }

    @Test
    @DisplayName("首次占用：库存预减 + 请求落库（出生即调度恢复检查）")
    void holdSeat_new_insertsWithRetrySchedule() {
        when(requestMapper.selectOne(any())).thenReturn(null);
        when(requestMapper.holdStock(PRODUCT_ID)).thenReturn(1);

        SeckillRequest held = requestService.holdSeat(USER_ID, ACTIVITY_ID, PRODUCT_ID, SKU_ID,
                new BigDecimal("9.90"), 1);

        assertThat(held.getRequestId()).isNotBlank();
        assertThat(held.getStatus()).isEqualTo("PENDING");
        assertThat(held.getNextRetryAt()).isAfter(LocalDateTime.now());
        verify(requestMapper).holdStock(PRODUCT_ID);
        verify(requestMapper).insert(any(SeckillRequest.class));
    }

    @Test
    @DisplayName("DB 口径售罄（库存预减 0 行）→ SeatSoldOutException")
    void holdSeat_dbSoldOut_throws() {
        when(requestMapper.selectOne(any())).thenReturn(null);
        when(requestMapper.holdStock(PRODUCT_ID)).thenReturn(0);

        assertThatThrownBy(() -> requestService.holdSeat(USER_ID, ACTIVITY_ID, PRODUCT_ID, SKU_ID,
                new BigDecimal("9.90"), 1))
                .isInstanceOf(SeckillRequestService.SeatSoldOutException.class);
    }

    @Test
    @DisplayName("已有 PENDING 资格 → SeatExistsException 携带既有事实（同 requestId 幂等）")
    void holdSeat_existingPending_throwsWithExisting() {
        SeckillRequest existing = existingRow("PENDING");
        when(requestMapper.selectOne(any())).thenReturn(existing);

        assertThatThrownBy(() -> requestService.holdSeat(USER_ID, ACTIVITY_ID, PRODUCT_ID, SKU_ID,
                new BigDecimal("9.90"), 1))
                .isInstanceOfSatisfying(SeckillRequestService.SeatExistsException.class,
                        e -> assertThat(e.existing().getRequestId()).isEqualTo("req-old"));
    }

    @Test
    @DisplayName("已有 SUCCESS 资格 → SeatExistsException（不可重复购买）")
    void holdSeat_existingSuccess_throws() {
        SeckillRequest existing = existingRow("SUCCESS");
        when(requestMapper.selectOne(any())).thenReturn(existing);

        assertThatThrownBy(() -> requestService.holdSeat(USER_ID, ACTIVITY_ID, PRODUCT_ID, SKU_ID,
                new BigDecimal("9.90"), 1))
                .isInstanceOf(SeckillRequestService.SeatExistsException.class);
    }

    @Test
    @DisplayName("T09：终态失败重发起 → CAS 复用原行换新 requestId")
    void holdSeat_existingFailed_reinitiates() {
        when(requestMapper.holdStock(PRODUCT_ID)).thenReturn(1);
        SeckillRequest failedRow = existingRow("FAILED");
        SeckillRequest requeuedRow = existingRow("PENDING");
        requeuedRow.setRequestId("req-new");
        when(requestMapper.selectOne(any())).thenReturn(failedRow, requeuedRow);
        when(requestMapper.reinitiate(any(SeckillRequest.class))).thenReturn(1);

        SeckillRequest held = requestService.holdSeat(USER_ID, ACTIVITY_ID, PRODUCT_ID, SKU_ID,
                new BigDecimal("9.90"), 1);

        assertThat(held.getRequestId()).isEqualTo("req-new");
        assertThat(held.getStatus()).isEqualTo("PENDING");
        ArgumentCaptor<SeckillRequest> captor = ArgumentCaptor.forClass(SeckillRequest.class);
        verify(requestMapper).reinitiate(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(9L);
        assertThat(captor.getValue().getRequestId()).isNotEqualTo("req-old");
        assertThat(captor.getValue().getSendAttempts()).isZero();
    }

    @Test
    @DisplayName("终态失败重发起 CAS 失败（他方先行）→ 返回胜者事实")
    void holdSeat_reinitiateLost_returnsWinner() {
        when(requestMapper.holdStock(PRODUCT_ID)).thenReturn(1);
        SeckillRequest failedRow = existingRow("FAILED");
        SeckillRequest winner = existingRow("PENDING");
        winner.setRequestId("req-winner");
        when(requestMapper.selectOne(any())).thenReturn(failedRow, winner);
        when(requestMapper.reinitiate(any(SeckillRequest.class))).thenReturn(0);

        assertThatThrownBy(() -> requestService.holdSeat(USER_ID, ACTIVITY_ID, PRODUCT_ID, SKU_ID,
                new BigDecimal("9.90"), 1))
                .isInstanceOfSatisfying(SeckillRequestService.SeatExistsException.class,
                        e -> assertThat(e.existing().getRequestId()).isEqualTo("req-winner"));
    }

    @Test
    @DisplayName("并发插入冲突：胜者 PENDING → 败者抛 SeatExists（事务回滚预减）")
    void holdSeat_duplicateInsert_pendingWinner_throws() {
        when(requestMapper.selectOne(any())).thenReturn(null, existingRow("PENDING"));
        when(requestMapper.holdStock(PRODUCT_ID)).thenReturn(1);
        when(requestMapper.insert(any(SeckillRequest.class)))
                .thenThrow(new DuplicateKeyException("uk_seckill_purchase_limit"));

        assertThatThrownBy(() -> requestService.holdSeat(USER_ID, ACTIVITY_ID, PRODUCT_ID, SKU_ID,
                new BigDecimal("9.90"), 1))
                .isInstanceOf(SeckillRequestService.SeatExistsException.class);
    }

    @Test
    @DisplayName("settleSuccess/settleFailed/scheduleRetry 走 CAS 条件更新")
    void settleMethods_casPassthrough() {
        LocalDateTime nextRetry = LocalDateTime.now().plusSeconds(10);
        when(requestMapper.markSuccess("req-1", 55L)).thenReturn(1);
        when(requestMapper.markFailed("req-2", "x")).thenReturn(0);
        when(requestMapper.scheduleRetry("req-3", 1, nextRetry)).thenReturn(1);

        assertThat(requestService.settleSuccess("req-1", 55L)).isTrue();
        assertThat(requestService.settleFailed("req-2", "x")).isFalse();
        assertThat(requestService.scheduleRetry("req-3", 1, nextRetry)).isTrue();
    }
}
