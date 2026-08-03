package com.cloudmart.wms.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.wms.converter.WmsConverter;
import com.cloudmart.wms.dto.*;
import com.cloudmart.wms.service.InboundOrderService;
import com.cloudmart.wms.service.PickOrderService;
import com.cloudmart.wms.service.ShippingService;
import com.cloudmart.wms.service.WarehouseService;
import com.cloudmart.wms.vo.InboundOrderVO;
import com.cloudmart.wms.vo.PickOrderVO;
import com.cloudmart.wms.vo.ShippingOrderVO;
import com.cloudmart.wms.vo.WarehouseVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "仓储管理", description = "仓储管理端接口")
@RestController
@RequestMapping("/admin/wms")
public class AdminWmsController {

    private final PickOrderService pickOrderService;
    private final InboundOrderService inboundOrderService;
    private final WarehouseService warehouseService;
    private final ShippingService shippingService;
    private final WmsConverter wmsConverter;

    public AdminWmsController(PickOrderService pickOrderService,
                              InboundOrderService inboundOrderService,
                              WarehouseService warehouseService,
                              ShippingService shippingService,
                              WmsConverter wmsConverter) {
        this.pickOrderService = pickOrderService;
        this.inboundOrderService = inboundOrderService;
        this.warehouseService = warehouseService;
        this.shippingService = shippingService;
        this.wmsConverter = wmsConverter;
    }

    @Operation(summary = "创建拣货单")
    @PostMapping("/pick-orders")
    public ApiResponse<PickOrderVO> createPickOrder(@Valid @RequestBody CreatePickOrderRequest request) {
        return ApiResponse.ok(wmsConverter.fromPickOrderDTO(pickOrderService.createPickOrder(request)));
    }

    @Operation(summary = "开始拣货")
    @PutMapping("/pick-orders/{id}/start")
    public ApiResponse<PickOrderVO> startPick(
            @PathVariable Long id,
            @RequestParam Long assignedUserId) {
        return ApiResponse.ok(wmsConverter.fromPickOrderDTO(pickOrderService.startPick(id, assignedUserId)));
    }

    @Operation(summary = "确认拣货完成")
    @PutMapping("/pick-orders/{id}/picked")
    public ApiResponse<PickOrderVO> confirmPicked(@PathVariable Long id) {
        return ApiResponse.ok(wmsConverter.fromPickOrderDTO(pickOrderService.confirmPicked(id)));
    }

    @Operation(summary = "确认打包完成")
    @PutMapping("/pick-orders/{id}/packed")
    public ApiResponse<PickOrderVO> confirmPacked(@PathVariable Long id) {
        return ApiResponse.ok(wmsConverter.fromPickOrderDTO(pickOrderService.confirmPacked(id)));
    }

    @Operation(summary = "查询拣货单详情")
    @GetMapping("/pick-orders/{id}")
    public ApiResponse<PickOrderVO> getPickOrder(@PathVariable Long id) {
        return ApiResponse.ok(wmsConverter.fromPickOrderDTO(pickOrderService.getPickOrder(id)));
    }

    @Operation(summary = "查询拣货单列表")
    @GetMapping("/pick-orders")
    public ApiResponse<List<PickOrderVO>> listPickOrders(
            @Parameter(description = "状态") @RequestParam(required = false) String status,
            @Parameter(description = "仓库ID") @RequestParam(required = false) Long warehouseId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int size) {
        IPage<PickOrderDTO> dtoPage = pickOrderService.listPickOrders(status, warehouseId, page, size);
        List<PickOrderVO> voList = dtoPage.getRecords().stream().map(wmsConverter::fromPickOrderDTO).toList();
        ApiResponse.Meta meta = new ApiResponse.Meta(page, size, dtoPage.getTotal());
        return ApiResponse.ok(voList, meta);
    }

    @Operation(summary = "创建入库单")
    @PostMapping("/inbound-orders")
    public ApiResponse<InboundOrderVO> createInboundOrder(@Valid @RequestBody CreateInboundOrderRequest request) {
        return ApiResponse.ok(wmsConverter.fromInboundOrderDTO(inboundOrderService.createInboundOrder(request)));
    }

    @Operation(summary = "收货入库")
    @PutMapping("/inbound-orders/{id}/receive")
    public ApiResponse<InboundOrderVO> receiveItem(
            @PathVariable Long id,
            @RequestParam Long itemId,
            @RequestParam Integer receivedQuantity) {
        return ApiResponse.ok(wmsConverter.fromInboundOrderDTO(inboundOrderService.receiveItem(id, itemId, receivedQuantity)));
    }

    @Operation(summary = "完成入库")
    @PutMapping("/inbound-orders/{id}/complete")
    public ApiResponse<InboundOrderVO> completeInbound(@PathVariable Long id) {
        return ApiResponse.ok(wmsConverter.fromInboundOrderDTO(inboundOrderService.completeInbound(id)));
    }

    @Operation(summary = "查询入库单详情")
    @GetMapping("/inbound-orders/{id}")
    public ApiResponse<InboundOrderVO> getInboundOrder(@PathVariable Long id) {
        return ApiResponse.ok(wmsConverter.fromInboundOrderDTO(inboundOrderService.getInboundOrder(id)));
    }

    @Operation(summary = "查询入库单列表")
    @GetMapping("/inbound-orders")
    public ApiResponse<List<InboundOrderVO>> listInboundOrders(
            @Parameter(description = "状态") @RequestParam(required = false) String status,
            @Parameter(description = "仓库ID") @RequestParam(required = false) Long warehouseId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int size) {
        IPage<InboundOrderDTO> dtoPage = inboundOrderService.listInboundOrders(status, warehouseId, page, size);
        List<InboundOrderVO> voList = dtoPage.getRecords().stream().map(wmsConverter::fromInboundOrderDTO).toList();
        ApiResponse.Meta meta = new ApiResponse.Meta(page, size, dtoPage.getTotal());
        return ApiResponse.ok(voList, meta);
    }

    @Operation(summary = "查询仓库列表")
    @GetMapping("/warehouses")
    public ApiResponse<List<WarehouseVO>> listWarehouses() {
        return ApiResponse.ok(warehouseService.listWarehouses());
    }

    @Operation(summary = "创建仓库")
    @PostMapping("/warehouses")
    public ApiResponse<WarehouseVO> createWarehouse(@Valid @RequestBody CreateWarehouseRequest request) {
        return ApiResponse.ok(warehouseService.createWarehouse(request));
    }

    @Operation(summary = "更新仓库")
    @PutMapping("/warehouses/{id}")
    public ApiResponse<WarehouseVO> updateWarehouse(
            @PathVariable Long id,
            @Valid @RequestBody UpdateWarehouseRequest request) {
        return ApiResponse.ok(warehouseService.updateWarehouse(id, request));
    }

    @Operation(summary = "删除仓库")
    @DeleteMapping("/warehouses/{id}")
    public ApiResponse<Void> deleteWarehouse(@PathVariable Long id) {
        warehouseService.deleteWarehouse(id);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "查询物流列表")
    @GetMapping("/shipping")
    public ApiResponse<List<ShippingOrderVO>> listShipping(
            @Parameter(description = "状态") @RequestParam(required = false) String status,
            @Parameter(description = "仓库ID") @RequestParam(required = false) Long warehouseId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int size) {
        IPage<ShippingOrderVO> voPage = shippingService.listShipping(status, warehouseId, page, size);
        ApiResponse.Meta meta = new ApiResponse.Meta(page, size, voPage.getTotal());
        return ApiResponse.ok(voPage.getRecords(), meta);
    }

    @Operation(summary = "更新物流状态")
    @PutMapping("/shipping/{id}/status")
    public ApiResponse<ShippingOrderVO> updateShippingStatus(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        String status = (String) body.get("status");
        return ApiResponse.ok(shippingService.updateStatus(id, status));
    }
}
