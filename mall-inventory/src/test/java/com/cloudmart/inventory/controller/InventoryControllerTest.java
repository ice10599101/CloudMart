package com.cloudmart.inventory.controller;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.inventory.converter.InventoryConverter;
import com.cloudmart.inventory.dto.DeductRequest;
import com.cloudmart.inventory.dto.InventoryDTO;
import com.cloudmart.inventory.dto.ReleaseRequest;
import com.cloudmart.inventory.service.InventoryService;
import com.cloudmart.inventory.vo.InventoryVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InventoryControllerTest {

    private MockMvc mockMvc;

    private final InventoryService inventoryService = Mockito.mock(InventoryService.class);
    private final InventoryConverter inventoryConverter = Mockito.mock(InventoryConverter.class);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new InventoryController(inventoryService, inventoryConverter))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("查询库存 - 成功返回信封")
    void getInventory_ShouldReturnEnvelope() throws Exception {
        InventoryDTO dto = new InventoryDTO(1L, 100L, 200L, 50, 10);
        InventoryVO vo = new InventoryVO(1L, 100L, 200L, 50, 10, null);

        given(inventoryService.getInventory(200L)).willReturn(dto);
        given(inventoryConverter.dtoToVO(dto)).willReturn(vo);

        mockMvc.perform(get("/200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.skuId").value(200))
                .andExpect(jsonPath("$.data.availableStock").value(50))
                .andExpect(jsonPath("$.data.lockedStock").value(10));
    }

    @Test
    @DisplayName("查询库存 - 不存在时返回错误信封")
    void getInventory_WhenNotFound_ShouldReturnErrorEnvelope() throws Exception {
        given(inventoryService.getInventory(999L))
                .willThrow(new BusinessException("INVENTORY_SERVICE_UNAVAILABLE", "库存不存在"));

        mockMvc.perform(get("/999"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVENTORY_SERVICE_UNAVAILABLE"));
    }

    @Test
    @DisplayName("预扣库存 - 成功返回信封")
    void deductStock_ShouldReturnEnvelope() throws Exception {
        given(inventoryService.deductStock(Mockito.any(DeductRequest.class))).willReturn(true);

        mockMvc.perform(post("/deduct")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skuId\":200,\"quantity\":5,\"orderId\":1000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    @DisplayName("预扣库存 - 缺少必填字段返回校验错误")
    void deductStock_WhenMissingRequiredField_ShouldReturnValidationError() throws Exception {
        mockMvc.perform(post("/deduct")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("释放库存 - 成功返回信封")
    void releaseStock_ShouldReturnEnvelope() throws Exception {
        mockMvc.perform(post("/release")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skuId\":200,\"quantity\":5,\"orderId\":1000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(inventoryService).releaseStock(Mockito.any(ReleaseRequest.class));
    }

    @Test
    @DisplayName("确认扣减 - 成功返回信封")
    void confirmDeduct_ShouldReturnEnvelope() throws Exception {
        mockMvc.perform(post("/confirm")
                        .param("skuId", "200")
                        .param("quantity", "5")
                        .param("orderId", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(inventoryService).confirmDeduct(200L, 5, 1000L);
    }

    @Test
    @DisplayName("初始化库存 - 成功返回信封")
    void initStock_ShouldReturnEnvelope() throws Exception {
        mockMvc.perform(post("/init")
                        .param("skuId", "200")
                        .param("productId", "100")
                        .param("stock", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(inventoryService).initStock(200L, 100L, 500);
    }
}
