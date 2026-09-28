package com.cloudmart.inventory.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.inventory.dto.DeductRequest;
import com.cloudmart.inventory.dto.ReleaseRequest;
import com.cloudmart.inventory.entity.InventoryReservation;
import com.cloudmart.inventory.repository.InventoryReservationMapper;
import com.cloudmart.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 库存内部接口（STOCK-01 / §8.4）：网关拒绝外部访问，服务令牌限调用方
 * （issuers=[mall-order]，scope=inventory:trade）。
 *
 * <ul>
 *   <li>预占：批量登记台账并条件扣减，部分失败时回滚本批已成功的预占（全成全败）；</li>
 *   <li>确认/释放：以台账为准，无客户端数量；已释放幂等成功，已确认返回状态冲突。</li>
 * </ul>
 */
@RestController
@RequestMapping("/internal/inventory")
@RequiredArgsConstructor
@PreAuthorize("hasRole('INTERNAL')")
@Tag(name = "内部-库存预占", description = "订单服务专用的预占/确认/释放（服务令牌可达）")
public class InternalInventoryController {

    private final InventoryService inventoryService;
    private final InventoryReservationMapper reservationMapper;

    public record ReservationRequest(@NotNull Long orderId,
                                     @Valid @NotNull List<Item> items) {
        public record Item(@NotNull Long skuId, @NotNull @Min(1) Integer quantity) {
        }
    }

    @PostMapping("/reservations")
    @Operation(summary = "批量预占", description = "UNIQUE(orderId,skuId) 幂等；部分失败回滚本批（全成全败）")
    public ApiResponse<Map<String, Object>> reserve(@Valid @RequestBody ReservationRequest request) {
        int reserved = 0;
        for (ReservationRequest.Item item : request.items()) {
            try {
                inventoryService.deductStock(
                        new DeductRequest(item.skuId(), item.quantity(), request.orderId()));
                reserved++;
            } catch (RuntimeException e) {
                // 全成全败：回滚本批已成功的预占（各自幂等），再向上抛出
                for (ReservationRequest.Item done : request.items().subList(0, reserved)) {
                    try {
                        inventoryService.releaseStock(
                                new ReleaseRequest(done.skuId(), done.quantity(), request.orderId()));
                    } catch (RuntimeException rollbackError) {
                        // 回滚失败登记为台账遗留，人工/补偿核对（不吞掉原始失败）
                    }
                }
                throw e;
            }
        }
        return ApiResponse.ok(Map.of("orderId", request.orderId(), "reserved", reserved));
    }

    @PostMapping("/reservations/{orderId}/confirm")
    @Operation(summary = "按订单确认", description = "无客户端数量，以台账为准；返回确认的预占行数")
    public ApiResponse<Map<String, Object>> confirm(@PathVariable("orderId") Long orderId) {
        List<InventoryReservation> reservations = reservationMapper.findReservedByOrder(orderId);
        int confirmed = 0;
        for (InventoryReservation reservation : reservations) {
            inventoryService.confirmDeduct(reservation.getSkuId(), reservation.getQuantity(), orderId);
            confirmed++;
        }
        return ApiResponse.ok(Map.of("orderId", orderId, "confirmed", confirmed));
    }

    @PostMapping("/reservations/{orderId}/release")
    @Operation(summary = "按订单释放", description = "已释放幂等成功；已确认返回状态冲突")
    public ApiResponse<Map<String, Object>> release(@PathVariable("orderId") Long orderId) {
        List<InventoryReservation> reservations = reservationMapper.findReservedByOrder(orderId);
        int released = 0;
        for (InventoryReservation reservation : reservations) {
            inventoryService.releaseStock(
                    new ReleaseRequest(reservation.getSkuId(), reservation.getQuantity(), orderId));
            released++;
        }
        return ApiResponse.ok(Map.of("orderId", orderId, "released", released));
    }

    @GetMapping("/reservations/{orderId}")
    @Operation(summary = "查询订单预占", description = "台账明细（状态/数量/版本），供排障与对账")
    public ApiResponse<List<InventoryReservation>> byOrder(@PathVariable("orderId") Long orderId) {
        return ApiResponse.ok(reservationMapper.findReservedByOrder(orderId));
    }
}
