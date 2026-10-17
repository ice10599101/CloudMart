package com.cloudmart.wms.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.wms.converter.WmsConverter;
import com.cloudmart.wms.dto.CreateInboundOrderRequest;
import com.cloudmart.wms.dto.CreatePickOrderRequest;
import com.cloudmart.wms.dto.InboundOrderDTO;
import com.cloudmart.wms.dto.PickOrderDTO;
import com.cloudmart.wms.service.InboundOrderService;
import com.cloudmart.wms.service.PickOrderService;
import com.cloudmart.wms.service.ShippingService;
import com.cloudmart.wms.service.WarehouseService;
import com.cloudmart.wms.vo.InboundOrderDetailVO;
import com.cloudmart.wms.vo.InboundOrderVO;
import com.cloudmart.wms.vo.PickOrderDetailVO;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TRADE-03：仓储管理端契约——列表/详情 VO 分离、仓库全链名称补齐、
 * 开始拣货操作者取认证身份、物流单号分列与订单维度筛选。
 */
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
        // 仓库全链名称补齐：默认空仓列表（enrich 不受影响）
        given(warehouseService.listWarehouses()).willReturn(List.of());
    }

    private void stubWarehouseName(Long warehouseId, String name) {
        given(warehouseService.listWarehouses()).willReturn(List.of(
                new WarehouseVO(warehouseId, name, "北京市", "010-12345678", 1, FIXED_TIME)));
    }

    @Test
    @DisplayName("创建拣货单 - 成功返回详情信封")
    void createPickOrder_ShouldReturnEnvelope() throws Exception {
        PickOrderDTO dto = new PickOrderDTO(1L, 100L, 1L, "PENDING", null, null, null, null, FIXED_TIME, List.of());
        PickOrderDetailVO vo = new PickOrderDetailVO(1L, 100L, 1L, null, "PENDING", null,
                null, null, null, FIXED_TIME, List.of());

        given(pickOrderService.createPickOrder(Mockito.any(CreatePickOrderRequest.class))).willReturn(dto);
        given(wmsConverter.toPickOrderDetailVO(dto)).willReturn(vo);

        mockMvc.perform(post("/admin/wms/pick-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":100,\"warehouseId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("开始拣货 - 缺省分配给当前管理员（X-User-Id），不再要求手填人员 ID")
    void startPick_defaultsToOperator() throws Exception {
        PickOrderDTO dto = new PickOrderDTO(1L, 100L, 1L, "PICKING", 9L, FIXED_TIME, null, null, FIXED_TIME, List.of());
        PickOrderDetailVO vo = new PickOrderDetailVO(1L, 100L, 1L, "主仓", "PICKING", 9L,
                FIXED_TIME, null, null, FIXED_TIME, List.of());

        given(pickOrderService.startPick(1L, 9L)).willReturn(dto);
        given(wmsConverter.toPickOrderDetailVO(dto)).willReturn(vo);
        stubWarehouseName(1L, "主仓");

        mockMvc.perform(put("/admin/wms/pick-orders/1/start")
                        .header("X-User-Id", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PICKING"))
                .andExpect(jsonPath("$.data.assignedUserId").value(9))
                .andExpect(jsonPath("$.data.warehouseName").value("主仓"));
        verify(pickOrderService).startPick(1L, 9L);
    }

    @Test
    @DisplayName("开始拣货 - 缺身份且缺 assignedUserId → 显式拒绝")
    void startPick_missingOperator_rejected() throws Exception {
        mockMvc.perform(put("/admin/wms/pick-orders/1/start"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PICK_OPERATOR_REQUIRED"));
    }

    @Test
    @DisplayName("确认拣货完成 - 成功返回详情信封")
    void confirmPicked_ShouldReturnEnvelope() throws Exception {
        PickOrderDTO dto = new PickOrderDTO(1L, 100L, 1L, "PICKED", 10L, FIXED_TIME, FIXED_TIME, null, FIXED_TIME, List.of());
        PickOrderDetailVO vo = new PickOrderDetailVO(1L, 100L, 1L, null, "PICKED", 10L,
                FIXED_TIME, FIXED_TIME, null, FIXED_TIME, List.of());

        given(pickOrderService.confirmPicked(1L)).willReturn(dto);
        given(wmsConverter.toPickOrderDetailVO(dto)).willReturn(vo);

        mockMvc.perform(put("/admin/wms/pick-orders/1/picked"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PICKED"));
    }

    @Test
    @DisplayName("确认打包完成 - 成功返回详情信封")
    void confirmPacked_ShouldReturnEnvelope() throws Exception {
        PickOrderDTO dto = new PickOrderDTO(1L, 100L, 1L, "PACKED", 10L, FIXED_TIME, FIXED_TIME, "已打包", FIXED_TIME, List.of());
        PickOrderDetailVO vo = new PickOrderDetailVO(1L, 100L, 1L, null, "PACKED", 10L,
                FIXED_TIME, FIXED_TIME, "已打包", FIXED_TIME, List.of());

        given(pickOrderService.confirmPacked(1L)).willReturn(dto);
        given(wmsConverter.toPickOrderDetailVO(dto)).willReturn(vo);

        mockMvc.perform(put("/admin/wms/pick-orders/1/packed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PACKED"));
    }

    @Test
    @DisplayName("查询拣货单列表 - 分页信封 + 仓库名称补齐")
    void listPickOrders_ShouldReturnPagedEnvelope() throws Exception {
        PickOrderDTO dto = new PickOrderDTO(1L, 100L, 1L, "PENDING", null, null, null, null, FIXED_TIME, List.of());
        Page<PickOrderDTO> page = new Page<>(1, 10, 1L);
        page.setRecords(List.of(dto));
        PickOrderVO vo = new PickOrderVO(1L, 100L, 1L, null, "PENDING", null, FIXED_TIME);

        given(pickOrderService.listPickOrders(null, null, 1, 10)).willReturn(page);
        given(wmsConverter.toPickOrderVO(dto)).willReturn(vo);
        stubWarehouseName(1L, "主仓");

        mockMvc.perform(get("/admin/wms/pick-orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].warehouseName").value("主仓"))
                .andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    @DisplayName("创建入库单 - 成功返回详情信封")
    void createInboundOrder_ShouldReturnEnvelope() throws Exception {
        InboundOrderDTO dto = new InboundOrderDTO(1L, 1L, "PURCHASE", "PO-001", "PENDING",
                100, 0, null, null, null, FIXED_TIME, List.of());
        InboundOrderDetailVO vo = new InboundOrderDetailVO(1L, 1L, null, "PURCHASE", "PO-001",
                "PENDING", 100, 0, null, null, null, FIXED_TIME, List.of());

        given(inboundOrderService.createInboundOrder(Mockito.any(CreateInboundOrderRequest.class))).willReturn(dto);
        given(wmsConverter.toInboundOrderDetailVO(dto)).willReturn(vo);

        mockMvc.perform(post("/admin/wms/inbound-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"warehouseId\":1,\"type\":\"PURCHASE\",\"items\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("收货入库 - C19 统一请求体 + receiptId 幂等 + 操作者头")
    void receiveItem_ShouldReturnEnvelope() throws Exception {
        InboundOrderDTO dto = new InboundOrderDTO(1L, 1L, "PURCHASE", "PO-001", "RECEIVING",
                100, 50, null, null, null, FIXED_TIME, List.of());
        InboundOrderDetailVO vo = new InboundOrderDetailVO(1L, 1L, null, "PURCHASE", "PO-001",
                "RECEIVING", 100, 50, null, null, null, FIXED_TIME, List.of());

        given(inboundOrderService.receiveItem(1L, 10L, 50, "RCP-1001", 9L, "PASSED", "PURCHASE_INBOUND"))
                .willReturn(dto);
        given(wmsConverter.toInboundOrderDetailVO(dto)).willReturn(vo);

        mockMvc.perform(put("/admin/wms/inbound-orders/1/receive")
                        .header("X-User-Id", "9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itemId":10,"receivedQuantity":50,"receiptId":"RCP-1001","qualityResult":"PASSED"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("RECEIVING"));
        verify(inboundOrderService).receiveItem(1L, 10L, 50, "RCP-1001", 9L, "PASSED", "PURCHASE_INBOUND");
    }

    @Test
    @DisplayName("完成入库 - 成功返回详情信封")
    void completeInbound_ShouldReturnEnvelope() throws Exception {
        InboundOrderDTO dto = new InboundOrderDTO(1L, 1L, "PURCHASE", "PO-001", "COMPLETED",
                100, 100, null, FIXED_TIME, null, FIXED_TIME, List.of());
        InboundOrderDetailVO vo = new InboundOrderDetailVO(1L, 1L, null, "PURCHASE", "PO-001",
                "COMPLETED", 100, 100, null, FIXED_TIME, null, FIXED_TIME, List.of());

        given(inboundOrderService.completeInbound(1L)).willReturn(dto);
        given(wmsConverter.toInboundOrderDetailVO(dto)).willReturn(vo);

        mockMvc.perform(put("/admin/wms/inbound-orders/1/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("查询仓库列表 - 成功返回信封")
    void listWarehouses_ShouldReturnEnvelope() throws Exception {
        mockMvc.perform(get("/admin/wms/warehouses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
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
    @DisplayName("查询物流列表 - orderId 筛选全链透传")
    void listShipping_orderIdFilter_passedThrough() throws Exception {
        ShippingOrderVO vo = new ShippingOrderVO(1L, 100L, 1L, "SF123456", "YT4160001",
                "顺丰", "SHIPPED", FIXED_TIME, FIXED_TIME);
        Page<ShippingOrderVO> page = new Page<>(1, 10, 1L);
        page.setRecords(List.of(vo));

        given(shippingService.listShipping(null, null, 100L, 1, 10)).willReturn(page);

        mockMvc.perform(get("/admin/wms/shipping").param("orderId", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].shippingNo").value("SF123456"))
                .andExpect(jsonPath("$.data[0].trackingNo").value("YT4160001"))
                .andExpect(jsonPath("$.meta.page").value(1));
        verify(shippingService).listShipping(isNull(), isNull(), eq(100L), eq(1), eq(10));
    }

    @Test
    @DisplayName("更新物流状态 - 成功返回信封")
    void updateShippingStatus_ShouldReturnEnvelope() throws Exception {
        ShippingOrderVO vo = new ShippingOrderVO(1L, 100L, 1L, "SF123456", "YT4160001",
                "顺丰", "DELIVERED", FIXED_TIME, FIXED_TIME);

        given(shippingService.updateStatus(1L, "DELIVERED")).willReturn(vo);

        mockMvc.perform(put("/admin/wms/shipping/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DELIVERED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("DELIVERED"));
    }
}
