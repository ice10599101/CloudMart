package com.cloudmart.wms.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import com.cloudmart.wms.service.ShippingService;
import com.cloudmart.wms.service.WarehouseService;
import com.cloudmart.wms.vo.InboundOrderVO;
import com.cloudmart.wms.vo.PickOrderVO;
import com.cloudmart.wms.vo.ShippingOrderVO;
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

class AdminWmsControllerTest {

    private MockMvc mockMvc;

    private final PickOrderService pickOrderService = Mockito.mock(PickOrderService.class);
    private final InboundOrderService inboundOrderService = Mockito.mock(InboundOrderService.class);
    private final WarehouseService warehouseService = Mockito.mock(WarehouseService.class);
    private final ShippingService shippingService = Mockito.mock(ShippingService.class);
    private final WmsConverter wmsConverter = Mockito.mock(WmsConverter.class);

    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2026, 5, 29, 10, 0);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new AdminWmsController(pickOrderService, inboundOrderService, warehouseService, shippingService, wmsConverter))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("创建拣货单 - 成功返回信封")
    void createPickOrder_ShouldReturnEnvelope() throws Exception {
        PickOrderDTO dto = new PickOrderDTO(1L, 100L, 1L, "PENDING", null, null, null, null, FIXED_TIME, List.of());
        PickOrderVO vo = new PickOrderVO(1L, 100L, "主仓", "PENDING", FIXED_TIME);

        given(pickOrderService.createPickOrder(Mockito.any(CreatePickOrderRequest.class))).willReturn(dto);
        given(wmsConverter.fromPickOrderDTO(dto)).willReturn(vo);

        mockMvc.perform(post("/admin/wms/pick-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":100,\"warehouseId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("开始拣货 - 成功返回信封")
    void startPick_ShouldReturnEnvelope() throws Exception {
        PickOrderDTO dto = new PickOrderDTO(1L, 100L, 1L, "PICKING", 10L, FIXED_TIME, null, null, FIXED_TIME, List.of());
        PickOrderVO vo = new PickOrderVO(1L, 100L, "主仓", "PICKING", FIXED_TIME);

        given(pickOrderService.startPick(1L, 10L)).willReturn(dto);
        given(wmsConverter.fromPickOrderDTO(dto)).willReturn(vo);

        mockMvc.perform(put("/admin/wms/pick-orders/1/start")
                        .param("assignedUserId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PICKING"));
    }

    @Test
    @DisplayName("确认拣货完成 - 成功返回信封")
    void confirmPicked_ShouldReturnEnvelope() throws Exception {
        PickOrderDTO dto = new PickOrderDTO(1L, 100L, 1L, "PICKED", 10L, FIXED_TIME, FIXED_TIME, null, FIXED_TIME, List.of());
        PickOrderVO vo = new PickOrderVO(1L, 100L, "主仓", "PICKED", FIXED_TIME);

        given(pickOrderService.confirmPicked(1L)).willReturn(dto);
        given(wmsConverter.fromPickOrderDTO(dto)).willReturn(vo);

        mockMvc.perform(put("/admin/wms/pick-orders/1/picked"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PICKED"));
    }

    @Test
    @DisplayName("确认打包完成 - 成功返回信封")
    void confirmPacked_ShouldReturnEnvelope() throws Exception {
        PickOrderDTO dto = new PickOrderDTO(1L, 100L, 1L, "PACKED", 10L, FIXED_TIME, FIXED_TIME, "已打包", FIXED_TIME, List.of());
        PickOrderVO vo = new PickOrderVO(1L, 100L, "主仓", "PACKED", FIXED_TIME);

        given(pickOrderService.confirmPacked(1L)).willReturn(dto);
        given(wmsConverter.fromPickOrderDTO(dto)).willReturn(vo);

        mockMvc.perform(put("/admin/wms/pick-orders/1/packed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PACKED"));
    }

    @Test
    @DisplayName("查询拣货单列表 - 成功返回分页信封")
    void listPickOrders_ShouldReturnPagedEnvelope() throws Exception {
        PickOrderDTO dto = new PickOrderDTO(1L, 100L, 1L, "PENDING", null, null, null, null, FIXED_TIME, List.of());
        Page<PickOrderDTO> page = new Page<>(1, 10, 1L);
        page.setRecords(List.of(dto));
        PickOrderVO vo = new PickOrderVO(1L, 100L, "主仓", "PENDING", FIXED_TIME);

        given(pickOrderService.listPickOrders(null, null, 1, 10)).willReturn(page);
        given(wmsConverter.fromPickOrderDTO(dto)).willReturn(vo);

        mockMvc.perform(get("/admin/wms/pick-orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.pageSize").value(10))
                .andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    @DisplayName("创建入库单 - 成功返回信封")
    void createInboundOrder_ShouldReturnEnvelope() throws Exception {
        InboundOrderDTO dto = new InboundOrderDTO(1L, 1L, "PURCHASE", "PO-001", "PENDING",
                100, 0, null, null, null, FIXED_TIME, List.of());
        InboundOrderVO vo = new InboundOrderVO(1L, "主仓", "供应商A", "PENDING", 100, FIXED_TIME);

        given(inboundOrderService.createInboundOrder(Mockito.any(CreateInboundOrderRequest.class))).willReturn(dto);
        given(wmsConverter.fromInboundOrderDTO(dto)).willReturn(vo);

        mockMvc.perform(post("/admin/wms/inbound-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"warehouseId\":1,\"type\":\"PURCHASE\",\"items\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("收货入库 - 成功返回信封")
    void receiveItem_ShouldReturnEnvelope() throws Exception {
        InboundOrderDTO dto = new InboundOrderDTO(1L, 1L, "PURCHASE", "PO-001", "RECEIVING",
                100, 50, null, null, null, FIXED_TIME, List.of());
        InboundOrderVO vo = new InboundOrderVO(1L, "主仓", "供应商A", "RECEIVING", 100, FIXED_TIME);

        given(inboundOrderService.receiveItem(1L, 10L, 50)).willReturn(dto);
        given(wmsConverter.fromInboundOrderDTO(dto)).willReturn(vo);

        mockMvc.perform(put("/admin/wms/inbound-orders/1/receive")
                        .param("itemId", "10")
                        .param("receivedQuantity", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("RECEIVING"));
    }

    @Test
    @DisplayName("完成入库 - 成功返回信封")
    void completeInbound_ShouldReturnEnvelope() throws Exception {
        InboundOrderDTO dto = new InboundOrderDTO(1L, 1L, "PURCHASE", "PO-001", "COMPLETED",
                100, 100, null, FIXED_TIME, null, FIXED_TIME, List.of());
        InboundOrderVO vo = new InboundOrderVO(1L, "主仓", "供应商A", "COMPLETED", 100, FIXED_TIME);

        given(inboundOrderService.completeInbound(1L)).willReturn(dto);
        given(wmsConverter.fromInboundOrderDTO(dto)).willReturn(vo);

        mockMvc.perform(put("/admin/wms/inbound-orders/1/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("查询仓库列表 - 成功返回信封")
    void listWarehouses_ShouldReturnEnvelope() throws Exception {
        WarehouseVO vo = new WarehouseVO(1L, "主仓", "WH001", "北京市", "010-12345678", 0);

        given(warehouseService.listWarehouses()).willReturn(List.of(vo));

        mockMvc.perform(get("/admin/wms/warehouses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1));
    }

    @Test
    @DisplayName("删除仓库 - 成功返回信封")
    void deleteWarehouse_ShouldReturnEnvelope() throws Exception {
        mockMvc.perform(delete("/admin/wms/warehouses/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(warehouseService).deleteWarehouse(1L);
    }

    @Test
    @DisplayName("查询物流列表 - 成功返回分页信封")
    void listShipping_ShouldReturnPagedEnvelope() throws Exception {
        ShippingOrderVO vo = new ShippingOrderVO(1L, 100L, "SF123456", "顺丰", "SHIPPED", FIXED_TIME);
        Page<ShippingOrderVO> page = new Page<>(1, 10, 1L);
        page.setRecords(List.of(vo));

        given(shippingService.listShipping(null, null, 1, 10)).willReturn(page);

        mockMvc.perform(get("/admin/wms/shipping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.meta.page").value(1));
    }

    @Test
    @DisplayName("更新物流状态 - 成功返回信封")
    void updateShippingStatus_ShouldReturnEnvelope() throws Exception {
        ShippingOrderVO vo = new ShippingOrderVO(1L, 100L, "SF123456", "顺丰", "DELIVERED", FIXED_TIME);

        given(shippingService.updateStatus(1L, "DELIVERED")).willReturn(vo);

        mockMvc.perform(put("/admin/wms/shipping/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DELIVERED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("DELIVERED"));
    }
}
