package com.cloudmart.wms.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.wms.converter.WmsConverter;
import com.cloudmart.wms.dto.CreateInboundOrderRequest;
import com.cloudmart.wms.dto.CreatePickOrderRequest;
import com.cloudmart.wms.dto.CreateWarehouseRequest;
import com.cloudmart.wms.dto.UpdateWarehouseRequest;
import com.cloudmart.wms.service.InboundOrderService;
import com.cloudmart.wms.service.PickOrderService;
import com.cloudmart.wms.service.WarehouseService;
import com.cloudmart.wms.vo.InboundOrderVO;
import com.cloudmart.wms.vo.PickOrderVO;
import com.cloudmart.wms.vo.WarehouseVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/warehouses")
@Tag(name = "仓库管理", description = "仓库的增删改查接口")
public class WarehouseController {

    private final WarehouseService warehouseService;
    private final PickOrderService pickOrderService;
    private final InboundOrderService inboundOrderService;
    private final WmsConverter wmsConverter;

    public WarehouseController(WarehouseService warehouseService,
                               PickOrderService pickOrderService,
                               InboundOrderService inboundOrderService,
                               WmsConverter wmsConverter) {
        this.warehouseService = warehouseService;
        this.pickOrderService = pickOrderService;
        this.inboundOrderService = inboundOrderService;
        this.wmsConverter = wmsConverter;
    }

    @PostMapping
    @Operation(summary = "创建仓库")
    public ApiResponse<WarehouseVO> createWarehouse(@Valid @RequestBody CreateWarehouseRequest request) {
        return ApiResponse.ok(warehouseService.createWarehouse(request));
    }

    @GetMapping
    @Operation(summary = "查询仓库列表")
    public ApiResponse<List<WarehouseVO>> listWarehouses() {
        return ApiResponse.ok(warehouseService.listWarehouses());
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询仓库详情")
    public ApiResponse<WarehouseVO> getWarehouse(@PathVariable Long id) {
        return ApiResponse.ok(warehouseService.getWarehouse(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新仓库")
    public ApiResponse<WarehouseVO> updateWarehouse(
            @PathVariable Long id,
            @Valid @RequestBody UpdateWarehouseRequest request) {
        return ApiResponse.ok(warehouseService.updateWarehouse(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除仓库")
    public ApiResponse<Void> deleteWarehouse(@PathVariable Long id) {
        warehouseService.deleteWarehouse(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/pick-orders")
    @Operation(summary = "创建拣货单")
    public ApiResponse<PickOrderVO> createPickOrder(@Valid @RequestBody CreatePickOrderRequest request) {
        return ApiResponse.ok(wmsConverter.fromPickOrderDTO(pickOrderService.createPickOrder(request)));
    }

    @PostMapping("/inbound-orders")
    @Operation(summary = "创建入库单")
    public ApiResponse<InboundOrderVO> createInboundOrder(@Valid @RequestBody CreateInboundOrderRequest request) {
        return ApiResponse.ok(wmsConverter.fromInboundOrderDTO(inboundOrderService.createInboundOrder(request)));
    }
}
