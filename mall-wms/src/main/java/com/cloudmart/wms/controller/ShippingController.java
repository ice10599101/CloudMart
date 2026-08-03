package com.cloudmart.wms.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.wms.dto.CreateShippingRequest;
import com.cloudmart.wms.service.ShippingService;
import com.cloudmart.wms.vo.ShippingOrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/shipping")
@Tag(name = "物流管理", description = "物流订单的创建和查询")
public class ShippingController {

    private final ShippingService shippingService;

    public ShippingController(ShippingService shippingService) {
        this.shippingService = shippingService;
    }

    @PostMapping
    @Operation(summary = "创建物流订单")
    public ApiResponse<ShippingOrderVO> createShippingOrder(@Valid @RequestBody CreateShippingRequest request) {
        return ApiResponse.ok(shippingService.createShipping(request));
    }

    @GetMapping("/order/{orderId}")
    @Operation(summary = "根据订单ID查询物流信息")
    public ApiResponse<ShippingOrderVO> getByOrderId(@PathVariable Long orderId) {
        return ApiResponse.ok(shippingService.getByOrderId(orderId));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "更新物流状态")
    public ApiResponse<ShippingOrderVO> updateStatus(
            @PathVariable Long id,
            @Parameter(description = "物流状态") @RequestParam String status) {
        return ApiResponse.ok(shippingService.updateStatus(id, status));
    }
}
