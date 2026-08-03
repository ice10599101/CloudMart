package com.cloudmart.product.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.product.converter.ProductConverter;
import com.cloudmart.product.dto.ReviewDTO;
import com.cloudmart.product.dto.ReviewStatsDTO;
import com.cloudmart.product.service.ReviewService;
import com.cloudmart.product.vo.ReviewStatsVO;
import com.cloudmart.product.vo.ReviewVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReviewControllerTest {

    private MockMvc mockMvc;

    private final ReviewService reviewService = Mockito.mock(ReviewService.class);
    private final ProductConverter productConverter = Mockito.mock(ProductConverter.class);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ReviewController(reviewService, productConverter))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getProductReviews_ShouldReturn200AndEnvelope() throws Exception {
        ReviewDTO dto = new ReviewDTO(
                1L, 3001L, 1001L, "用户****1001", null,
                2001L, 4001L, "颜色:红色", 5, "非常好",
                List.of(), 1, LocalDateTime.of(2026, 5, 13, 10, 0));

        Page<ReviewDTO> page = new Page<>(1, 10, 1L);
        page.setRecords(List.of(dto));

        given(reviewService.getProductReviews(3001L, 1, 10)).willReturn(page);

        ReviewVO vo = new ReviewVO(1L, "用户****1001", 5, "非常好", List.of(),
                1, LocalDateTime.of(2026, 5, 13, 10, 0));
        given(productConverter.reviewDtoListToVOList(List.of(dto))).willReturn(List.of(vo));

        mockMvc.perform(get("/reviews/product/3001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].rating").value(5))
                .andExpect(jsonPath("$.data[0].content").value("非常好"))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.pageSize").value(10))
                .andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    void getReviewStats_ShouldReturn200AndStats() throws Exception {
        ReviewStatsDTO statsDTO = new ReviewStatsDTO(
                3001L, BigDecimal.valueOf(4.5), 10, 5, 3, 1, 1, 0);

        given(reviewService.getReviewStats(3001L)).willReturn(statsDTO);

        ReviewStatsVO statsVO = new ReviewStatsVO(
                BigDecimal.valueOf(4.5), 10, Map.of(5, 5, 4, 3, 3, 1, 2, 1, 1, 0));
        given(productConverter.reviewStatsDtoToVO(statsDTO)).willReturn(statsVO);

        mockMvc.perform(get("/reviews/stats/3001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.averageRating").value(4.5))
                .andExpect(jsonPath("$.data.totalCount").value(10))
                .andExpect(jsonPath("$.data.distribution").isMap())
                .andExpect(jsonPath("$.data.distribution.5").value(5))
                .andExpect(jsonPath("$.data.distribution.4").value(3));
    }
}
