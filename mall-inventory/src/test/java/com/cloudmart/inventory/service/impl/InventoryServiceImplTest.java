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
        redisTemplate = mock(StringRedisTemplate.class);
        deductInventoryScript = mock(DefaultRedisScript.class);
        redissonClient = mock(RedissonClient.class);
        transactionTemplate = mock(TransactionTemplate.class);
        rLock = mock(RLock.class);
        valueOperations = mock(ValueOperations.class);

        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        inventoryService = new InventoryServiceImpl(
                inventoryMapper, inventoryLogMapper, inventoryConverter,
                redisTemplate, deductInventoryScript, redissonClient, transactionTemplate
        );
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
        @DisplayName("should release stock successfully")
        void releaseStock_success() throws InterruptedException {
            ReleaseRequest request = new ReleaseRequest(SKU_ID, 5, ORDER_ID);
            mockLockAcquired();
            when(inventoryMapper.releaseStock(SKU_ID, 5)).thenReturn(1);
            mockExecuteWithoutResult();

            inventoryService.releaseStock(request);

            verify(inventoryMapper).releaseStock(SKU_ID, 5);
            verify(valueOperations).increment("inventory:product:" + SKU_ID, 5);
            verify(inventoryLogMapper).insert(any(InventoryLog.class));
            verify(rLock).unlock();
        }

        @Test
        @DisplayName("should throw when inventory not found for release")
        void releaseStock_notFound_throwsException() throws InterruptedException {
            ReleaseRequest request = new ReleaseRequest(SKU_ID, 5, ORDER_ID);
            mockLockAcquired();
            when(inventoryMapper.releaseStock(SKU_ID, 5)).thenReturn(0);
            mockExecuteWithoutResult();

            assertThatThrownBy(() -> inventoryService.releaseStock(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("INVENTORY_NOT_FOUND"));
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
        @DisplayName("should confirm deduction successfully")
        void confirmDeduct_success() throws InterruptedException {
            mockLockAcquired();
            when(inventoryMapper.confirmDeduct(SKU_ID, 5)).thenReturn(1);
            mockExecuteWithoutResult();

            inventoryService.confirmDeduct(SKU_ID, 5, ORDER_ID);

            verify(inventoryMapper).confirmDeduct(SKU_ID, 5);
            verify(inventoryLogMapper).insert(any(InventoryLog.class));
            verify(rLock).unlock();
        }

        @Test
        @DisplayName("should throw when confirm fails due to insufficient reserved stock")
        void confirmDeduct_insufficientReserved_throwsException() throws InterruptedException {
            mockLockAcquired();
            when(inventoryMapper.confirmDeduct(SKU_ID, 5)).thenReturn(0);
            mockExecuteWithoutResult();

            assertThatThrownBy(() -> inventoryService.confirmDeduct(SKU_ID, 5, ORDER_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("INVENTORY_CONFIRM_FAILED"));
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
        @DisplayName("should update existing inventory when already initialized")
        void initStock_existingInventory_updatesSuccessfully() throws InterruptedException {
            mockLockAcquired();

            Inventory existing = new Inventory();
            existing.setId(1L);
            existing.setSkuId(SKU_ID);
            existing.setAvailable(50);

            when(inventoryMapper.selectOne(any())).thenReturn(existing);
            when(inventoryMapper.updateById(existing)).thenReturn(1);
            mockExecuteWithoutResult();

            inventoryService.initStock(SKU_ID, PRODUCT_ID, 100);

            assertThat(existing.getAvailable()).isEqualTo(100);
            verify(inventoryMapper).updateById(existing);
            verify(valueOperations).set(eq("inventory:product:" + SKU_ID), eq("100"), any());
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
                    new InventoryDTO(1L, PRODUCT_ID, SKU_ID, 100, 0)
            );

            InventoryDTO result = inventoryService.getInventory(SKU_ID);

            assertThat(result.available()).isEqualTo(100);
            verify(valueOperations).set(eq("inventory:product:" + SKU_ID), eq("100"), any());
        }
    }
}
