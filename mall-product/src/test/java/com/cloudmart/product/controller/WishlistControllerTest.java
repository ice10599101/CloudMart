package com.cloudmart.product.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.product.dto.WishlistDTO;
import com.cloudmart.product.service.WishlistService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WishlistControllerTest {

    private MockMvc mockMvc;

    private final WishlistService wishlistService = Mockito.mock(WishlistService.class);

    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2026, 5, 29, 10, 0);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new WishlistController(wishlistService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private void setSecurityContext(Long userId) {
        Authentication auth = new TestingAuthenticationToken(String.valueOf(userId), null);
        SecurityContext context = Mockito.mock(SecurityContext.class);
        given(context.getAuthentication()).willReturn(auth);
        SecurityContextHolder.setContext(context);
    }

    private void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private WishlistDTO buildWishlistDTO() {
        return new WishlistDTO(1L, 100L, "测试商品", "image.jpg",
                new BigDecimal("99.00"), "品牌A", FIXED_TIME);
    }

    @Test
    @DisplayName("POST /wishlists/{productId} - 添加收藏返回信封格式")
    void addToList_ShouldReturnSuccessEnvelope() throws Exception {
        setSecurityContext(1L);
        willDoNothing().given(wishlistService).addToList(1L, 100L);

        try {
            mockMvc.perform(post("/wishlists/100"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        } finally {
            clearSecurityContext();
        }
    }

    @Test
    @DisplayName("POST /wishlists/{productId} - 未登录返回错误信封")
    void addToList_WhenNotAuthenticated_ShouldReturnErrorEnvelope() throws Exception {
        clearSecurityContext();

        mockMvc.perform(post("/wishlists/100"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("POST /wishlists/{productId} - 重复收藏返回错误信封")
    void addToList_WhenAlreadyInWishlist_ShouldReturnErrorEnvelope() throws Exception {
        setSecurityContext(1L);
        willThrow(new BusinessException("VALIDATION_ERROR", "商品已在收藏列表中"))
                .given(wishlistService).addToList(1L, 100L);

        try {
            mockMvc.perform(post("/wishlists/100"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        } finally {
            clearSecurityContext();
        }
    }

    @Test
    @DisplayName("DELETE /wishlists/{productId} - 取消收藏返回信封格式")
    void removeFromList_ShouldReturnSuccessEnvelope() throws Exception {
        setSecurityContext(1L);
        willDoNothing().given(wishlistService).removeFromList(1L, 100L);

        try {
            mockMvc.perform(delete("/wishlists/100"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        } finally {
            clearSecurityContext();
        }
    }

    @Test
    @DisplayName("DELETE /wishlists/{productId} - 未登录返回错误信封")
    void removeFromList_WhenNotAuthenticated_ShouldReturnErrorEnvelope() throws Exception {
        clearSecurityContext();

        mockMvc.perform(delete("/wishlists/100"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /wishlists - 收藏列表返回信封格式含meta")
    void getUserWishlist_ShouldReturnSuccessEnvelopeWithMeta() throws Exception {
        setSecurityContext(1L);
        WishlistDTO dto = buildWishlistDTO();
        Page<WishlistDTO> page = new Page<>(1, 10, 1L);
        page.setRecords(List.of(dto));
        given(wishlistService.getUserWishlist(1L, 1, 10)).willReturn(page);

        try {
            mockMvc.perform(get("/wishlists")
                            .param("page", "1")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].id").value(1))
                    .andExpect(jsonPath("$.data[0].productId").value(100))
                    .andExpect(jsonPath("$.data[0].productName").value("测试商品"))
                    .andExpect(jsonPath("$.meta.page").value(1))
                    .andExpect(jsonPath("$.meta.pageSize").value(10))
                    .andExpect(jsonPath("$.meta.total").value(1));
        } finally {
            clearSecurityContext();
        }
    }

    @Test
    @DisplayName("GET /wishlists - 未登录返回错误信封")
    void getUserWishlist_WhenNotAuthenticated_ShouldReturnErrorEnvelope() throws Exception {
        clearSecurityContext();

        mockMvc.perform(get("/wishlists"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /wishlists/check/{productId} - 已收藏返回信封格式")
    void checkWishlist_WhenInWishlist_ShouldReturnTrue() throws Exception {
        setSecurityContext(1L);
        given(wishlistService.isInWishlist(1L, 100L)).willReturn(true);

        try {
            mockMvc.perform(get("/wishlists/check/100"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.isInWishlist").value(true));
        } finally {
            clearSecurityContext();
        }
    }

    @Test
    @DisplayName("GET /wishlists/check/{productId} - 未收藏返回信封格式")
    void checkWishlist_WhenNotInWishlist_ShouldReturnFalse() throws Exception {
        setSecurityContext(1L);
        given(wishlistService.isInWishlist(1L, 200L)).willReturn(false);

        try {
            mockMvc.perform(get("/wishlists/check/200"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.isInWishlist").value(false));
        } finally {
            clearSecurityContext();
        }
    }

    @Test
    @DisplayName("GET /wishlists/check/{productId} - 未登录返回错误信封")
    void checkWishlist_WhenNotAuthenticated_ShouldReturnErrorEnvelope() throws Exception {
        clearSecurityContext();

        mockMvc.perform(get("/wishlists/check/100"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
}
