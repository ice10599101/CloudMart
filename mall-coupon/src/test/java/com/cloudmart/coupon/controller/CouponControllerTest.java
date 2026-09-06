package com.cloudmart.coupon.controller;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.coupon.converter.CouponTemplateConverter;
import com.cloudmart.coupon.dto.CreateCouponTemplateRequest;
import com.cloudmart.coupon.dto.CouponTemplateDTO;
import com.cloudmart.coupon.service.CouponService;
import com.cloudmart.coupon.vo.CouponTemplateVO;
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

class CouponControllerTest {

    private MockMvc mockMvc;

    private final CouponService couponService = Mockito.mock(CouponService.class);
    private final CouponTemplateConverter couponTemplateConverter = Mockito.mock(CouponTemplateConverter.class);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CouponController(couponService, couponTemplateConverter))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("创建优惠券模板 - 成功返回信封格式")
    void createTemplate_ShouldReturn200WithEnvelope() throws Exception {
        CouponTemplateDTO dto = new CouponTemplateDTO(1L, "满100减20", "AMOUNT_OFF",
                new BigDecimal("100.00"), new BigDecimal("20.00"), null,
                1000, 980, 1, "FIXED_DAYS", null, null, 30, "ENABLED", LocalDateTime.now());

        given(couponService.createTemplate(Mockito.any(CreateCouponTemplateRequest.class))).willReturn(dto);

        CouponTemplateVO vo = new CouponTemplateVO(1L, "满100减20", "AMOUNT_OFF",
                new BigDecimal("100.00"), new BigDecimal("20.00"), null, 1000, 980, 1,
                null, null, null, null, "ENABLED");
        given(couponTemplateConverter.dtoToVO(dto)).willReturn(vo);

        mockMvc.perform(post("/coupon-templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"满100减20\",\"type\":\"AMOUNT_OFF\",\"thresholdAmount\":100,\"discountAmount\":20,\"totalQuantity\":1000,\"perUserLimit\":1,\"validityType\":\"FIXED_DAYS\",\"validDays\":30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("满100减20"));
    }

    @Test
    @DisplayName("查询优惠券模板列表 - 成功返回信封格式带分页")
    void listTemplates_ShouldReturn200WithMeta() throws Exception {
        CouponTemplateDTO dto = new CouponTemplateDTO(1L, "满100减20", "AMOUNT_OFF",
                new BigDecimal("100.00"), new BigDecimal("20.00"), null,
                1000, 980, 1, "FIXED_DAYS", null, null, 30, "ENABLED", LocalDateTime.now());

        given(couponService.listTemplates(null, null, 1, 20)).willReturn(List.of(dto));
        given(couponService.countTemplates(null, null)).willReturn(1L);

        CouponTemplateVO vo = new CouponTemplateVO(1L, "满100减20", "AMOUNT_OFF",
                new BigDecimal("100.00"), new BigDecimal("20.00"), null, 1000, 980, 1,
                null, null, null, null, "ENABLED");
        given(couponTemplateConverter.dtoListToVOList(List.of(dto))).willReturn(List.of(vo));

        mockMvc.perform(get("/coupon-templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.pageSize").value(20))
                .andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    @DisplayName("查询优惠券模板详情 - 成功返回信封格式")
    void getTemplateById_ShouldReturn200WithEnvelope() throws Exception {
        CouponTemplateDTO dto = new CouponTemplateDTO(1L, "满100减20", "AMOUNT_OFF",
                new BigDecimal("100.00"), new BigDecimal("20.00"), null,
                1000, 980, 1, "FIXED_DAYS", null, null, 30, "ENABLED", LocalDateTime.now());

        given(couponService.getTemplateById(1L)).willReturn(dto);

        CouponTemplateVO vo = new CouponTemplateVO(1L, "满100减20", "AMOUNT_OFF",
                new BigDecimal("100.00"), new BigDecimal("20.00"), null, 1000, 980, 1,
                null, null, null, null, "ENABLED");
        given(couponTemplateConverter.dtoToVO(dto)).willReturn(vo);

        mockMvc.perform(get("/coupon-templates/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("满100减20"));
    }

    @Test
    @DisplayName("禁用优惠券模板 - 成功返回信封格式")
    void disableTemplate_ShouldReturn200WithEnvelope() throws Exception {
        CouponTemplateDTO dto = new CouponTemplateDTO(1L, "满100减20", "AMOUNT_OFF",
                new BigDecimal("100.00"), new BigDecimal("20.00"), null,
                1000, 980, 1, "FIXED_DAYS", null, null, 30, "DISABLED", LocalDateTime.now());

        given(couponService.disableTemplate(1L)).willReturn(dto);

        CouponTemplateVO vo = new CouponTemplateVO(1L, "满100减20", "AMOUNT_OFF",
                new BigDecimal("100.00"), new BigDecimal("20.00"), null, 1000, 980, 1,
                null, null, null, null, "DISABLED");
        given(couponTemplateConverter.dtoToVO(dto)).willReturn(vo);

        mockMvc.perform(put("/coupon-templates/1/disable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("DISABLED"));
    }

    @Test
    @DisplayName("查询不存在的优惠券模板 - 返回错误信封")
    void getTemplateById_WhenNotFound_ShouldReturnErrorEnvelope() throws Exception {
        given(couponService.getTemplateById(999L))
                .willThrow(new BusinessException("COUPON_NOT_FOUND", "优惠券模板不存在"));

        mockMvc.perform(get("/coupon-templates/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COUPON_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("优惠券模板不存在"));
    }

    @Test
    @DisplayName("创建优惠券模板 - 缺少必填字段返回校验错误")
    void createTemplate_WhenMissingRequiredFields_ShouldReturnValidationError() throws Exception {
        mockMvc.perform(post("/coupon-templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"AMOUNT_OFF\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
