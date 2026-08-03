package com.cloudmart.marketing.controller;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.marketing.converter.MarketingConverter;
import com.cloudmart.marketing.dto.CalculateDiscountRequest;
import com.cloudmart.marketing.dto.CalculateDiscountResult;
import com.cloudmart.marketing.dto.TieredPromotionDTO;
import com.cloudmart.marketing.dto.TieredRuleDTO;
import com.cloudmart.marketing.service.TieredPromotionService;
import com.cloudmart.marketing.vo.TieredPromotionVO;
import com.cloudmart.marketing.vo.TieredRuleVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TieredPromotionControllerTest {

    private MockMvc mockMvc;

    private final TieredPromotionService tieredPromotionService = Mockito.mock(TieredPromotionService.class);
    private final MarketingConverter marketingConverter = Mockito.mock(MarketingConverter.class);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TieredPromotionController(tieredPromotionService, marketingConverter))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("获取满减活动详情 - 成功返回信封格式")
    void getPromotion_ShouldReturn200WithEnvelope() throws Exception {
        TieredRuleDTO ruleDto = new TieredRuleDTO(1L, new BigDecimal("100.00"), new BigDecimal("10.00"));
        TieredPromotionDTO dto = new TieredPromotionDTO(1L, "满减活动", "满100减10",
                LocalDateTime.now(), LocalDateTime.now().plusDays(7), "ENABLED", LocalDateTime.now(), List.of(ruleDto));

        given(tieredPromotionService.getPromotion(1L)).willReturn(dto);

        TieredRuleVO ruleVO = new TieredRuleVO(1L, new BigDecimal("100.00"), new BigDecimal("10.00"));
        TieredPromotionVO vo = new TieredPromotionVO(1L, "满减活动", "TIERED",
                LocalDateTime.now(), LocalDateTime.now().plusDays(7), "ENABLED", List.of(ruleVO));
        given(marketingConverter.tieredPromotionDtoToVOWithRules(dto)).willReturn(vo);

        mockMvc.perform(get("/tiered/promotions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("满减活动"));
    }

    @Test
    @DisplayName("计算满减优惠 - 成功返回信封格式")
    void calculateDiscount_ShouldReturn200WithEnvelope() throws Exception {
        CalculateDiscountResult result = new CalculateDiscountResult(1L, new BigDecimal("100.00"),
                new BigDecimal("10.00"), true);

        given(tieredPromotionService.calculateDiscount(Mockito.any(CalculateDiscountRequest.class))).willReturn(result);

        mockMvc.perform(post("/tiered/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"promotionId\":1,\"orderAmount\":150.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.matched").value(true))
                .andExpect(jsonPath("$.data.discountAmount").value(10.00));
    }

    @Test
    @DisplayName("获取不存在的满减活动 - 返回错误信封")
    void getPromotion_WhenNotFound_ShouldReturnErrorEnvelope() throws Exception {
        given(tieredPromotionService.getPromotion(999L))
                .willThrow(new BusinessException("PROMOTION_NOT_FOUND", "满减活动不存在"));

        mockMvc.perform(get("/tiered/promotions/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("PROMOTION_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("满减活动不存在"));
    }

    @Test
    @DisplayName("计算满减优惠 - 活动不存在返回错误信封")
    void calculateDiscount_WhenPromotionNotFound_ShouldReturnErrorEnvelope() throws Exception {
        given(tieredPromotionService.calculateDiscount(Mockito.any(CalculateDiscountRequest.class)))
                .willThrow(new BusinessException("PROMOTION_NOT_FOUND", "满减活动不存在"));

        mockMvc.perform(post("/tiered/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"promotionId\":999,\"orderAmount\":150.00}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("PROMOTION_NOT_FOUND"));
    }
}
