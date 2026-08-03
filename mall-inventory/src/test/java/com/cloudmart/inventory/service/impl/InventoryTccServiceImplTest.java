package com.cloudmart.inventory.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.inventory.dto.TccDeductRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link InventoryTccServiceImpl} 单元测试。
 *
 * <p>覆盖 TCC 状态机的四个核心场景：防悬挂、幂等、空回滚、防并发重试。
 * 通过 mock {@link StringRedisTemplate} 与 {@link JdbcTemplate} 隔离真实依赖，
 * 以 Lua 脚本返回值模拟不同 TCC 状态流转。
 */
class InventoryTccServiceImplTest {

    private StringRedisTemplate redisTemplate;
    private JdbcTemplate jdbcTemplate;
    private HashOperations<String, Object, Object> hashOperations;
    private ValueOperations<String, String> valueOperations;
    private InventoryTccServiceImpl inventoryTccService;

    private static final Long SKU_ID = 1001L;
    private static final Long PRODUCT_ID = 2001L;
    private static final Long ORDER_ID = 3001L;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        hashOperations = mock(HashOperations.class);
        valueOperations = mock(ValueOperations.class);

        inventoryTccService = new InventoryTccServiceImpl(redisTemplate, jdbcTemplate);
    }

    @Nested
    @DisplayName("tryDeduct - Try 阶段")
    class TryDeductTests {

        @Test
        @DisplayName("Lua 返回 1 时应返回 xid（成功）")
        void tryDeduct_success_returnsXid() {
            TccDeductRequest request = new TccDeductRequest(SKU_ID, PRODUCT_ID, 5, ORDER_ID);
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class)))
                    .thenReturn(1L);

            String xid = inventoryTccService.tryDeduct(request);

            assertThat(xid).isNotBlank();
            verify(redisTemplate, times(1))
                    .execute(any(DefaultRedisScript.class), anyList(), any(Object[].class));
        }

        @Test
        @DisplayName("Lua 返回 0 时应抛 INSUFFICIENT_STOCK")
        void tryDeduct_insufficientStock_throwsException() {
            TccDeductRequest request = new TccDeductRequest(SKU_ID, PRODUCT_ID, 5, ORDER_ID);
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class)))
                    .thenReturn(0L);

            assertThatThrownBy(() -> inventoryTccService.tryDeduct(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("INSUFFICIENT_STOCK"));
        }

        @Test
        @DisplayName("Lua 返回 null 时应抛 INSUFFICIENT_STOCK")
        void tryDeduct_nullResult_throwsException() {
            TccDeductRequest request = new TccDeductRequest(SKU_ID, PRODUCT_ID, 5, ORDER_ID);
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class)))
                    .thenReturn(null);

            assertThatThrownBy(() -> inventoryTccService.tryDeduct(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("INSUFFICIENT_STOCK"));
        }

        @Test
        @DisplayName("Lua 返回 -1 时应抛 TCC_SUSPENDED（防悬挂）")
        void tryDeduct_suspended_throwsException() {
            TccDeductRequest request = new TccDeductRequest(SKU_ID, PRODUCT_ID, 5, ORDER_ID);
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class)))
                    .thenReturn(-1L);

            assertThatThrownBy(() -> inventoryTccService.tryDeduct(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("TCC_SUSPENDED"));
        }

        @Test
        @DisplayName("Lua 返回 -2 时应抛 TCC_DUPLICATE_TRY")
        void tryDeduct_duplicate_throwsException() {
            TccDeductRequest request = new TccDeductRequest(SKU_ID, PRODUCT_ID, 5, ORDER_ID);
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class)))
                    .thenReturn(-2L);

            assertThatThrownBy(() -> inventoryTccService.tryDeduct(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("TCC_DUPLICATE_TRY"));
        }

        @Test
        @DisplayName("orderId 为 null 时应正常执行（传空字符串给 Lua）")
        void tryDeduct_nullOrderId_success() {
            TccDeductRequest request = new TccDeductRequest(SKU_ID, PRODUCT_ID, 5, null);
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class)))
                    .thenReturn(1L);

            String xid = inventoryTccService.tryDeduct(request);

            assertThat(xid).isNotBlank();
        }
    }

    @Nested
    @DisplayName("confirmDeduct - Confirm 阶段")
    class ConfirmDeductTests {

        @Test
        @DisplayName("code=1 + DB updated=1 时应确认扣减并标记 CONFIRMED")
        void confirmDeduct_success() {
            String xid = "test-xid-123";
            // 第一次 execute=CONFIRM_LUA 返回 1L，第二次 execute=FINISH_LUA 返回 1L
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class)))
                    .thenReturn(1L, 1L);
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.get(contains("meta"), eq("quantity"))).thenReturn("5");
            when(hashOperations.get(contains("meta"), eq("skuId"))).thenReturn(SKU_ID.toString());
            when(jdbcTemplate.update(anyString(), anyInt(), anyInt(), anyLong(), anyInt()))
                    .thenReturn(1);

            boolean result = inventoryTccService.confirmDeduct(xid);

            assertThat(result).isTrue();
            verify(jdbcTemplate).update(
                    contains("UPDATE inventories"),
                    eq(5), eq(5), eq(SKU_ID), eq(5)
            );
            verify(redisTemplate, times(2))
                    .execute(any(DefaultRedisScript.class), anyList(), any(Object[].class));
            verify(redisTemplate).delete(contains("frozen:" + xid));
        }

        @Test
        @DisplayName("code=0 时应幂等返回 true，不调用 DB")
        void confirmDeduct_alreadyConfirmed_idempotent() {
            String xid = "test-xid-123";
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class)))
                    .thenReturn(0L);

            boolean result = inventoryTccService.confirmDeduct(xid);

            assertThat(result).isTrue();
            verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
            verify(hashOperations, never()).get(anyString(), any());
        }

        @Test
        @DisplayName("code=-1 时应抛 TCC_STATE_INVALID（状态异常）")
        void confirmDeduct_stateInvalid_throwsException() {
            String xid = "test-xid-123";
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class)))
                    .thenReturn(-1L);

            assertThatThrownBy(() -> inventoryTccService.confirmDeduct(xid))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("TCC_STATE_INVALID"));
            verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
        }

        @Test
        @DisplayName("DB updated=0 时应幂等返回 true（并发下已被其他 confirm 处理）")
        void confirmDeduct_dbUpdatedZero_treatAsIdempotent() {
            String xid = "test-xid-123";
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class)))
                    .thenReturn(1L, 1L);
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.get(contains("meta"), eq("quantity"))).thenReturn("5");
            when(hashOperations.get(contains("meta"), eq("skuId"))).thenReturn(SKU_ID.toString());
            when(jdbcTemplate.update(anyString(), anyInt(), anyInt(), anyLong(), anyInt()))
                    .thenReturn(0);

            boolean result = inventoryTccService.confirmDeduct(xid);

            // 不再抛异常，而是幂等返回 true
            assertThat(result).isTrue();
            // 仍标记 CONFIRMED + 清理
            verify(redisTemplate, times(2))
                    .execute(any(DefaultRedisScript.class), anyList(), any(Object[].class));
            verify(redisTemplate).delete(contains("frozen:" + xid));
        }
    }

    @Nested
    @DisplayName("cancelDeduct - Cancel 阶段")
    class CancelDeductTests {

        @Test
        @DisplayName("code=1 时应归还库存并标记 CANCELLED")
        void cancelDeduct_success() {
            String xid = "test-xid-123";
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class)))
                    .thenReturn(1L, 1L);
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.get(contains("meta"), eq("quantity"))).thenReturn("5");
            when(hashOperations.get(contains("meta"), eq("productId"))).thenReturn(PRODUCT_ID.toString());
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            boolean result = inventoryTccService.cancelDeduct(xid);

            assertThat(result).isTrue();
            verify(valueOperations).increment("inventory:product:" + PRODUCT_ID, 5);
            verify(redisTemplate, times(2))
                    .execute(any(DefaultRedisScript.class), anyList(), any(Object[].class));
            verify(redisTemplate).delete(contains("frozen:" + xid));
        }

        @Test
        @DisplayName("code=0 时应幂等返回 true，不归还库存")
        void cancelDeduct_alreadyCancelled_idempotent() {
            String xid = "test-xid-123";
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class)))
                    .thenReturn(0L);

            boolean result = inventoryTccService.cancelDeduct(xid);

            assertThat(result).isTrue();
            verify(valueOperations, never()).increment(anyString(), anyLong());
            verify(hashOperations, never()).get(anyString(), any());
        }

        @Test
        @DisplayName("code=2 时应空回滚（不归还库存，标记 CANCELLED 防悬挂）")
        void cancelDeduct_emptyRollback_marksCancelled() {
            String xid = "test-xid-123";
            // CANCEL_LUA 返回 2L（空回滚），FINISH_LUA 返回 1L
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class)))
                    .thenReturn(2L, 1L);

            boolean result = inventoryTccService.cancelDeduct(xid);

            assertThat(result).isTrue();
            // 空回滚不归还库存
            verify(valueOperations, never()).increment(anyString(), anyLong());
            verify(hashOperations, never()).get(anyString(), any());
            // 但仍调用 FINISH_LUA 标记 CANCELLED
            verify(redisTemplate, times(2))
                    .execute(any(DefaultRedisScript.class), anyList(), any(Object[].class));
        }

        @Test
        @DisplayName("code=-1 时应抛 TCC_ALREADY_CONFIRMED（已确认无法取消）")
        void cancelDeduct_alreadyConfirmed_throwsException() {
            String xid = "test-xid-123";
            when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class)))
                    .thenReturn(-1L);

            assertThatThrownBy(() -> inventoryTccService.cancelDeduct(xid))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("TCC_ALREADY_CONFIRMED"));
            verify(valueOperations, never()).increment(anyString(), anyLong());
            verify(hashOperations, never()).get(anyString(), any());
        }
    }
}
