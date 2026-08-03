package com.cloudmart.cart.controller;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.cart.converter.CartConverter;
import com.cloudmart.cart.dto.CartDTO;
import com.cloudmart.cart.dto.CartItemDTO;
import com.cloudmart.cart.service.CartService;
import com.cloudmart.cart.vo.CartItemVO;
import com.cloudmart.cart.vo.CartVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminCartControllerTest {

    private MockMvc mockMvc;

    private final CartService cartService = Mockito.mock(CartService.class);
    private final CartConverter cartConverter = Mockito.mock(CartConverter.class);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminCartController(cartService, cartConverter))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("查询用户购物车 - 成功返回信封格式")
    void getCartByUserId_ShouldReturn200WithEnvelope() throws Exception {
        CartItemDTO itemDto = new CartItemDTO(1L, 1L, 100L, 200L, 2, 1, "商品A", "img.jpg", "红色", new BigDecimal("99.00"));
        CartDTO dto = new CartDTO(List.of(itemDto), 2, new BigDecimal("198.00"));

        given(cartService.getCart(1L)).willReturn(dto);

        CartItemVO itemVO = new CartItemVO(1L, 100L, "商品A", "img.jpg", 200L, "红色", new BigDecimal("99.00"), 2, new BigDecimal("198.00"), true);
        CartVO vo = new CartVO(List.of(itemVO), 2, new BigDecimal("198.00"));
        given(cartConverter.cartDtoToVOWithItems(dto)).willReturn(vo);

        mockMvc.perform(get("/admin/cart/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    @DisplayName("删除购物车商品 - 成功返回信封格式")
    void removeCartItem_ShouldReturn200WithEnvelope() throws Exception {
        willDoNothing().given(cartService).removeItem(1L, 200L);

        mockMvc.perform(delete("/admin/cart/1/items/200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("清空用户购物车 - 成功返回信封格式")
    void clearCart_ShouldReturn200WithEnvelope() throws Exception {
        willDoNothing().given(cartService).clearCart(1L);

        mockMvc.perform(delete("/admin/cart/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("查询不存在用户的购物车 - 返回错误信封")
    void getCartByUserId_WhenUserNotFound_ShouldReturnErrorEnvelope() throws Exception {
        given(cartService.getCart(999L))
                .willThrow(new BusinessException("USER_NOT_FOUND", "用户不存在"));

        mockMvc.perform(get("/admin/cart/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("用户不存在"));
    }
}
