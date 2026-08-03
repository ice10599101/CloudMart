package com.cloudmart.marketing.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.marketing.converter.MarketingConverter;
import com.cloudmart.marketing.dto.CreateTieredPromotionRequest;
import com.cloudmart.marketing.dto.TieredPromotionDTO;
import com.cloudmart.marketing.dto.TieredRuleDTO;
import com.cloudmart.marketing.repository.TieredPromotionMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminTieredPromotionControllerTest {

    private MockMvc mockMvc;

    private final TieredPromotionService tieredPromotionService = Mockito.mock(TieredPromotionService.class);
    private final MarketingConverter marketingConverter = Mockito.mock(MarketingConverter.class);
    private final TieredPromotionMapper tieredPromotionMapper = Mockito.mock(TieredPromotionMapper.class);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminTieredPromotionController(tieredPromotionService, marketingConverter, tieredPromotionMapper))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("创建阶梯满减活动 - 成功返回信封格式")
    void createPromotion_ShouldReturn200WithEnvelope() throws Exception {
        TieredRuleDTO ruleDto = new TieredRuleDTO(1L, new BigDecimal("100.00"), new BigDecimal("10.00"));
        TieredPromotionDTO dto = new TieredPromotionDTO(1L, "满减活动", "满100减10",
                LocalDateTime.now(), LocalDateTime.now().plusDays(7), "ENABLED", LocalDateTime.now(), List.of(ruleDto));

        given(tieredPromotionService.createPromotion(Mockito.any(CreateTieredPromotionRequest.class))).willReturn(dto);

        TieredRuleVO ruleVO = new TieredRuleVO(1L, new BigDecimal("100.00"), new BigDecimal("10.00"));
        TieredPromotionVO vo = new TieredPromotionVO(1L, "满减活动", "TIERED",
                LocalDateTime.now(), LocalDateTime.now().plusDays(7), "ENABLED", List.of(ruleVO));
        given(marketingConverter.tieredPromotionDtoToVOWithRules(dto)).willReturn(vo);

        mockMvc.perform(post("/admin/marketing/tiered/promotions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"满减活动\",\"description\":\"满100减10\",\"startTime\":\"2026-06-01T00:00:00\",\"endTime\":\"2026-06-08T00:00:00\",\"rules\":[{\"minAmount\":100.00,\"discountAmount\":10.00}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("满减活动"));
    }

    @Test
    @DisplayName("启用满减活动 - 成功返回信封格式")
    void enablePromotion_ShouldReturn200WithEnvelope() throws Exception {
        TieredRuleDTO ruleDto = new TieredRuleDTO(1L, new BigDecimal("100.00"), new BigDecimal("10.00"));
        TieredPromotionDTO dto = new TieredPromotionDTO(1L, "满减活动", "满100减10",
                LocalDateTime.now(), LocalDateTime.now().plusDays(7), "ENABLED", LocalDateTime.now(), List.of(ruleDto));

        given(tieredPromotionService.enablePromotion(1L)).willReturn(dto);

        TieredRuleVO ruleVO = new TieredRuleVO(1L, new BigDecimal("100.00"), new BigDecimal("10.00"));
        TieredPromotionVO vo = new TieredPromotionVO(1L, "满减活动", "TIERED",
                LocalDateTime.now(), LocalDateTime.now().plusDays(7), "ENABLED", List.of(ruleVO));
        given(marketingConverter.tieredPromotionDtoToVOWithRules(dto)).willReturn(vo);

        mockMvc.perform(put("/admin/marketing/tiered/promotions/1/enable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ENABLED"));
    }

    @Test
    @DisplayName("停用满减活动 - 成功返回信封格式")
    void disablePromotion_ShouldReturn200WithEnvelope() throws Exception {
        TieredRuleDTO ruleDto = new TieredRuleDTO(1L, new BigDecimal("100.00"), new BigDecimal("10.00"));
        TieredPromotionDTO dto = new TieredPromotionDTO(1L, "满减活动", "满100减10",
                LocalDateTime.now(), LocalDateTime.now().plusDays(7), "DISABLED", LocalDateTime.now(), List.of(ruleDto));

        given(tieredPromotionService.disablePromotion(1L)).willReturn(dto);

        TieredRuleVO ruleVO = new TieredRuleVO(1L, new BigDecimal("100.00"), new BigDecimal("10.00"));
        TieredPromotionVO vo = new TieredPromotionVO(1L, "满减活动", "TIERED",
                LocalDateTime.now(), LocalDateTime.now().plusDays(7), "DISABLED", List.of(ruleVO));
        given(marketingConverter.tieredPromotionDtoToVOWithRules(dto)).willReturn(vo);

        mockMvc.perform(put("/admin/marketing/tiered/promotions/1/disable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("DISABLED"));
    }

    @Test
    @DisplayName("查询满减活动列表 - 成功返回信封格式")
    void listPromotions_ShouldReturn200WithEnvelope() throws Exception {
        TieredRuleDTO ruleDto = new TieredRuleDTO(1L, new BigDecimal("100.00"), new BigDecimal("10.00"));
        TieredPromotionDTO dto = new TieredPromotionDTO(1L, "满减活动", "满100减10",
                LocalDateTime.now(), LocalDateTime.now().plusDays(7), "ENABLED", LocalDateTime.now(), List.of(ruleDto));

        IPage<TieredPromotionDTO> dtoPage = new Page<>(1, 10, 1);
        dtoPage.setRecords(List.of(dto));
        given(tieredPromotionService.listPromotions(null, 1, 10)).willReturn(dtoPage);

        TieredRuleVO ruleVO = new TieredRuleVO(1L, new BigDecimal("100.00"), new BigDecimal("10.00"));
        TieredPromotionVO vo = new TieredPromotionVO(1L, "满减活动", "TIERED",
                LocalDateTime.now(), LocalDateTime.now().plusDays(7), "ENABLED", List.of(ruleVO));
        given(marketingConverter.tieredPromotionDtoToVOWithRules(dto)).willReturn(vo);

        mockMvc.perform(get("/admin/marketing/tiered/promotions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.records[0].id").value(1));
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

        mockMvc.perform(get("/admin/marketing/tiered/promotions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("创建满减活动 - 缺少必填字段返回校验错误")
    void createPromotion_WhenMissingRequiredFields_ShouldReturnValidationError() throws Exception {
        mockMvc.perform(post("/admin/marketing/tiered/promotions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("启用不存在的满减活动 - 返回错误信封")
    void enablePromotion_WhenNotFound_ShouldReturnErrorEnvelope() throws Exception {
        given(tieredPromotionService.enablePromotion(999L))
                .willThrow(new BusinessException("PROMOTION_NOT_FOUND", "满减活动不存在"));

        mockMvc.perform(put("/admin/marketing/tiered/promotions/999/enable"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("PROMOTION_NOT_FOUND"));
    }
}
