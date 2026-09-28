package com.cloudmart.cart.service.impl;

import com.cloudmart.cart.dto.AddCartItemRequest;
import com.cloudmart.cart.dto.CartDTO;
import com.cloudmart.cart.dto.CartItemDTO;
import com.cloudmart.cart.dto.UpdateCartItemRequest;
import com.cloudmart.cart.entity.CartItem;
import com.cloudmart.cart.feign.ProductFeignClient;
import com.cloudmart.cart.feign.ProductFeignClient.ProductInfo;
import com.cloudmart.cart.feign.ProductFeignClient.SkuInfo;
import com.cloudmart.cart.repository.CartItemMapper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class CartServiceImplTest {

    private StringRedisTemplate redisTemplate;
    private CartItemMapper cartItemMapper;
    private ObjectMapper objectMapper;
    private ProductFeignClient productFeignClient;
    private CartServiceImpl cartService;
    private HashOperations<String, Object, Object> hashOperations;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        cartItemMapper = mock(CartItemMapper.class);
        objectMapper = new ObjectMapper();
        productFeignClient = mock(ProductFeignClient.class);
        hashOperations = mock(HashOperations.class);

        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        cartService = new CartServiceImpl(redisTemplate, cartItemMapper, objectMapper, productFeignClient);
    }

    private CartItemDTO buildCartItemDTO(Long skuId, int quantity, int checked) {
        return new CartItemDTO(null, 1L, 100L, skuId, quantity, checked, "Phone", "phone.jpg", "Red", new BigDecimal("999.00"));
    }

    @Nested
    @DisplayName("getCart")
    class GetCartTests {

        @Test
        @DisplayName("empty cart -> returns empty CartDTO")
        void getCart_Empty_ShouldReturnEmpty() {
            when(hashOperations.entries("cart:user:1")).thenReturn(Map.of());

            CartDTO result = cartService.getCart(1L);

            assertThat(result.items()).isEmpty();
            assertThat(result.totalQuantity()).isEqualTo(0);
            assertThat(result.totalPrice()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("cart with checked items -> calculates total correctly")
        void getCart_WithCheckedItems_ShouldCalculateTotal() throws Exception {
            CartItemDTO item1 = buildCartItemDTO(10L, 2, 1);
            CartItemDTO item2 = buildCartItemDTO(20L, 1, 0);

            Map<Object, Object> entries = Map.of(
                    "10", objectMapper.writeValueAsString(item1),
                    "20", objectMapper.writeValueAsString(item2)
            );
            when(hashOperations.entries("cart:user:1")).thenReturn(entries);

            CartDTO result = cartService.getCart(1L);

            assertThat(result.items()).hasSize(2);
            assertThat(result.totalQuantity()).isEqualTo(2);
            assertThat(result.totalPrice()).isEqualByComparingTo(new BigDecimal("1998.00"));
        }
    }

    @Nested
    @DisplayName("addItem")
    class AddItemTests {

        @Test
        @DisplayName("new item -> adds to cart")
        void addItem_NewItem_ShouldAdd() {
            when(hashOperations.get("cart:user:1", "10")).thenReturn(null);

            SkuInfo skuInfo = new SkuInfo(10L, "SKU001", "Red", new BigDecimal("999.00"), new BigDecimal("1299.00"), 100, "phone.jpg", 1);
            ProductInfo productInfo = new ProductInfo(100L, "Phone", "main.jpg", List.of(skuInfo));
            when(productFeignClient.getProductById(100L)).thenReturn(ApiResponse.ok(productInfo));
            // CART-01：DB 权威——upsert 后回查返回新行
            CartItem entity = new CartItem();
            entity.setId(1L);
            entity.setUserId(1L);
            entity.setProductId(100L);
            entity.setSkuId(10L);
            entity.setQuantity(1);
            entity.setChecked(1);
            when(cartItemMapper.upsertIncrement(1L, 100L, 10L, 1)).thenReturn(1);
            when(cartItemMapper.findByUserAndSku(1L, 10L)).thenReturn(entity);

            AddCartItemRequest request = new AddCartItemRequest(100L, 10L, 1);
            CartItemDTO result = cartService.addItem(1L, request);

            assertThat(result).isNotNull();
            assertThat(result.quantity()).isEqualTo(1);
            assertThat(result.checked()).isEqualTo(1);
            assertThat(result.productName()).isEqualTo("Phone");
            verify(hashOperations).put(eq("cart:user:1"), eq("10"), anyString());
        }

        @Test
        @DisplayName("existing item -> increments quantity")
        void addItem_ExistingItem_ShouldIncrementQuantity() {
            // CART-01：增量由 DB 原子 upsert 完成（并发不丢），服务读回权威行
            SkuInfo skuInfo = new SkuInfo(10L, "SKU001", "Red", new BigDecimal("999.00"), new BigDecimal("1299.00"), 100, "phone.jpg", 1);
            ProductInfo productInfo = new ProductInfo(100L, "Phone", "main.jpg", List.of(skuInfo));
            when(productFeignClient.getProductById(100L)).thenReturn(ApiResponse.ok(productInfo));
            CartItem merged = new CartItem();
            merged.setId(1L);
            merged.setUserId(1L);
            merged.setProductId(100L);
            merged.setSkuId(10L);
            merged.setQuantity(5);
            merged.setChecked(1);
            when(cartItemMapper.upsertIncrement(1L, 100L, 10L, 3)).thenReturn(1);
            when(cartItemMapper.findByUserAndSku(1L, 10L)).thenReturn(merged);

            AddCartItemRequest request = new AddCartItemRequest(100L, 10L, 3);
            CartItemDTO result = cartService.addItem(1L, request);

            assertThat(result.quantity()).isEqualTo(5);
            verify(cartItemMapper).upsertIncrement(1L, 100L, 10L, 3);
        }

        @Test
        @DisplayName("CART-01：SKU 与商品不匹配拒绝加购")
        void addItem_SkuMismatch_ShouldReject() {
            SkuInfo otherSku = new SkuInfo(20L, "SKU002", "Blue", new BigDecimal("999.00"), new BigDecimal("1299.00"), 100, "phone.jpg", 1);
            ProductInfo productInfo = new ProductInfo(100L, "Phone", "main.jpg", List.of(otherSku));
            when(productFeignClient.getProductById(100L)).thenReturn(ApiResponse.ok(productInfo));

            assertThatThrownBy(() -> cartService.addItem(1L, new AddCartItemRequest(100L, 10L, 1)))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("CART_SKU_MISMATCH"));
            verify(cartItemMapper, never()).upsertIncrement(anyLong(), anyLong(), anyLong(), org.mockito.ArgumentMatchers.anyInt());
        }
    }

    @Nested
    @DisplayName("updateItem")
    class UpdateItemTests {

        @Test
        @DisplayName("existing item -> updates quantity and checked")
        void updateItem_ExistingItem_ShouldUpdate() throws Exception {
            // CART-01：基准取 DB 权威行，缓存仅用于补展示字段
            CartItem entity = new CartItem();
            entity.setId(1L);
            entity.setUserId(1L);
            entity.setProductId(100L);
            entity.setSkuId(10L);
            entity.setQuantity(2);
            entity.setChecked(1);
            when(cartItemMapper.findByUserAndSku(1L, 10L)).thenReturn(entity);
            when(cartItemMapper.updateById(any(CartItem.class))).thenReturn(1);
            CartItemDTO cached = buildCartItemDTO(10L, 2, 1);
            when(hashOperations.get("cart:user:1", "10")).thenReturn(objectMapper.writeValueAsString(cached));

            UpdateCartItemRequest request = new UpdateCartItemRequest(5, 0);
            CartItemDTO result = cartService.updateItem(1L, 10L, request);

            assertThat(result.quantity()).isEqualTo(5);
            assertThat(result.checked()).isEqualTo(0);
            verify(cartItemMapper).updateById(any(CartItem.class));
            verify(hashOperations).put(eq("cart:user:1"), eq("10"), anyString());
        }

        @Test
        @DisplayName("non-existing item -> throws CART_ITEM_NOT_FOUND")
        void updateItem_NonExisting_ShouldThrowBusinessException() {
            // CART-01：以 DB 为准判断存在性
            when(cartItemMapper.findByUserAndSku(1L, 999L)).thenReturn(null);

            UpdateCartItemRequest request = new UpdateCartItemRequest(1, 1);

            assertThatThrownBy(() -> cartService.updateItem(1L, 999L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("CART_ITEM_NOT_FOUND"));
        }

        @Test
        @DisplayName("null fields -> keeps existing values")
        void updateItem_NullFields_ShouldKeepExisting() throws Exception {
            CartItem entity = new CartItem();
            entity.setId(1L);
            entity.setUserId(1L);
            entity.setProductId(100L);
            entity.setSkuId(10L);
            entity.setQuantity(2);
            entity.setChecked(1);
            when(cartItemMapper.findByUserAndSku(1L, 10L)).thenReturn(entity);
            when(cartItemMapper.updateById(any(CartItem.class))).thenReturn(1);
            CartItemDTO cached = buildCartItemDTO(10L, 2, 1);
            when(hashOperations.get("cart:user:1", "10")).thenReturn(objectMapper.writeValueAsString(cached));

            UpdateCartItemRequest request = new UpdateCartItemRequest(null, null);
            CartItemDTO result = cartService.updateItem(1L, 10L, request);

            assertThat(result.quantity()).isEqualTo(2);
            assertThat(result.checked()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("removeItem")
    class RemoveItemTests {

        @Test
        @DisplayName("existing item -> removes from cart")
        void removeItem_ExistingItem_ShouldRemove() {
            // CART-01：删除权威行 + 同步缓存
            when(cartItemMapper.delete(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(1);

            cartService.removeItem(1L, 10L);

            verify(hashOperations).delete("cart:user:1", "10");
        }

        @Test
        @DisplayName("non-existing item -> throws CART_ITEM_NOT_FOUND")
        void removeItem_NonExisting_ShouldThrowBusinessException() {
            when(cartItemMapper.delete(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(0);

            assertThatThrownBy(() -> cartService.removeItem(1L, 999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("CART_ITEM_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("clearCart")
    class ClearCartTests {

        @Test
        @DisplayName("clears the entire cart")
        void clearCart_ShouldDeleteKey() {
            cartService.clearCart(1L);

            verify(redisTemplate).delete("cart:user:1");
        }
    }
}
