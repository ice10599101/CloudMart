package com.cloudmart.product.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.product.converter.ProductConverter;
import com.cloudmart.product.dto.ReviewDTO;
import com.cloudmart.product.dto.ReviewStatsDTO;
import com.cloudmart.product.service.ReviewService;
import com.cloudmart.product.vo.ReviewStatsVO;
import com.cloudmart.product.vo.ReviewVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminReviewControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ReviewService reviewService = Mockito.mock(ReviewService.class);
    private final ProductConverter productConverter = Mockito.mock(ProductConverter.class);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminReviewController(reviewService, productConverter))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private ReviewDTO buildReviewDTO() {
        return new ReviewDTO(1L, 10L, 1L, "user***", null, 100L, 200L,
                "颜色:红色", 5, "非常好的商品", List.of("img1.jpg"), 1, LocalDateTime.now());
    }

    private ReviewVO buildReviewVO() {
        return new ReviewVO(1L, "user***", 5, "非常好的商品", List.of("img1.jpg"), 1, LocalDateTime.now());
    }

    @Nested
    @DisplayName("GET /admin/reviews - 评价列表")
    class ListReviews {

        @Test
        @DisplayName("分页查询评价列表成功")
        void shouldReturnPagedReviews() throws Exception {
            ReviewDTO dto = buildReviewDTO();
            ReviewVO vo = buildReviewVO();
            Page<ReviewDTO> page = new Page<>(1, 20, 1L);
            page.setRecords(List.of(dto));
            given(reviewService.listReviewsForAdmin(null, null, 1, 20)).willReturn(page);
            given(productConverter.reviewDtoListToVOList(List.of(dto))).willReturn(List.of(vo));

            mockMvc.perform(get("/admin/reviews"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.records").isArray())
                    .andExpect(jsonPath("$.data.records[0].id").value(1))
                    .andExpect(jsonPath("$.data.total").value(1))
                    .andExpect(jsonPath("$.data.page").value(1));
        }

        @Test
        @DisplayName("按商品ID筛选评价成功")
        void shouldReturnFilteredReviews() throws Exception {
            ReviewDTO dto = buildReviewDTO();
            ReviewVO vo = buildReviewVO();
            Page<ReviewDTO> page = new Page<>(1, 20, 1L);
            page.setRecords(List.of(dto));
            given(reviewService.listReviewsForAdmin(10L, null, 1, 20)).willReturn(page);
            given(productConverter.reviewDtoListToVOList(List.of(dto))).willReturn(List.of(vo));

            mockMvc.perform(get("/admin/reviews")
                            .param("productId", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.records").isArray());
        }
    }

    @Nested
    @DisplayName("GET /admin/reviews/{id} - 评价详情")
    class GetReview {

        @Test
        @DisplayName("查询评价详情成功")
        void shouldReturnReviewDetail() throws Exception {
            ReviewDTO dto = buildReviewDTO();
            ReviewVO vo = buildReviewVO();
            given(reviewService.getReviewById(1L)).willReturn(dto);
            given(productConverter.reviewDtoToVO(dto)).willReturn(vo);

            mockMvc.perform(get("/admin/reviews/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.rating").value(5));
        }
    }

    @Nested
    @DisplayName("PUT /admin/reviews/{id}/status - 更新评价状态")
    class UpdateReviewStatus {

        @Test
        @DisplayName("更新评价状态成功")
        void shouldUpdateReviewStatus() throws Exception {
            willDoNothing().given(reviewService).updateReviewStatus(1L, 0);

            mockMvc.perform(put("/admin/reviews/1/status")
                            .param("status", "0"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("DELETE /admin/reviews/{id} - 删除评价")
    class DeleteReview {

        @Test
        @DisplayName("删除评价成功")
        void shouldDeleteReview() throws Exception {
            willDoNothing().given(reviewService).deleteReview(1L);

            mockMvc.perform(delete("/admin/reviews/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("GET /admin/reviews/stats/{productId} - 评价统计")
    class GetReviewStats {

        @Test
        @DisplayName("查询商品评价统计成功")
        void shouldReturnReviewStats() throws Exception {
            ReviewStatsDTO statsDTO = new ReviewStatsDTO(10L, new BigDecimal("4.5"), 20, 10, 5, 3, 1, 1);
            ReviewStatsVO statsVO = new ReviewStatsVO(new BigDecimal("4.5"), 20,
                    java.util.Map.of(5, 10, 4, 5, 3, 3, 2, 1, 1, 1));
            given(reviewService.getReviewStats(10L)).willReturn(statsDTO);
            given(productConverter.reviewStatsDtoToVO(statsDTO)).willReturn(statsVO);

            mockMvc.perform(get("/admin/reviews/stats/10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.averageRating").value(4.5))
                    .andExpect(jsonPath("$.data.totalCount").value(20));
        }
    }
}
