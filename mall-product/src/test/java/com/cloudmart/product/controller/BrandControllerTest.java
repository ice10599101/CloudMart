package com.cloudmart.product.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.product.converter.ProductConverter;
import com.cloudmart.product.dto.BrandDTO;
import com.cloudmart.product.dto.CreateBrandRequest;
import com.cloudmart.product.dto.UpdateBrandRequest;
import com.cloudmart.product.repository.BrandMapper;
import com.cloudmart.product.service.BrandService;
import com.cloudmart.product.vo.BrandVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BrandControllerTest {

    private MockMvc mockMvc;

    private final BrandService brandService = Mockito.mock(BrandService.class);
    private final ProductConverter productConverter = Mockito.mock(ProductConverter.class);
    private final BrandMapper brandMapper = Mockito.mock(BrandMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2026, 5, 29, 10, 0);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new BrandController(brandService, productConverter),
                        new AdminBrandController(brandService, productConverter, brandMapper))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private BrandDTO buildBrandDTO() {
        return new BrandDTO(1L, "品牌A", "logo.png", "品牌描述", 1, 1, FIXED_TIME);
    }

    private BrandVO buildBrandVO() {
        return new BrandVO(1L, "品牌A", "logo.png", "品牌描述", 1);
    }

    @Test
    @DisplayName("GET /brands/{id} - 获取品牌详情返回信封格式")
    void getBrand_ShouldReturnSuccessEnvelope() throws Exception {
        BrandDTO dto = buildBrandDTO();
        BrandVO vo = buildBrandVO();
        given(brandService.getBrand(1L)).willReturn(dto);
        given(productConverter.brandDtoToVO(dto)).willReturn(vo);

        mockMvc.perform(get("/brands/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("品牌A"));
    }

    @Test
    @DisplayName("GET /brands/{id} - 品牌不存在返回错误信封")
    void getBrand_WhenNotFound_ShouldReturnErrorEnvelope() throws Exception {
        willThrow(new BusinessException("BRAND_SERVICE_UNAVAILABLE", "品牌不存在"))
                .given(brandService).getBrand(999L);

        mockMvc.perform(get("/brands/999"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("BRAND_SERVICE_UNAVAILABLE"));
    }

    @Test
    @DisplayName("GET /brands - 品牌列表返回信封格式")
    void listBrands_ShouldReturnSuccessEnvelope() throws Exception {
        BrandDTO dto = buildBrandDTO();
        IPage<BrandDTO> dtoPage = new Page<>(1, 10, 1L);
        ((Page<BrandDTO>) dtoPage).setRecords(List.of(dto));
        given(brandService.listBrands(null, null, 1, 10)).willReturn(dtoPage);

        BrandVO vo = buildBrandVO();
        given(productConverter.brandDtoToVO(dto)).willReturn(vo);

        mockMvc.perform(get("/brands")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    @DisplayName("GET /brands - 带名称过滤返回信封格式")
    void listBrands_WithNameFilter_ShouldReturnSuccessEnvelope() throws Exception {
        BrandDTO dto = buildBrandDTO();
        IPage<BrandDTO> dtoPage = new Page<>(1, 10, 1L);
        ((Page<BrandDTO>) dtoPage).setRecords(List.of(dto));
        given(brandService.listBrands("品牌", null, 1, 10)).willReturn(dtoPage);

        BrandVO vo = buildBrandVO();
        given(productConverter.brandDtoToVO(dto)).willReturn(vo);

        mockMvc.perform(get("/brands")
                        .param("name", "品牌")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /admin/brands - 创建品牌返回信封格式")
    void createBrand_ShouldReturnSuccessEnvelope() throws Exception {
        BrandDTO dto = buildBrandDTO();
        BrandVO vo = buildBrandVO();
        given(brandService.createBrand(any(CreateBrandRequest.class))).willReturn(dto);
        given(productConverter.brandDtoToVO(dto)).willReturn(vo);

        mockMvc.perform(post("/admin/brands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateBrandRequest("品牌A", "logo.png", "品牌描述", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("品牌A"));
    }

    @Test
    @DisplayName("POST /admin/brands - 品牌名重复返回错误信封")
    void createBrand_WhenDuplicateName_ShouldReturnErrorEnvelope() throws Exception {
        willThrow(new BusinessException("VALIDATION_ERROR", "品牌名称已存在"))
                .given(brandService).createBrand(any(CreateBrandRequest.class));

        mockMvc.perform(post("/admin/brands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateBrandRequest("品牌A", "logo.png", "品牌描述", 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("PUT /admin/brands/{id} - 更新品牌返回信封格式")
    void updateBrand_ShouldReturnSuccessEnvelope() throws Exception {
        BrandDTO dto = new BrandDTO(1L, "品牌B", "new-logo.png", "新描述", 2, 1, FIXED_TIME);
        BrandVO vo = new BrandVO(1L, "品牌B", "new-logo.png", "新描述", 1);
        given(brandService.updateBrand(eq(1L), any(UpdateBrandRequest.class))).willReturn(dto);
        given(productConverter.brandDtoToVO(dto)).willReturn(vo);

        mockMvc.perform(put("/admin/brands/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateBrandRequest("品牌B", "new-logo.png", "新描述", 2, 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("品牌B"));
    }

    @Test
    @DisplayName("PUT /admin/brands/{id} - 品牌不存在返回错误信封")
    void updateBrand_WhenNotFound_ShouldReturnErrorEnvelope() throws Exception {
        willThrow(new BusinessException("BRAND_SERVICE_UNAVAILABLE", "品牌不存在"))
                .given(brandService).updateBrand(eq(999L), any(UpdateBrandRequest.class));

        mockMvc.perform(put("/admin/brands/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateBrandRequest("品牌B", null, null, null, null))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("BRAND_SERVICE_UNAVAILABLE"));
    }

    @Test
    @DisplayName("GET /admin/brands/{id} - Admin获取品牌详情返回信封格式")
    void adminGetBrand_ShouldReturnSuccessEnvelope() throws Exception {
        BrandDTO dto = buildBrandDTO();
        BrandVO vo = buildBrandVO();
        given(brandService.getBrand(1L)).willReturn(dto);
        given(productConverter.brandDtoToVO(dto)).willReturn(vo);

        mockMvc.perform(get("/admin/brands/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("GET /admin/brands - Admin品牌列表返回信封格式")
    void adminListBrands_ShouldReturnSuccessEnvelope() throws Exception {
        BrandDTO dto = buildBrandDTO();
        IPage<BrandDTO> dtoPage = new Page<>(1, 10, 1L);
        ((Page<BrandDTO>) dtoPage).setRecords(List.of(dto));
        given(brandService.listBrands(null, null, 1, 10)).willReturn(dtoPage);

        BrandVO vo = buildBrandVO();
        given(productConverter.brandDtoToVO(dto)).willReturn(vo);

        mockMvc.perform(get("/admin/brands")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
