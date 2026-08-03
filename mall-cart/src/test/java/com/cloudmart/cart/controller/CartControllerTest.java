package com.cloudmart.cart.controller;

import com.cloudmart.cart.converter.CartConverter;
import com.cloudmart.cart.dto.*;
import com.cloudmart.cart.service.CartService;
import com.cloudmart.cart.vo.CartItemVO;
import com.cloudmart.cart.vo.CartVO;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CartControllerTest {

    private CartService cartService;
    private CartConverter cartConverter;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        cartService = mock(CartService.class);
        cartConverter = mock(CartConverter.class);
        CartController controller = new CartController(cartService, cartConverter);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private CartDTO buildCartDTO() {
        CartItemDTO item = new CartItemDTO(null, 1L, 100L, 10L, 2, 1, "Phone", "phone.jpg", "Red", new BigDecimal("999.00"));
        return new CartDTO(List.of(item), 2, new BigDecimal("1998.00"));
    }

    private CartVO buildCartVO() {
        CartItemVO itemVO = new CartItemVO(null, 100L, "Phone", "phone.jpg", 10L, "Red", new BigDecimal("999.00"), 2, new BigDecimal("1998.00"), true);
        return new CartVO(List.of(itemVO), 2, new BigDecimal("1998.00"));
    }

    @Nested
    @DisplayName("GET /")
    class GetCartTests {

        @Test
        @DisplayName("returns cart with items")
        void getCart_ShouldReturnCart() throws Exception {
            CartDTO dto = buildCartDTO();
            when(cartService.getCart(1L)).thenReturn(dto);
            when(cartConverter.cartDtoToVOWithItems(dto)).thenReturn(buildCartVO());

            mockMvc.perform(get("/")
                            .header("X-User-Id", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.totalCount").value(2))
                    .andExpect(jsonPath("$.data.items").isArray());
        }
    }

    @Nested
    @DisplayName("POST /items")
    class AddItemTests {

        @Test
        @DisplayName("valid request -> adds item to cart")
        void addItem_ValidRequest_ShouldAdd() throws Exception {
            CartItemDTO itemDTO = new CartItemDTO(null, 1L, 100L, 10L, 1, 1, "Phone", "phone.jpg", "Red", new BigDecimal("999.00"));
            when(cartService.addItem(eq(1L), any(AddCartItemRequest.class))).thenReturn(itemDTO);
            when(cartConverter.cartItemDtoToVO(itemDTO)).thenReturn(
                    new CartItemVO(null, 100L, "Phone", "phone.jpg", 10L, "Red", new BigDecimal("999.00"), 1, new BigDecimal("999.00"), true));

            AddCartItemRequest request = new AddCartItemRequest(100L, 10L, 1);

            mockMvc.perform(post("/items")
                            .header("X-User-Id", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.productName").value("Phone"));
        }
    }

    @Nested
    @DisplayName("PUT /items/{skuId}")
    class UpdateItemTests {

        @Test
        @DisplayName("valid request -> updates cart item")
        void updateItem_ValidRequest_ShouldUpdate() throws Exception {
            CartItemDTO itemDTO = new CartItemDTO(null, 1L, 100L, 10L, 5, 0, "Phone", "phone.jpg", "Red", new BigDecimal("999.00"));
            when(cartService.updateItem(eq(1L), eq(10L), any(UpdateCartItemRequest.class))).thenReturn(itemDTO);
            when(cartConverter.cartItemDtoToVO(itemDTO)).thenReturn(
                    new CartItemVO(null, 100L, "Phone", "phone.jpg", 10L, "Red", new BigDecimal("999.00"), 5, new BigDecimal("4995.00"), false));

            UpdateCartItemRequest request = new UpdateCartItemRequest(5, 0);

            mockMvc.perform(put("/items/10")
                            .header("X-User-Id", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.quantity").value(5));
        }
    }

    @Nested
    @DisplayName("DELETE /items/{skuId}")
    class RemoveItemTests {

        @Test
        @DisplayName("removes item from cart")
        void removeItem_ShouldRemove() throws Exception {
            mockMvc.perform(delete("/items/10")
                            .header("X-User-Id", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(cartService).removeItem(1L, 10L);
        }
    }

    @Nested
    @DisplayName("DELETE /")
    class ClearCartTests {

        @Test
        @DisplayName("clears the entire cart")
        void clearCart_ShouldClear() throws Exception {
            mockMvc.perform(delete("/")
                            .header("X-User-Id", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(cartService).clearCart(1L);
        }
    }

    @Nested
    @DisplayName("DELETE /checked")
    class ClearCheckedItemsTests {

        @Test
        @DisplayName("clears checked items")
        void clearCheckedItems_ShouldClear() throws Exception {
            mockMvc.perform(delete("/checked")
                            .header("X-User-Id", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(cartService).clearCheckedItems(1L);
        }
    }
}
