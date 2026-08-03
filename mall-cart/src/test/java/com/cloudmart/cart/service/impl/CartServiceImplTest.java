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
        void addItem_ExistingItem_ShouldIncrementQuantity() throws Exception {
            CartItemDTO existing = buildCartItemDTO(10L, 2, 1);
            when(hashOperations.get("cart:user:1", "10")).thenReturn(objectMapper.writeValueAsString(existing));

            AddCartItemRequest request = new AddCartItemRequest(100L, 10L, 3);
            CartItemDTO result = cartService.addItem(1L, request);

            assertThat(result.quantity()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("updateItem")
    class UpdateItemTests {

        @Test
        @DisplayName("existing item -> updates quantity and checked")
        void updateItem_ExistingItem_ShouldUpdate() throws Exception {
            CartItemDTO existing = buildCartItemDTO(10L, 2, 1);
            when(hashOperations.get("cart:user:1", "10")).thenReturn(objectMapper.writeValueAsString(existing));

            UpdateCartItemRequest request = new UpdateCartItemRequest(5, 0);
            CartItemDTO result = cartService.updateItem(1L, 10L, request);

            assertThat(result.quantity()).isEqualTo(5);
            assertThat(result.checked()).isEqualTo(0);
            verify(hashOperations).put(eq("cart:user:1"), eq("10"), anyString());
        }

        @Test
        @DisplayName("non-existing item -> throws CART_ITEM_NOT_FOUND")
        void updateItem_NonExisting_ShouldThrowBusinessException() {
            when(hashOperations.get("cart:user:1", "999")).thenReturn(null);

            UpdateCartItemRequest request = new UpdateCartItemRequest(1, 1);

            assertThatThrownBy(() -> cartService.updateItem(1L, 999L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("CART_ITEM_NOT_FOUND"));
        }

        @Test
        @DisplayName("null fields -> keeps existing values")
        void updateItem_NullFields_ShouldKeepExisting() throws Exception {
            CartItemDTO existing = buildCartItemDTO(10L, 2, 1);
            when(hashOperations.get("cart:user:1", "10")).thenReturn(objectMapper.writeValueAsString(existing));

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
            when(hashOperations.get("cart:user:1", "10")).thenReturn("some-data");

            cartService.removeItem(1L, 10L);

            verify(hashOperations).delete("cart:user:1", "10");
        }

        @Test
        @DisplayName("non-existing item -> throws CART_ITEM_NOT_FOUND")
        void removeItem_NonExisting_ShouldThrowBusinessException() {
            when(hashOperations.get("cart:user:1", "999")).thenReturn(null);

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
