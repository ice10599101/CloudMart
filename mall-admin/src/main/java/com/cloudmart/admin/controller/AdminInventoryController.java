package com.cloudmart.admin.controller;

import com.cloudmart.admin.dto.InventoryAdjustRequest;
import com.cloudmart.admin.dto.InventoryInitRequest;
import com.cloudmart.admin.dto.feign.InventoryAdjustmentDTO;
import com.cloudmart.admin.dto.feign.InventoryDTO;
import com.cloudmart.admin.dto.feign.InventoryRowDTO;
import com.cloudmart.admin.dto.feign.ProductDTO;
import com.cloudmart.admin.dto.feign.SkuBatchDTO;
import com.cloudmart.admin.feign.InventoryFeignClient;
import com.cloudmart.admin.feign.ProductFeignClient;
import com.cloudmart.common.annotation.OperLog;
import com.cloudmart.common.annotation.RequiresPermission;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.common.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 业务管理-库存域：SKU 库存查询（含商品名补齐）、初始化（归属校验）与盘点调整单。
 * <p>路径前缀保持历史契约 {@code /business}，与拆分前 AdminBusinessController 完全一致。
 * TRADE-02：初始化仅一次（已有库存服务端拒绝），一切修正走调整单（幂等/CAS/审计），无直接覆盖入口。</p>
 */
@Slf4j
@RestController
@RequestMapping("/business")
@RequiredArgsConstructor
@Tag(name = "业务管理", description = "管理后台对业务模块的代理接口：库存管理")
public class AdminInventoryController {

    private final InventoryFeignClient inventoryFeignClient;
    private final ProductFeignClient productFeignClient;

    // ==================== 库存 ====================

    @GetMapping("/inventory")
    @RequiresPermission("business:inventory:list")
    @Operation(summary = "库存列表", description = "分页查询库存（支持按商品ID筛选），"
            + "批量补齐商品名/SKU 规格（单次批量调用；补名失败不阻断库存事实返回）")
    public ApiResponse<List<InventoryRowDTO>> listInventory(
            @RequestParam(value = "productId", required = false) Long productId,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize) {
        ApiResponse<List<InventoryDTO>> response = inventoryFeignClient.listInventory(productId, page, pageSize);
        if (!response.success() || response.data() == null) {
            return failOf(response, "INVENTORY_SERVICE_UNAVAILABLE", "库存服务不可用，请稍后重试");
        }
        List<InventoryRowDTO> rows = enrichWithProductInfo(response.data());
        return ApiResponse.ok(rows, response.meta());
    }

    @GetMapping("/inventory/{skuId}")
    @RequiresPermission("business:inventory:list")
    @Operation(summary = "查询库存", description = "根据SKU ID查询库存（含版本号，供调整单 CAS）")
    public ApiResponse<InventoryRowDTO> getInventory(@PathVariable Long skuId) {
        ApiResponse<InventoryDTO> response = inventoryFeignClient.getInventory(skuId);
        if (!response.success() || response.data() == null) {
            return failOf(response, "INVENTORY_SERVICE_UNAVAILABLE", "库存服务不可用，请稍后重试");
        }
        return ApiResponse.ok(enrichWithProductInfo(List.of(response.data())).get(0));
    }

    @PostMapping("/inventory/init")
    @OperLog(title = "库存管理", businessType = 1)
    @RequiresPermission("business:inventory:init")
    @Operation(summary = "初始化库存", description = "仅对无库存记录的 SKU 生效（已有库存服务端拒绝）；"
            + "校验 SKU 确实属于该商品（TRADE-02，防止串号初始化）")
    public ApiResponse<Void> initInventory(@Valid @RequestBody InventoryInitRequest request,
                                           @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId) {
        validateSkuBelongsToProduct(request.productId(), request.skuId());
        return inventoryFeignClient.initStock(request.skuId(), request.productId(), request.stock());
    }

    // ==================== TRADE-02：盘点调整单 ====================

    @PostMapping("/inventory/adjustments")
    @OperLog(title = "库存调整", businessType = 2)
    @RequiresPermission("business:inventory:adjust")
    @Operation(summary = "提交盘点调整单", description = "delta 非 0、乐观锁版本 CAS、requestKey 幂等；"
            + "操作者取认证上下文（不信任 body），调整只动可用库存、预占只读快照")
    public ApiResponse<InventoryAdjustmentDTO> adjustInventory(
            @Valid @RequestBody InventoryAdjustRequest request,
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId) {
        return inventoryFeignClient.adjustStock(request, adminUserId);
    }

    @GetMapping("/inventory/adjustments")
    @RequiresPermission("business:inventory:adjust")
    @Operation(summary = "调整单分页", description = "时间倒序，skuId 可选过滤；审计含操作者/前后值/幂等键")
    public ApiResponse<List<InventoryAdjustmentDTO>> listInventoryAdjustments(
            @RequestParam(value = "skuId", required = false) Long skuId,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize) {
        return inventoryFeignClient.listAdjustments(skuId, page, pageSize);
    }

    // ==================== 私有辅助 ====================

    /** 批量补齐商品名/SKU 规格：一次 /skus/batch 调用；商品服务异常降级为空名（不阻断库存事实） */
    private List<InventoryRowDTO> enrichWithProductInfo(List<InventoryDTO> rows) {
        List<Long> skuIds = rows.stream().map(InventoryDTO::skuId).filter(java.util.Objects::nonNull).toList();
        Map<Long, SkuBatchDTO> skuInfo = new HashMap<>();
        if (!skuIds.isEmpty()) {
            try {
                ApiResponse<List<SkuBatchDTO>> batch = productFeignClient.getSkusBatch(skuIds);
                if (batch.success() && batch.data() != null) {
                    batch.data().forEach(sku -> skuInfo.put(sku.skuId(), sku));
                }
            } catch (Exception degrade) {
                log.warn("库存列表补名失败（降级返回无名称事实行）: {}", degrade.getMessage());
            }
        }
        return rows.stream()
                .map(row -> {
                    SkuBatchDTO info = skuInfo.get(row.skuId());
                    return new InventoryRowDTO(
                            row.id(), row.productId(), row.skuId(),
                            info == null ? null : info.productName(),
                            info == null ? null : info.attributes(),
                            row.availableStock(), row.lockedStock(), row.version(), row.updatedAt());
                })
                .toList();
    }

    /** 初始化前置校验：SKU 必须真实属于该商品（商品不存在/未含该 SKU 均拒绝） */
    private void validateSkuBelongsToProduct(Long productId, Long skuId) {
        ApiResponse<ProductDTO> response = productFeignClient.getProductById(productId);
        if (!response.success() || response.data() == null) {
            throw new BusinessException("PRODUCT_NOT_FOUND", "商品不存在或已删除，无法初始化库存");
        }
        boolean containsSku = response.data().skus() != null && response.data().skus().stream()
                .anyMatch(sku -> skuId.equals(sku.id()));
        if (!containsSku) {
            throw new BusinessException("INVENTORY_SKU_MISMATCH",
                    "SKU " + skuId + " 不属于商品 " + productId + "，请核对后重试");
        }
    }

    /** 上游失败透传：优先沿用上游错误码/文案，缺失时用兜底 */
    private <T> ApiResponse<T> failOf(ApiResponse<?> response, String fallbackCode, String fallbackMessage) {
        var error = response.error();
        if (error != null) {
            return ApiResponse.fail(error.code(), error.message());
        }
        return ApiResponse.fail(fallbackCode, fallbackMessage);
    }
}
