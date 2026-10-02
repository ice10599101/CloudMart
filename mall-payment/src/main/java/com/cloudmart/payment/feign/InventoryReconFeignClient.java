package com.cloudmart.payment.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.payment.dto.ReservationScanDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.List;

/**
 * mall-inventory 内部预占台账客户端（T11 对账 INVENTORY 层）：预占台账与
 * 订单状态跨服务核对；服务令牌自动签名（inventory:trade）。
 */
@FeignClient(contextId = "inventoryReconFeignClient", name = "mall-inventory",
        fallbackFactory = InventoryReconFeignClientFallbackFactory.class)
public interface InventoryReconFeignClient {

    @GetMapping("/internal/inventory/reservations/reconcile-scan")
    ApiResponse<List<ReservationScanDTO>> scanReservations(
            @RequestParam("since") LocalDateTime since,
            @RequestParam("lastId") long lastId,
            @RequestParam("limit") int limit);
}
