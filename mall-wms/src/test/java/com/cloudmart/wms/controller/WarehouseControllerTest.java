package com.cloudmart.wms.controller;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.wms.converter.WmsConverter;
import com.cloudmart.wms.dto.CreateInboundOrderRequest;
import com.cloudmart.wms.dto.CreatePickOrderRequest;
import com.cloudmart.wms.dto.CreateWarehouseRequest;
import com.cloudmart.wms.dto.InboundOrderDTO;
import com.cloudmart.wms.dto.PickOrderDTO;
import com.cloudmart.wms.dto.UpdateWarehouseRequest;
import com.cloudmart.wms.service.InboundOrderService;
import com.cloudmart.wms.service.PickOrderService;
import com.cloudmart.wms.service.WarehouseService;
import com.cloudmart.wms.vo.InboundOrderVO;
import com.cloudmart.wms.vo.PickOrderVO;
import com.cloudmart.wms.vo.WarehouseVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WarehouseControllerTest {

    private MockMvc mockMvc;

    private final WarehouseService warehouseService = Mockito.mock(WarehouseService.class);
    private final PickOrderService pickOrderService = Mockito.mock(PickOrderService.class);
    private final InboundOrderService inboundOrderService = Mockito.mock(InboundOrderService.class);
    private final WmsConverter wmsConverter = Mockito.mock(WmsConverter.class);

    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2026, 5, 29, 10, 0);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new WarehouseController(warehouseService, pickOrderService, inboundOrderService, wmsConverter))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("创建仓库 - 成功返回信封")
    void createWarehouse_ShouldReturnEnvelope() throws Exception {
        WarehouseVO vo = new WarehouseVO(1L, "主仓", "WH001", "北京市", "010-12345678", 0);

        given(warehouseService.createWarehouse(Mockito.any(CreateWarehouseRequest.class))).willReturn(vo);

        mockMvc.perform(post("/warehouses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"主仓\",\"address\":\"北京市\",\"contactPhone\":\"010-12345678\",\"status\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("主仓"));
    }

    @Test
    @DisplayName("创建仓库 - 缺少必填字段返回校验错误")
    void createWarehouse_WhenMissingName_ShouldReturnValidationError() throws Exception {
        mockMvc.perform(post("/warehouses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"address\":\"北京市\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("查询仓库列表 - 成功返回信封")
    void listWarehouses_ShouldReturnEnvelope() throws Exception {
        WarehouseVO vo = new WarehouseVO(1L, "主仓", "WH001", "北京市", "010-12345678", 0);

        given(warehouseService.listWarehouses()).willReturn(List.of(vo));

        mockMvc.perform(get("/warehouses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1));
    }

    @Test
    @DisplayName("查询仓库详情 - 成功返回信封")
    void getWarehouse_ShouldReturnEnvelope() throws Exception {
        WarehouseVO vo = new WarehouseVO(1L, "主仓", "WH001", "北京市", "010-12345678", 0);

        given(warehouseService.getWarehouse(1L)).willReturn(vo);

        mockMvc.perform(get("/warehouses/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("主仓"));
    }

    @Test
    @DisplayName("更新仓库 - 成功返回信封")
    void updateWarehouse_ShouldReturnEnvelope() throws Exception {
        WarehouseVO vo = new WarehouseVO(1L, "主仓-更新", "WH001", "上海市", "021-12345678", 0);

        given(warehouseService.updateWarehouse(Mockito.eq(1L), Mockito.any(UpdateWarehouseRequest.class))).willReturn(vo);

        mockMvc.perform(put("/warehouses/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"主仓-更新\",\"address\":\"上海市\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("主仓-更新"));
    }

    @Test
    @DisplayName("删除仓库 - 成功返回信封")
    void deleteWarehouse_ShouldReturnEnvelope() throws Exception {
        mockMvc.perform(delete("/warehouses/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(warehouseService).deleteWarehouse(1L);
    }

    @Test
    @DisplayName("创建拣货单 - 成功返回信封")
    void createPickOrder_ShouldReturnEnvelope() throws Exception {
        PickOrderDTO dto = new PickOrderDTO(1L, 100L, 1L, "PENDING", null, null, null, null, FIXED_TIME, List.of());
        PickOrderVO vo = new PickOrderVO(1L, 100L, "主仓", "PENDING", FIXED_TIME);

        given(pickOrderService.createPickOrder(Mockito.any(CreatePickOrderRequest.class))).willReturn(dto);
        given(wmsConverter.fromPickOrderDTO(dto)).willReturn(vo);

        mockMvc.perform(post("/warehouses/pick-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":100,\"warehouseId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("创建入库单 - 成功返回信封")
    void createInboundOrder_ShouldReturnEnvelope() throws Exception {
        InboundOrderDTO dto = new InboundOrderDTO(1L, 1L, "PURCHASE", "PO-001", "PENDING",
                100, 0, null, null, null, FIXED_TIME, List.of());
        InboundOrderVO vo = new InboundOrderVO(1L, "主仓", "供应商A", "PENDING", 100, FIXED_TIME);

        given(inboundOrderService.createInboundOrder(Mockito.any(CreateInboundOrderRequest.class))).willReturn(dto);
        given(wmsConverter.fromInboundOrderDTO(dto)).willReturn(vo);

        mockMvc.perform(post("/warehouses/inbound-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"warehouseId\":1,\"type\":\"PURCHASE\",\"items\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }
}
