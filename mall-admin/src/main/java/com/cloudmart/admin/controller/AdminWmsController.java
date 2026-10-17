package com.cloudmart.admin.controller;

import com.cloudmart.admin.feign.WmsFeignClient;
import com.cloudmart.common.annotation.OperLog;
import com.cloudmart.common.annotation.RequiresPermission;
import com.cloudmart.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 业务管理-仓储物流域：拣货单、入库单、物流单、仓库。
 * <p>路径前缀保持历史契约 {@code /business}，与拆分前 AdminBusinessController 完全一致。
 */
@RestController
@RequestMapping("/business")
@Tag(name = "业务管理", description = "管理后台对业务模块的代理接口：仓储/物流/仓库")
public class AdminWmsController {

    private final WmsFeignClient wmsFeignClient;

    public AdminWmsController(WmsFeignClient wmsFeignClient) {
        this.wmsFeignClient = wmsFeignClient;
    }

    // ==================== 仓储管理 ====================

    @GetMapping("/wms/pick-orders")
    @RequiresPermission("business:wms:list")
    @Operation(summary = "拣货单列表", description = "查询拣货单列表")
    public ApiResponse<Object> listPickOrders(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "warehouseId", required = false) Long warehouseId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        return wmsFeignClient.listPickOrders(status, warehouseId, page, pageSize);
    }

    @GetMapping("/wms/pick-orders/{id}")
    @RequiresPermission("business:wms:query")
    @Operation(summary = "拣货单详情", description = "查询拣货单详情")
    public ApiResponse<Object> getPickOrder(@PathVariable Long id) {
        return wmsFeignClient.getPickOrder(id);
    }

    @PutMapping("/wms/pick-orders/{id}/start")
    @OperLog(title = "仓储管理", businessType = 2)
    @RequiresPermission("business:wms:edit")
    @Operation(summary = "开始拣货", description = "分配拣货员并开始拣货")
    public ApiResponse<Object> startPick(@PathVariable Long id,
                                         @RequestBody(required = false) Map<String, Object> body) {
        // TRADE-03：assignedUserId 可空——mall-wms 缺省取透传的管理员身份（不要求前端手填人员 ID）
        return wmsFeignClient.startPick(id, body);
    }

    @PutMapping("/wms/pick-orders/{id}/picked")
    @OperLog(title = "仓储管理", businessType = 2)
    @RequiresPermission("business:wms:edit")
    @Operation(summary = "确认拣货完成", description = "确认拣货完成")
    public ApiResponse<Object> confirmPicked(@PathVariable Long id) {
        return wmsFeignClient.confirmPicked(id);
    }

    @PutMapping("/wms/pick-orders/{id}/packed")
    @OperLog(title = "仓储管理", businessType = 2)
    @RequiresPermission("business:wms:edit")
    @Operation(summary = "确认打包完成", description = "确认打包完成")
    public ApiResponse<Object> confirmPacked(@PathVariable Long id) {
        return wmsFeignClient.confirmPacked(id);
    }

    @GetMapping("/wms/inbound-orders")
    @RequiresPermission("business:wms:list")
    @Operation(summary = "入库单列表", description = "查询入库单列表")
    public ApiResponse<Object> listInboundOrders(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "warehouseId", required = false) Long warehouseId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        return wmsFeignClient.listInboundOrders(status, warehouseId, page, pageSize);
    }

    @GetMapping("/wms/inbound-orders/{id}")
    @RequiresPermission("business:wms:query")
    @Operation(summary = "入库单详情", description = "查询入库单详情")
    public ApiResponse<Object> getInboundOrder(@PathVariable Long id) {
        return wmsFeignClient.getInboundOrder(id);
    }

    @PostMapping("/wms/pick-orders")
    @OperLog(title = "仓储管理", businessType = 1)
    @RequiresPermission("business:wms:edit")
    @Operation(summary = "创建拣货单", description = "按订单+仓库生成拣货单")
    public ApiResponse<Object> createPickOrder(@RequestBody Map<String, Object> body) {
        return wmsFeignClient.createPickOrder(body);
    }

    @PostMapping("/wms/inbound-orders")
    @OperLog(title = "仓储管理", businessType = 1)
    @RequiresPermission("business:wms:edit")
    @Operation(summary = "创建入库单", description = "创建 PURCHASE/RETURN/TRANSFER 类型入库单（含明细）")
    public ApiResponse<Object> createInboundOrder(@RequestBody Map<String, Object> body) {
        return wmsFeignClient.createInboundOrder(body);
    }

    @PutMapping("/wms/inbound-orders/{id}/receive")
    @OperLog(title = "仓储管理", businessType = 2)
    @RequiresPermission("business:wms:edit")
    @Operation(summary = "收货入库", description = "C19：统一收货请求体；receiptId 由后台确认时生成并保留，重试原样回传")
    public ApiResponse<Object> receiveInboundItem(@PathVariable Long id,
                                                  @Valid @RequestBody ReceiveInboundItemRequest request) {
        return wmsFeignClient.receiveInboundItem(id, Map.of(
                "itemId", request.itemId(),
                "receivedQuantity", request.receivedQuantity(),
                "receiptId", request.receiptId(),
                "qualityResult", request.qualityResult() == null ? "PASSED" : request.qualityResult()));
    }

    /** C19 管理代理收货请求（操作者由管理员身份头确定，禁止外部自报） */
    public record ReceiveInboundItemRequest(
            @Schema(description = "入库明细 ID", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "itemId 不能为空")
            Long itemId,
            @Schema(description = "本次实收数量（正整数）", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "receivedQuantity 不能为空")
            @Min(value = 1, message = "收货数量必须为正数")
            Integer receivedQuantity,
            @Schema(description = "收货流水 ID（后台生成，重试原样回传）", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "receiptId 不能为空")
            @Size(max = 64, message = "receiptId 最长 64 字符")
            String receiptId,
            @Schema(description = "质检结果：PASSED 合格 / QUARANTINE 隔离；缺省 PASSED")
            String qualityResult
    ) {
    }

    @PutMapping("/wms/inbound-orders/{id}/complete")
    @OperLog(title = "仓储管理", businessType = 2)
    @RequiresPermission("business:wms:edit")
    @Operation(summary = "完成入库", description = "全部明细收货完成后关闭入库单")
    public ApiResponse<Object> completeInbound(@PathVariable Long id) {
        return wmsFeignClient.completeInbound(id);
    }

    // ==================== 物流管理 ====================

    @GetMapping("/wms/shipping")
    @RequiresPermission("business:shipping:list")
    @Operation(summary = "物流列表", description = "查询物流订单列表")
    public ApiResponse<Object> listShipping(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "warehouseId", required = false) Long warehouseId,
            @RequestParam(value = "orderId", required = false) Long orderId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        // TRADE-03：订单维度筛选全链透传
        return wmsFeignClient.listShipping(status, warehouseId, orderId, page, pageSize);
    }

    @PutMapping("/wms/shipping/{id}/status")
    @OperLog(title = "仓储管理", businessType = 2)
    @RequiresPermission("business:shipping:edit")
    @Operation(summary = "更新物流状态", description = "更新物流订单状态")
    public ApiResponse<Object> updateShippingStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return wmsFeignClient.updateShippingStatus(id, body);
    }

    // ==================== 仓库管理 ====================

    @GetMapping("/wms/warehouses")
    @RequiresPermission("business:warehouse:list")
    @Operation(summary = "仓库列表", description = "查询仓库列表")
    public ApiResponse<Object> listWarehouses() {
        return wmsFeignClient.listWarehouses();
    }

    @PostMapping("/wms/warehouses")
    @OperLog(title = "仓储管理", businessType = 1)
    @RequiresPermission("business:warehouse:add")
    @Operation(summary = "创建仓库", description = "创建新仓库")
    public ApiResponse<Object> createWarehouse(@RequestBody Map<String, Object> body) {
        return wmsFeignClient.createWarehouse(body);
    }

    @PutMapping("/wms/warehouses/{id}")
    @OperLog(title = "仓储管理", businessType = 2)
    @RequiresPermission("business:warehouse:edit")
    @Operation(summary = "更新仓库", description = "更新仓库信息")
    public ApiResponse<Object> updateWarehouse(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return wmsFeignClient.updateWarehouse(id, body);
    }

    @DeleteMapping("/wms/warehouses/{id}")
    @OperLog(title = "仓储管理", businessType = 3)
    @RequiresPermission("business:warehouse:remove")
    @Operation(summary = "删除仓库", description = "删除仓库")
    public ApiResponse<Void> deleteWarehouse(@PathVariable Long id) {
        return wmsFeignClient.deleteWarehouse(id);
    }
}
