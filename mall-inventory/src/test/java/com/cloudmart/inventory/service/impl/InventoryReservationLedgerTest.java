package com.cloudmart.inventory.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.inventory.dto.DeductRequest;
import com.cloudmart.inventory.dto.ReleaseRequest;
import com.cloudmart.inventory.entity.InventoryReservation;
import com.cloudmart.inventory.repository.InventoryLogMapper;
import com.cloudmart.inventory.repository.InventoryMapper;
import com.cloudmart.inventory.repository.InventoryReservationMapper;
import com.cloudmart.inventory.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * STOCK-01：台账驱动的预占状态机——
 * 重复预占显式拒绝；释放一次性（CONFIRMED 冲突/RELEASED 幂等）；
 * 确认数量以台账为准（不信任客户端）；释放 SQL 带 reserved 下限。
 */
@DisplayName("InventoryServiceImpl 台账状态机")
class InventoryReservationLedgerTest {

    private static final Long ORDER_ID = 9001L;
    private static final Long SKU_ID = 100L;

    private InventoryServiceImpl service;
    private InventoryMapper inventoryMapper;
    private InventoryLogMapper inventoryLogMapper;
    private InventoryReservationMapper reservationMapper;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private TransactionTemplate transactionTemplate;
    private RLock lock;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws InterruptedException {
        inventoryMapper = mock(InventoryMapper.class);
        inventoryLogMapper = mock(InventoryLogMapper.class);
        reservationMapper = mock(InventoryReservationMapper.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        transactionTemplate = mock(TransactionTemplate.class);
        // 直接执行回调（单测内不模拟事务回滚语义）
        AtomicReference<TransactionCallback<?>> callback = new AtomicReference<>();
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> cb = inv.getArgument(0);
            return cb.doInTransaction(null);
        });
        // executeWithoutResult 是接口 default 方法，mock 不执行——显式触发回调
        org.mockito.Mockito.doAnswer(inv -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> consumer =
                    inv.getArgument(0);
            consumer.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        RedissonClient redissonClient = mock(RedissonClient.class);
        lock = mock(RLock.class);
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        service = new InventoryServiceImpl(inventoryMapper, inventoryLogMapper, reservationMapper,
                null, redisTemplate,
                mock(org.springframework.data.redis.core.script.DefaultRedisScript.class),
                redissonClient, transactionTemplate);
    }

    private InventoryReservation reservation(String status) {
        InventoryReservation reservation = new InventoryReservation();
        reservation.setOrderId(ORDER_ID);
        reservation.setSkuId(SKU_ID);
        reservation.setQuantity(3);
        reservation.setStatus(status);
        return reservation;
    }

    private void stubLuaSuccess() {
        Mockito.when(redisTemplate.execute(any(org.springframework.data.redis.core.script.RedisScript.class),
                any(List.class), any(Object[].class))).thenReturn(1L);
    }

    @Test
    @DisplayName("预扣成功后登记台账；台账已存在（重复预占）显式拒绝并归还 Redis")
    void deduct_duplicateReservation_rejected() {
        stubLuaSuccess();
        when(inventoryMapper.deductStock(SKU_ID, 2)).thenReturn(1);
        // 第一次：登记成功
        when(reservationMapper.insertReservation(ORDER_ID, SKU_ID, 2)).thenReturn(1);
        assertThat(service.deductStock(new DeductRequest(SKU_ID, 2, ORDER_ID))).isTrue();

        // 第二次：唯一键冲突（insert 返回 0）→ 抛 DUPLICATE，且 Redis 归还
        stubLuaSuccess();
        when(reservationMapper.insertReservation(ORDER_ID, SKU_ID, 2)).thenReturn(0);
        assertThatThrownBy(() -> service.deductStock(new DeductRequest(SKU_ID, 2, ORDER_ID)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "INVENTORY_DUPLICATE_RESERVATION");
        verify(valueOperations, Mockito.atLeastOnce()).increment(anyString(), eq(2L));
    }

    @Test
    @DisplayName("释放：RESERVED → RELEASED 一次性迁移，按台账数量释放")
    void release_reserved_releasedOnce() {
        when(reservationMapper.findByOrderAndSku(ORDER_ID, SKU_ID)).thenReturn(reservation("RESERVED"));
        when(reservationMapper.releaseReservation(ORDER_ID, SKU_ID)).thenReturn(1);
        when(inventoryMapper.releaseStock(SKU_ID, 3)).thenReturn(1);

        service.releaseStock(new ReleaseRequest(SKU_ID, 2, ORDER_ID));

        // 以台账数量（3）而非客户端数量（2）释放
        verify(inventoryMapper).releaseStock(SKU_ID, 3);
        verify(inventoryLogMapper).insert(any(com.cloudmart.inventory.entity.InventoryLog.class));
    }

    @Test
    @DisplayName("释放：已 RELEASED 幂等成功，不再动库存行")
    void release_alreadyReleased_idempotent() {
        when(reservationMapper.findByOrderAndSku(ORDER_ID, SKU_ID)).thenReturn(reservation("RELEASED"));

        service.releaseStock(new ReleaseRequest(SKU_ID, 2, ORDER_ID));

        verify(reservationMapper, never()).releaseReservation(anyLong(), anyLong());
        verify(inventoryMapper, never()).releaseStock(anyLong(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("释放：已 CONFIRMED 冲突——确认销售不得复用释放回补可售库存")
    void release_confirmed_conflict() {
        when(reservationMapper.findByOrderAndSku(ORDER_ID, SKU_ID)).thenReturn(reservation("CONFIRMED"));

        assertThatThrownBy(() -> service.releaseStock(new ReleaseRequest(SKU_ID, 2, ORDER_ID)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "INVENTORY_RELEASE_CONFLICT");
        verify(inventoryMapper, never()).releaseStock(anyLong(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("确认：数量以台账为准（客户端 5 vs 台账 3 → 按 3 确认）")
    void confirm_quantityFromLedger() {
        when(reservationMapper.findByOrderAndSku(ORDER_ID, SKU_ID)).thenReturn(reservation("RESERVED"));
        when(reservationMapper.confirmReservation(ORDER_ID, SKU_ID)).thenReturn(1);
        when(inventoryMapper.confirmDeduct(SKU_ID, 3)).thenReturn(1);

        service.confirmDeduct(SKU_ID, 5, ORDER_ID);

        verify(inventoryMapper).confirmDeduct(SKU_ID, 3);
        verify(inventoryMapper, never()).confirmDeduct(SKU_ID, 5);
    }

    @Test
    @DisplayName("确认：已 CONFIRMED 幂等成功；已 RELEASED 冲突")
    void confirm_stateMachine() {
        when(reservationMapper.findByOrderAndSku(ORDER_ID, SKU_ID)).thenReturn(reservation("CONFIRMED"));
        service.confirmDeduct(SKU_ID, 3, ORDER_ID);
        verify(inventoryMapper, never()).confirmDeduct(anyLong(), org.mockito.ArgumentMatchers.anyInt());

        when(reservationMapper.findByOrderAndSku(ORDER_ID, SKU_ID)).thenReturn(reservation("RELEASED"));
        assertThatThrownBy(() -> service.confirmDeduct(SKU_ID, 3, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "INVENTORY_CONFIRM_CONFLICT");
    }

    @Test
    @DisplayName("T04/LC04：无台账行拒绝释放（返回异常并核查），不再裸更新库存行")
    void release_noLedgerRow_rejected() {
        when(reservationMapper.findByOrderAndSku(ORDER_ID, SKU_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.releaseStock(new ReleaseRequest(SKU_ID, 2, ORDER_ID)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("INVENTORY_RESERVATION_MISSING"));
        verify(inventoryMapper, never()).releaseStock(anyLong(), anyInt());
        verify(reservationMapper, never()).releaseReservation(anyLong(), anyLong());
    }
}
