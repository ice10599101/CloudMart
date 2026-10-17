package com.cloudmart.wms.controller;

import org.springframework.security.access.prepost.PreAuthorize;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.util.Ids;
import com.cloudmart.wms.converter.WmsConverter;
import com.cloudmart.wms.dto.*;
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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 仓储管理端接口（TRADE-03：列表/详情 VO 分离、仓库全链名称补齐、
 * 开始拣货操作者取认证身份、物流订单号分列与订单维度筛选）。
 */
@Tag(name = "仓储管理", description = "仓储管理端接口")
@RestController
@RequestMapping("/admin/wms")
@PreAuthorize("hasRole('INTERNAL')")
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

    // ==================== 拣货单 ====================

    @Operation(summary = "创建拣货单")
    @PostMapping("/pick-orders")
    public ApiResponse<PickOrderDetailVO> createPickOrder(@Valid @RequestBody CreatePickOrderRequest request) {
        return ApiResponse.ok(enrich(wmsConverter.toPickOrderDetailVO(pickOrderService.createPickOrder(request))));
    }

    /**
     * 开始拣货（TRADE-03）：typed body，assignedUserId 可空——缺省分配给当前已认证管理员
     * （X-User-Id，网关注入），不再要求前端手填人员 ID。
     */
    @Operation(summary = "开始拣货", description = "assignedUserId 缺省取当前管理员身份；显式指定即派给该操作员")
    @PutMapping("/pick-orders/{id}/start")
    public ApiResponse<PickOrderDetailVO> startPick(
            @PathVariable Long id,
            @RequestBody(required = false) StartPickRequest body,
            @Parameter(hidden = true)
            @RequestHeader(value = SecurityConstants.USER_ID_HEADER, required = false) String operatorId) {
        Long assignedUserId = body == null ? null : body.assignedUserId();
        if (assignedUserId == null) {
            assignedUserId = Ids.parse(operatorId);
        }
        if (assignedUserId == null) {
            throw new BusinessException("PICK_OPERATOR_REQUIRED", "缺少拣货员：请指定 assignedUserId 或携带管理员身份");
        }
        return ApiResponse.ok(enrich(wmsConverter.toPickOrderDetailVO(pickOrderService.startPick(id, assignedUserId))));
    }

    /** 开始拣货请求（assignedUserId 可空：缺省当前管理员） */
    public record StartPickRequest(Long assignedUserId) {
    }

    @Operation(summary = "确认拣货完成")
    @PutMapping("/pick-orders/{id}/picked")
    public ApiResponse<PickOrderDetailVO> confirmPicked(@PathVariable Long id) {
        return ApiResponse.ok(enrich(wmsConverter.toPickOrderDetailVO(pickOrderService.confirmPicked(id))));
    }

    @Operation(summary = "确认打包完成")
    @PutMapping("/pick-orders/{id}/packed")
    public ApiResponse<PickOrderDetailVO> confirmPacked(@PathVariable Long id) {
        return ApiResponse.ok(enrich(wmsConverter.toPickOrderDetailVO(pickOrderService.confirmPacked(id))));
    }

    @Operation(summary = "查询拣货单详情", description = "含行项数量/已拣数量与分配拣货员")
    @GetMapping("/pick-orders/{id}")
    public ApiResponse<PickOrderDetailVO> getPickOrder(@PathVariable Long id) {
        return ApiResponse.ok(enrich(wmsConverter.toPickOrderDetailVO(pickOrderService.getPickOrder(id))));
    }

    @Operation(summary = "查询拣货单列表")
    @GetMapping("/pick-orders")
    public ApiResponse<List<PickOrderVO>> listPickOrders(
            @Parameter(description = "状态") @RequestParam(required = false) String status,
            @Parameter(description = "仓库ID") @RequestParam(required = false) Long warehouseId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int size) {
        IPage<PickOrderDTO> dtoPage = pickOrderService.listPickOrders(status, warehouseId, page, size);
        List<PickOrderVO> voList = dtoPage.getRecords().stream()
                .map(wmsConverter::toPickOrderVO)
                .map(this::enrich)
                .toList();
        ApiResponse.Meta meta = new ApiResponse.Meta(page, size, dtoPage.getTotal());
        return ApiResponse.ok(voList, meta);
    }

    // ==================== 入库单 ====================

    @Operation(summary = "创建入库单")
    @PostMapping("/inbound-orders")
    public ApiResponse<InboundOrderDetailVO> createInboundOrder(@Valid @RequestBody CreateInboundOrderRequest request) {
        return ApiResponse.ok(enrich(wmsConverter.toInboundOrderDetailVO(inboundOrderService.createInboundOrder(request))));
    }

    @Operation(summary = "收货入库（C19 统一请求体）", description = "receiptId 由后台确认时生成、重试原样回传（幂等）；"
            + "operatorId 取自 X-User-Id 管理身份头；本入口 bizSource 固定 PURCHASE")
    @PutMapping("/inbound-orders/{id}/receive")
    public ApiResponse<InboundOrderDetailVO> receiveItem(
            @PathVariable Long id,
            @Parameter(hidden = true)
            @RequestHeader(value = SecurityConstants.USER_ID_HEADER, required = false) Long operatorId,
            @Valid @RequestBody ReceiveItemRequest request) {
        return ApiResponse.ok(enrich(wmsConverter.toInboundOrderDetailVO(inboundOrderService.receiveItem(
                id, request.itemId(), request.receivedQuantity(),
                request.receiptId(), operatorId, request.qualityResult(),
                com.cloudmart.wms.entity.InboundReceipt.SOURCE_PURCHASE))));
    }

    @Operation(summary = "完成入库", description = "未收齐需差异审批（INBOUND_SHORTAGE）")
    @PutMapping("/inbound-orders/{id}/complete")
    public ApiResponse<InboundOrderDetailVO> completeInbound(@PathVariable Long id) {
        return ApiResponse.ok(enrich(wmsConverter.toInboundOrderDetailVO(inboundOrderService.completeInbound(id))));
    }

    @Operation(summary = "查询入库单详情", description = "含明细：itemId/skuId/商品/计划/已收/剩余")
    @GetMapping("/inbound-orders/{id}")
    public ApiResponse<InboundOrderDetailVO> getInboundOrder(@PathVariable Long id) {
        return ApiResponse.ok(enrich(wmsConverter.toInboundOrderDetailVO(inboundOrderService.getInboundOrder(id))));
    }

    @Operation(summary = "查询入库单列表")
    @GetMapping("/inbound-orders")
    public ApiResponse<List<InboundOrderVO>> listInboundOrders(
            @Parameter(description = "状态") @RequestParam(required = false) String status,
            @Parameter(description = "仓库ID") @RequestParam(required = false) Long warehouseId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int size) {
        IPage<InboundOrderDTO> dtoPage = inboundOrderService.listInboundOrders(status, warehouseId, page, size);
        List<InboundOrderVO> voList = dtoPage.getRecords().stream()
                .map(wmsConverter::toInboundOrderVO)
                .map(this::enrich)
                .toList();
        ApiResponse.Meta meta = new ApiResponse.Meta(page, size, dtoPage.getTotal());
        return ApiResponse.ok(voList, meta);
    }

    // ==================== 仓库 ====================

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

    @Operation(summary = "更新仓库", description = "停用有未完成单据时拒绝（TRADE-03）")
    @PutMapping("/warehouses/{id}")
    public ApiResponse<WarehouseVO> updateWarehouse(
            @PathVariable Long id,
            @Valid @RequestBody UpdateWarehouseRequest request) {
        return ApiResponse.ok(warehouseService.updateWarehouse(id, request));
    }

    @Operation(summary = "删除仓库", description = "有历史引用改为停用（TRADE-03：删除仅限零引用）")
    @DeleteMapping("/warehouses/{id}")
    public ApiResponse<Void> deleteWarehouse(@PathVariable Long id) {
        warehouseService.deleteWarehouse(id);
        return ApiResponse.ok(null);
    }

    // ==================== 物流 ====================

    @Operation(summary = "查询物流列表", description = "内部出库单号（shippingNo）与承运商运单号（trackingNo）分列；"
            + "支持 orderId/status/warehouseId 筛选")
    @GetMapping("/shipping")
    public ApiResponse<List<ShippingOrderVO>> listShipping(
            @Parameter(description = "状态") @RequestParam(required = false) String status,
            @Parameter(description = "仓库ID") @RequestParam(required = false) Long warehouseId,
            @Parameter(description = "订单ID") @RequestParam(required = false) Long orderId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int size) {
        IPage<ShippingOrderVO> voPage = shippingService.listShipping(status, warehouseId, orderId, page, size);
        ApiResponse.Meta meta = new ApiResponse.Meta(page, size, voPage.getTotal());
        return ApiResponse.ok(voPage.getRecords(), meta);
    }

    @Operation(summary = "更新物流状态", description = "状态机白名单 + 合法迁移；无运单不得 SHIPPED")
    @PutMapping("/shipping/{id}/status")
    public ApiResponse<ShippingOrderVO> updateShippingStatus(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        String status = (String) body.get("status");
        return ApiResponse.ok(shippingService.updateStatus(id, status));
    }

    // ==================== 私有辅助 ====================

    /** 仓库全链名称补齐：一次列表查询构建 id→name 映射（VO 的 warehouseName 不再恒 null） */
    private Map<Long, String> warehouseNameMap() {
        return warehouseService.listWarehouses().stream()
                .collect(Collectors.toMap(WarehouseVO::id, WarehouseVO::name, (a, b) -> a));
    }

    private PickOrderDetailVO enrich(PickOrderDetailVO vo) {
        String name = warehouseNameMap().get(vo.warehouseId());
        return new PickOrderDetailVO(vo.id(), vo.orderId(), vo.warehouseId(), name, vo.status(),
                vo.assignedUserId(), vo.pickTime(), vo.packedTime(), vo.remark(), vo.createdAt(), vo.items());
    }

    private PickOrderVO enrich(PickOrderVO vo) {
        String name = warehouseNameMap().get(vo.warehouseId());
        return new PickOrderVO(vo.id(), vo.orderId(), vo.warehouseId(), name, vo.status(),
                vo.assignedUserId(), vo.createdAt());
    }

    private InboundOrderDetailVO enrich(InboundOrderDetailVO vo) {
        String name = warehouseNameMap().get(vo.warehouseId());
        return new InboundOrderDetailVO(vo.id(), vo.warehouseId(), name, vo.type(), vo.referenceNo(),
                vo.status(), vo.totalQuantity(), vo.receivedQuantity(), vo.operatorUserId(),
                vo.completedTime(), vo.remark(), vo.createdAt(), vo.items());
    }

    private InboundOrderVO enrich(InboundOrderVO vo) {
        String name = warehouseNameMap().get(vo.warehouseId());
        return new InboundOrderVO(vo.id(), vo.warehouseId(), name, vo.referenceNo(), vo.status(),
                vo.totalQuantity(), vo.receivedQuantity(), vo.operatorUserId(), vo.completedTime(),
                vo.createdAt());
    }
}
