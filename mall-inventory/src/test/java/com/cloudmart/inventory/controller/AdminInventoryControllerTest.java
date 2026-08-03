package com.cloudmart.inventory.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.inventory.converter.InventoryConverter;
import com.cloudmart.inventory.dto.InventoryDTO;
import com.cloudmart.inventory.service.InventoryService;
import com.cloudmart.inventory.vo.InventoryVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminInventoryControllerTest {

    private MockMvc mockMvc;

    private final InventoryService inventoryService = Mockito.mock(InventoryService.class);
    private final InventoryConverter inventoryConverter = Mockito.mock(InventoryConverter.class);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminInventoryController(inventoryService, inventoryConverter))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("库存列表 - 成功返回分页信封")
    void listInventory_ShouldReturnPagedEnvelope() throws Exception {
        InventoryDTO dto = new InventoryDTO(1L, 100L, 200L, 50, 10);
        Page<InventoryDTO> page = new Page<>(1, 20, 1L);
        page.setRecords(List.of(dto));
        InventoryVO vo = new InventoryVO(1L, 100L, 200L, 50, 10, null);

        given(inventoryService.listInventory(null, 1, 20)).willReturn(page);
        given(inventoryConverter.dtoToVO(dto)).willReturn(vo);

        mockMvc.perform(get("/admin/inventory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].skuId").value(200))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.pageSize").value(20))
                .andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    @DisplayName("库存列表 - 按商品ID筛选返回信封")
    void listInventory_WithProductIdFilter_ShouldReturnEnvelope() throws Exception {
        InventoryDTO dto = new InventoryDTO(1L, 100L, 200L, 50, 10);
        Page<InventoryDTO> page = new Page<>(1, 20, 1L);
        page.setRecords(List.of(dto));
        InventoryVO vo = new InventoryVO(1L, 100L, 200L, 50, 10, null);

        given(inventoryService.listInventory(100L, 1, 20)).willReturn(page);
        given(inventoryConverter.dtoToVO(dto)).willReturn(vo);

        mockMvc.perform(get("/admin/inventory")
                        .param("productId", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].productId").value(100));
    }

    @Test
    @DisplayName("查询库存详情 - 成功返回信封")
    void getInventory_ShouldReturnEnvelope() throws Exception {
        InventoryDTO dto = new InventoryDTO(1L, 100L, 200L, 50, 10);
        InventoryVO vo = new InventoryVO(1L, 100L, 200L, 50, 10, null);

        given(inventoryService.getInventory(200L)).willReturn(dto);
        given(inventoryConverter.dtoToVO(dto)).willReturn(vo);

        mockMvc.perform(get("/admin/inventory/200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.skuId").value(200))
                .andExpect(jsonPath("$.data.availableStock").value(50));
    }

    @Test
    @DisplayName("初始化库存 - 成功返回信封")
    void initStock_ShouldReturnEnvelope() throws Exception {
        mockMvc.perform(post("/admin/inventory/init")
                        .param("skuId", "200")
                        .param("productId", "100")
                        .param("stock", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(inventoryService).initStock(200L, 100L, 500);
    }
}
