package com.cloudmart.inventory.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.inventory.converter.InventoryConverter;
import com.cloudmart.inventory.dto.DeductRequest;
import com.cloudmart.inventory.dto.InventoryDTO;
import com.cloudmart.inventory.dto.ReleaseRequest;
import com.cloudmart.inventory.entity.Inventory;
import com.cloudmart.inventory.entity.InventoryLog;
import com.cloudmart.inventory.repository.InventoryLogMapper;
import com.cloudmart.inventory.repository.InventoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryServiceImplTest {

    private InventoryMapper inventoryMapper;
    private InventoryLogMapper inventoryLogMapper;
    private InventoryConverter inventoryConverter;
    private com.cloudmart.inventory.repository.InventoryReservationMapper reservationMapper;
    private com.cloudmart.inventory.repository.InventoryAdjustmentMapper adjustmentMapper;
    private StringRedisTemplate redisTemplate;
    private DefaultRedisScript<Long> deductInventoryScript;
    private RedissonClient redissonClient;
    private TransactionTemplate transactionTemplate;
    private RLock rLock;
    private ValueOperations<String, String> valueOperations;
    private InventoryServiceImpl inventoryService;

    private static final Long SKU_ID = 1001L;
    private static final Long PRODUCT_ID = 2001L;
    private static final Long ORDER_ID = 3001L;

    @BeforeEach
    void setUp() {
        inventoryMapper = mock(InventoryMapper.class);
        inventoryLogMapper = mock(InventoryLogMapper.class);
        inventoryConverter = mock(InventoryConverter.class);
        reservationMapper = mock(com.cloudmart.inventory.repository.InventoryReservationMapper.class);
        redisTemplate = mock(StringRedisTemplate.class);
        deductInventoryScript = mock(DefaultRedisScript.class);
        redissonClient = mock(RedissonClient.class);
        transactionTemplate = mock(TransactionTemplate.class);
        rLock = mock(RLock.class);
        valueOperations = mock(ValueOperations.class);

        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        adjustmentMapper = mock(com.cloudmart.inventory.repository.InventoryAdjustmentMapper.class);

        inventoryService = new InventoryServiceImpl(
                inventoryMapper, inventoryLogMapper,
                reservationMapper,
                adjustmentMapper,
                inventoryConverter,
                redisTemplate, deductInventoryScript, redissonClient, transactionTemplate,
                java.time.Clock.systemDefaultZone()
        );
    }

    /** T04：构造一条 RESERVED 台账行（释放/确认按台账数量与状态机迁移） */
    private com.cloudmart.inventory.entity.InventoryReservation reservation(String status, int quantity) {
        com.cloudmart.inventory.entity.InventoryReservation row =
                new com.cloudmart.inventory.entity.InventoryReservation();
        row.setId(1L);
        row.setOrderId(ORDER_ID);
        row.setSkuId(SKU_ID);
        row.setQuantity(quantity);
        row.setStatus(status);
        return row;
    }

    private void mockLockAcquired() throws InterruptedException {
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
    }

    @SuppressWarnings("unchecked")
    private void mockExecuteWithoutResult() {
        doAnswer(invocation -> {
            Consumer<?> action = invocation.getArgument(0);
            action.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any(Consumer.class));
    }

    @Nested
    @DisplayName("deductStock")
    class DeductStockTests {

        @Test
        @DisplayName("should throw when quantity is zero or negative")
        void deductStock_invalidQuantity_throwsException() {
            DeductRequest request = new DeductRequest(SKU_ID, 0, ORDER_ID);

            assertThatThrownBy(() -> inventoryService.deductStock(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("INVALID_QUANTITY"));
        }

        @Test
        @DisplayName("should throw when lock acquisition is interrupted")
        void deductStock_lockInterrupted_throwsException() throws InterruptedException {
            DeductRequest request = new DeductRequest(SKU_ID, 5, ORDER_ID);
            when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class)))
                    .thenThrow(new InterruptedException("interrupted"));

            assertThatThrownBy(() -> inventoryService.deductStock(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("INVENTORY_LOCK_INTERRUPTED"));

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        }

        @Test
        @DisplayName("should throw when lock cannot be acquired")
        void deductStock_lockBusy_throwsException() throws InterruptedException {
            DeductRequest request = new DeductRequest(SKU_ID, 5, ORDER_ID);
            when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);

            assertThatThrownBy(() -> inventoryService.deductStock(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("INVENTORY_BUSY"));
        }

        @Test
        @DisplayName("should return true when stock deduction succeeds")
        void deductStock_success_returnsTrue() throws InterruptedException {
            DeductRequest request = new DeductRequest(SKU_ID, 5, ORDER_ID);
            mockLockAcquired();

            when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
                when(redisTemplate.execute(eq(deductInventoryScript), any(), anyString()))
                        .thenReturn(1L);
                when(inventoryMapper.deductStock(SKU_ID, 5)).thenReturn(1);
                when(inventoryLogMapper.insert(any(InventoryLog.class))).thenReturn(1);
                return true;
            });

            boolean result = inventoryService.deductStock(request);

            assertThat(result).isTrue();
            verify(rLock).unlock();
        }

        @Test
        @DisplayName("should return false when Redis stock is insufficient")
        void deductStock_insufficientRedisStock_returnsFalse() throws InterruptedException {
            DeductRequest request = new DeductRequest(SKU_ID, 5, ORDER_ID);
            mockLockAcquired();

            when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
                when(redisTemplate.execute(eq(deductInventoryScript), any(), anyString()))
                        .thenReturn(0L);
                return false;
            });

            boolean result = inventoryService.deductStock(request);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false and rollback Redis when DB deduction fails")
        void deductStock_dbDeductFails_rollbacksRedis() throws InterruptedException {
            DeductRequest request = new DeductRequest(SKU_ID, 5, ORDER_ID);
            mockLockAcquired();

            when(redisTemplate.execute(eq(deductInventoryScript), any(), anyString()))
                    .thenReturn(1L);
            when(inventoryMapper.deductStock(SKU_ID, 5)).thenReturn(0);
            when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
                var callback = invocation.getArgument(0);
                try {
                    var method = callback.getClass().getDeclaredMethod("doInTransaction", org.springframework.transaction.TransactionStatus.class);
                    method.setAccessible(true);
                    return method.invoke(callback, (Object) null);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            boolean result = inventoryService.deductStock(request);

            assertThat(result).isFalse();
            verify(valueOperations).increment("inventory:product:" + SKU_ID, 5);
        }
    }

    @Nested
    @DisplayName("releaseStock")
    class ReleaseStockTests {

        @Test
        @DisplayName("should throw when quantity is zero or negative")
        void releaseStock_invalidQuantity_throwsException() {
            ReleaseRequest request = new ReleaseRequest(SKU_ID, 0, ORDER_ID);

            assertThatThrownBy(() -> inventoryService.releaseStock(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("INVALID_QUANTITY"));
        }

        @Test
        @DisplayName("T04 台账驱动：释放按台账数量回补可售库存")
        void releaseStock_success() throws InterruptedException {
            ReleaseRequest request = new ReleaseRequest(SKU_ID, 5, ORDER_ID);
            mockLockAcquired();
            when(reservationMapper.findByOrderAndSku(ORDER_ID, SKU_ID)).thenReturn(reservation("RESERVED", 5));
            when(reservationMapper.releaseReservation(ORDER_ID, SKU_ID)).thenReturn(1);
            when(inventoryMapper.releaseStock(SKU_ID, 5)).thenReturn(1);
            mockExecuteWithoutResult();

            inventoryService.releaseStock(request);

            verify(inventoryMapper).releaseStock(SKU_ID, 5);
            verify(valueOperations).increment("inventory:product:" + SKU_ID, 5);
            verify(inventoryLogMapper).insert(any(InventoryLog.class));
            verify(rLock).unlock();
        }

        @Test
        @DisplayName("T04 台账驱动：库存行回补失败（预占不足）抛 INVENTORY_NOT_FOUND")
        void releaseStock_notFound_throwsException() throws InterruptedException {
            ReleaseRequest request = new ReleaseRequest(SKU_ID, 5, ORDER_ID);
            mockLockAcquired();
            when(reservationMapper.findByOrderAndSku(ORDER_ID, SKU_ID)).thenReturn(reservation("RESERVED", 5));
            when(reservationMapper.releaseReservation(ORDER_ID, SKU_ID)).thenReturn(1);
            when(inventoryMapper.releaseStock(SKU_ID, 5)).thenReturn(0);
            mockExecuteWithoutResult();

            assertThatThrownBy(() -> inventoryService.releaseStock(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("INVENTORY_NOT_FOUND"));
        }

        @Test
        @DisplayName("T04/LC04：无台账行拒绝裸释放（INVENTORY_RESERVATION_MISSING），不更新库存")
        void releaseStock_noLedgerRow_rejected() throws InterruptedException {
            ReleaseRequest request = new ReleaseRequest(SKU_ID, 5, ORDER_ID);
            mockLockAcquired();
            when(reservationMapper.findByOrderAndSku(ORDER_ID, SKU_ID)).thenReturn(null);
            mockExecuteWithoutResult();

            assertThatThrownBy(() -> inventoryService.releaseStock(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                            .isEqualTo("INVENTORY_RESERVATION_MISSING"));
            verify(inventoryMapper, never()).releaseStock(anyLong(), anyInt());
        }

        @Test
        @DisplayName("T04/LC04：零订单释放被拒绝（INVENTORY_ORDER_REQUIRED）")
        void releaseStock_zeroOrder_rejected() throws InterruptedException {
            ReleaseRequest request = new ReleaseRequest(SKU_ID, 5, 0L);
            mockLockAcquired();
            mockExecuteWithoutResult();

            assertThatThrownBy(() -> inventoryService.releaseStock(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                            .isEqualTo("INVENTORY_ORDER_REQUIRED"));
            verify(inventoryMapper, never()).releaseStock(anyLong(), anyInt());
        }
    }

    @Nested
    @DisplayName("confirmDeduct")
    class ConfirmDeductTests {

        @Test
        @DisplayName("should throw when quantity is zero or negative")
        void confirmDeduct_invalidQuantity_throwsException() {
            assertThatThrownBy(() -> inventoryService.confirmDeduct(SKU_ID, 0, ORDER_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("INVALID_QUANTITY"));
        }

        @Test
        @DisplayName("T04 台账驱动：确认数量以台账为准，状态机迁移成功")
        void confirmDeduct_success() throws InterruptedException {
            mockLockAcquired();
            when(reservationMapper.findByOrderAndSku(ORDER_ID, SKU_ID)).thenReturn(reservation("RESERVED", 5));
            when(reservationMapper.confirmReservation(ORDER_ID, SKU_ID)).thenReturn(1);
            when(inventoryMapper.confirmDeduct(SKU_ID, 5)).thenReturn(1);
            mockExecuteWithoutResult();

            inventoryService.confirmDeduct(SKU_ID, 5, ORDER_ID);

            verify(inventoryMapper).confirmDeduct(SKU_ID, 5);
            verify(inventoryLogMapper).insert(any(InventoryLog.class));
            verify(rLock).unlock();
        }

        @Test
        @DisplayName("T04 台账驱动：库存行确认失败（预占不足）抛 INVENTORY_CONFIRM_FAILED")
        void confirmDeduct_insufficientReserved_throwsException() throws InterruptedException {
            mockLockAcquired();
            when(reservationMapper.findByOrderAndSku(ORDER_ID, SKU_ID)).thenReturn(reservation("RESERVED", 5));
            when(reservationMapper.confirmReservation(ORDER_ID, SKU_ID)).thenReturn(1);
            when(inventoryMapper.confirmDeduct(SKU_ID, 5)).thenReturn(0);
            mockExecuteWithoutResult();

            assertThatThrownBy(() -> inventoryService.confirmDeduct(SKU_ID, 5, ORDER_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("INVENTORY_CONFIRM_FAILED"));
        }

        @Test
        @DisplayName("T04/LC04：无台账行拒绝裸确认（INVENTORY_RESERVATION_MISSING），不更新库存")
        void confirmDeduct_noLedgerRow_rejected() throws InterruptedException {
            mockLockAcquired();
            when(reservationMapper.findByOrderAndSku(ORDER_ID, SKU_ID)).thenReturn(null);
            mockExecuteWithoutResult();

            assertThatThrownBy(() -> inventoryService.confirmDeduct(SKU_ID, 5, ORDER_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                            .isEqualTo("INVENTORY_RESERVATION_MISSING"));
            verify(inventoryMapper, never()).confirmDeduct(anyLong(), anyInt());
        }
    }

    @Nested
    @DisplayName("initStock")
    class InitStockTests {

        @Test
        @DisplayName("should insert new inventory when not existing")
        void initStock_newInventory_insertsSuccessfully() throws InterruptedException {
            mockLockAcquired();
            when(inventoryMapper.selectOne(any())).thenReturn(null);
            when(inventoryMapper.insert(any(Inventory.class))).thenReturn(1);
            mockExecuteWithoutResult();

            inventoryService.initStock(SKU_ID, PRODUCT_ID, 100);

            verify(inventoryMapper).insert(any(Inventory.class));
            verify(valueOperations).set(eq("inventory:product:" + SKU_ID), eq("100"), any());
            verify(rLock).unlock();
        }

        @Test
        @DisplayName("TRADE-02：已有库存记录 → 初始化拒绝（不覆盖 available/reserved，不写缓存）")
        void initStock_existingInventory_rejectedNoOverwrite() throws InterruptedException {
            mockLockAcquired();

            Inventory existing = new Inventory();
            existing.setId(1L);
            existing.setSkuId(SKU_ID);
            existing.setAvailable(50);
            existing.setReserved(7);

            when(inventoryMapper.selectOne(any())).thenReturn(existing);
            mockExecuteWithoutResult();

            assertThatThrownBy(() -> inventoryService.initStock(SKU_ID, PRODUCT_ID, 100))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                            .isEqualTo("INVENTORY_ALREADY_INITIALIZED"));

            // 原行为 setAvailable(100) 覆盖被禁止：不改行、不写 Redis、释放锁
            assertThat(existing.getAvailable()).isEqualTo(50);
            assertThat(existing.getReserved()).isEqualTo(7);
            verify(inventoryMapper, never()).updateById(any(Inventory.class));
            verify(valueOperations, never()).set(any(), any(), any());
            verify(rLock).unlock();
        }

        @Test
        @DisplayName("TRADE-02：并发初始化竞态（uk 冲突）→ 同码拒绝，不裸露冲突异常")
        void initStock_concurrentRace_rejectedAsAlreadyInitialized() throws InterruptedException {
            mockLockAcquired();
            when(inventoryMapper.selectOne(any())).thenReturn(null);
            when(inventoryMapper.insert(any(Inventory.class)))
                    .thenThrow(new org.springframework.dao.DuplicateKeyException("uk_sku_id"));
            mockExecuteWithoutResult();

            assertThatThrownBy(() -> inventoryService.initStock(SKU_ID, PRODUCT_ID, 100))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                            .isEqualTo("INVENTORY_ALREADY_INITIALIZED"));
            verify(valueOperations, never()).set(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("getInventory")
    class GetInventoryTests {

        @Test
        @DisplayName("should throw when inventory not found")
        void getInventory_notFound_throwsException() {
            when(inventoryMapper.selectOne(any())).thenReturn(null);

            assertThatThrownBy(() -> inventoryService.getInventory(SKU_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("INVENTORY_NOT_FOUND"));
        }

        @Test
        @DisplayName("should return inventory and cache to Redis when not cached")
        void getInventory_notCached_cachesToRedis() {
            Inventory inventory = new Inventory();
            inventory.setId(1L);
            inventory.setSkuId(SKU_ID);
            inventory.setAvailable(100);

            when(inventoryMapper.selectOne(any())).thenReturn(inventory);
            when(valueOperations.get("inventory:product:" + SKU_ID)).thenReturn(null);
            when(inventoryConverter.toDTO(inventory)).thenReturn(
                    new InventoryDTO(1L, PRODUCT_ID, SKU_ID, 100, 0, null)
            );

            InventoryDTO result = inventoryService.getInventory(SKU_ID);

            assertThat(result.available()).isEqualTo(100);
            verify(valueOperations).set(eq("inventory:product:" + SKU_ID), eq("100"), any());
        }
    }

    // ==================== TRADE-02：盘点调整单 ====================

    private com.cloudmart.inventory.entity.InventoryAdjustment existingAdjustment(int delta) {
        com.cloudmart.inventory.entity.InventoryAdjustment row =
                new com.cloudmart.inventory.entity.InventoryAdjustment();
        row.setId(9L);
        row.setAdjustNo("ADJ2026101700001");
        row.setSkuId(SKU_ID);
        row.setProductId(7L);
        row.setDelta(delta);
        row.setReason("月度盘点");
        row.setRequestKey("req-key-1");
        row.setExpectedVersion(3);
        row.setBeforeAvailable(100);
        row.setAfterAvailable(100 + delta);
        row.setBeforeReserved(5);
        row.setAfterReserved(5);
        row.setOperatorId(9001L);
        return row;
    }

    private Inventory inventoryRow(int available, int reserved, int version) {
        Inventory row = new Inventory();
        row.setId(1L);
        row.setSkuId(SKU_ID);
        row.setProductId(7L);
        row.setAvailable(available);
        row.setReserved(reserved);
        row.setVersion(version);
        return row;
    }

    private com.cloudmart.inventory.dto.InventoryAdjustRequest adjustRequest(
            int delta, int expectedVersion, String requestKey) {
        return new com.cloudmart.inventory.dto.InventoryAdjustRequest(
                SKU_ID, delta, "月度盘点", expectedVersion, requestKey);
    }

    @Nested
    @DisplayName("TRADE-02 盘点调整单")
    class AdjustStock {

        @BeforeEach
        void enableTransaction() {
            when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
                org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
                return callback.doInTransaction(null);
            });
        }

        @Test
        @DisplayName("幂等：同 requestKey 同参返回原单，不重复入账")
        void adjust_sameKeySameParams_returnsOriginal() {
            when(adjustmentMapper.selectByRequestKey("req-key-1")).thenReturn(existingAdjustment(5));

            var vo = inventoryService.adjustStock(adjustRequest(5, 3, "req-key-1"), 9001L);

            assertThat(vo.adjustNo()).isEqualTo("ADJ2026101700001");
            assertThat(vo.afterAvailable()).isEqualTo(105);
            verify(adjustmentMapper, never()).casAdjust(any(), anyInt(), anyInt());
            verify(adjustmentMapper, never()).insert(any(com.cloudmart.inventory.entity.InventoryAdjustment.class));
        }

        @Test
        @DisplayName("幂等键被异参占用 → 显式拒绝（防误复用请求键）")
        void adjust_sameKeyDifferentParams_rejected() {
            when(adjustmentMapper.selectByRequestKey("req-key-1")).thenReturn(existingAdjustment(5));

            assertThatThrownBy(() -> inventoryService.adjustStock(adjustRequest(-3, 3, "req-key-1"), 9001L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo("INVENTORY_REQUEST_KEY_CONFLICT");
        }

        @Test
        @DisplayName("成功：CAS 按 expectedVersion 生效，调整单/流水/缓存齐备，前后值正确")
        void adjust_success_recordsAll() {
            when(adjustmentMapper.selectByRequestKey("req-key-1")).thenReturn(null);
            // 第一次 selectOne：事务内读当前行（100/版本 3）；第二次：提交后缓存刷新读到新值 105
            when(inventoryMapper.selectOne(any()))
                    .thenReturn(inventoryRow(100, 5, 3))
                    .thenReturn(inventoryRow(105, 5, 4));
            when(adjustmentMapper.casAdjust(SKU_ID, 5, 3)).thenReturn(1);
            when(adjustmentMapper.countByAdjustNoPrefix(anyString())).thenReturn(0L);
            when(adjustmentMapper.selectCount(any())).thenReturn(0L);
            when(adjustmentMapper.insert(any(com.cloudmart.inventory.entity.InventoryAdjustment.class))).thenReturn(1);
            when(inventoryLogMapper.insert(any(InventoryLog.class))).thenReturn(1);

            var vo = inventoryService.adjustStock(adjustRequest(5, 3, "req-key-1"), 9001L);

            assertThat(vo.beforeAvailable()).isEqualTo(100);
            assertThat(vo.afterAvailable()).isEqualTo(105);
            assertThat(vo.beforeReserved()).isEqualTo(5);
            assertThat(vo.afterReserved()).isEqualTo(5);
            assertThat(vo.operatorId()).isEqualTo(9001L);
            // 预占只读：缓存刷新为新可用值
            verify(valueOperations).set(eq("inventory:product:" + SKU_ID), eq("105"), any());
        }

        @Test
        @DisplayName("版本冲突：expectedVersion 过期 → 拒绝并提示当前版本")
        void adjust_versionConflict_rejected() {
            when(adjustmentMapper.selectByRequestKey("req-key-1")).thenReturn(null);
            when(inventoryMapper.selectOne(any())).thenReturn(inventoryRow(100, 5, 7));

            assertThatThrownBy(() -> inventoryService.adjustStock(adjustRequest(5, 3, "req-key-1"), 9001L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", "INVENTORY_VERSION_CONFLICT")
                    .hasMessageContaining("当前版本 7");
            verify(adjustmentMapper, never()).casAdjust(any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("负库存边界：调整后可用为负 → 拒绝")
        void adjust_wouldGoNegative_rejected() {
            when(adjustmentMapper.selectByRequestKey("req-key-1")).thenReturn(null);
            when(inventoryMapper.selectOne(any())).thenReturn(inventoryRow(2, 5, 3));

            assertThatThrownBy(() -> inventoryService.adjustStock(adjustRequest(-3, 3, "req-key-1"), 9001L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", "INVENTORY_ADJUST_INVALID");
        }

        @Test
        @DisplayName("未初始化 SKU → 提示先走初始化")
        void adjust_notInitialized_rejected() {
            when(adjustmentMapper.selectByRequestKey("req-key-1")).thenReturn(null);
            when(inventoryMapper.selectOne(any())).thenReturn(null);

            assertThatThrownBy(() -> inventoryService.adjustStock(adjustRequest(5, 0, "req-key-1"), 9001L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", "INVENTORY_NOT_FOUND");
        }

        @Test
        @DisplayName("缺操作人身份 → 显式拒绝（调整必须可追溯到人）")
        void adjust_missingOperator_rejected() {
            assertThatThrownBy(() -> inventoryService.adjustStock(adjustRequest(5, 3, "req-key-1"), null))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", "INVENTORY_OPERATOR_REQUIRED");
        }

        @Test
        @DisplayName("缓存刷新失败：不回滚已提交事实，降级为失效（读路径重建）")
        void adjust_cacheFailure_degradesToEvict() {
            when(adjustmentMapper.selectByRequestKey("req-key-1")).thenReturn(null);
            when(inventoryMapper.selectOne(any())).thenReturn(inventoryRow(100, 5, 3));
            when(adjustmentMapper.casAdjust(SKU_ID, 5, 3)).thenReturn(1);
            when(adjustmentMapper.countByAdjustNoPrefix(anyString())).thenReturn(0L);
            when(adjustmentMapper.selectCount(any())).thenReturn(0L);
            when(adjustmentMapper.insert(any(com.cloudmart.inventory.entity.InventoryAdjustment.class))).thenReturn(1);
            when(inventoryLogMapper.insert(any(InventoryLog.class))).thenReturn(1);
            // 缓存故障：从 opsForValue 入口抛出（覆盖 set 调用），降级路径走 redisTemplate.delete
            when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("redis down"));

            var vo = inventoryService.adjustStock(adjustRequest(5, 3, "req-key-1"), 9001L);

            assertThat(vo.afterAvailable()).isEqualTo(105);
            verify(redisTemplate).delete("inventory:product:" + SKU_ID);
        }
    }
}
