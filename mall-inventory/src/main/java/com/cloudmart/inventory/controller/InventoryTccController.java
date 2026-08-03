package com.cloudmart.inventory.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.inventory.dto.TccDeductRequest;
import com.cloudmart.inventory.service.InventoryTccService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 库存 TCC 模式接口（Seata TCC 框架集成版）。
 * <p>
 * 用于高并发秒杀场景。通过 Seata TC 自动管理事务分支，
 * 也支持手动调用 Try/Confirm/Cancel 进行编排。
 */
@Tag(name = "库存TCC", description = "库存 TCC 模式接口（Seata 集成，用于秒杀等高并发场景）")
@RestController
@RequestMapping("/tcc")
public class InventoryTccController {

    private final InventoryTccService tccService;

    public InventoryTccController(InventoryTccService tccService) {
        this.tccService = tccService;
    }

    @Operation(summary = "Try: 冻结库存", description = "TCC 第一阶段，冻结指定数量的库存，返回事务XID。支持 Seata 全局事务自动编排。")
    @PostMapping("/try")
    public ApiResponse<String> tryDeduct(@Valid @RequestBody TccDeductRequest request) {
        String xid = tccService.tryDeduct(request);
        return ApiResponse.ok(xid);
    }

    @Operation(summary = "Confirm: 确认扣减", description = "TCC 第二阶段，将冻结的库存转为实际扣减。Seata 全局事务提交时自动调用，也可手动触发。")
    @PostMapping("/confirm/{xid}")
    public ApiResponse<Void> confirmDeduct(
            @Parameter(description = "事务XID") @PathVariable String xid) {
        tccService.confirmDeduct(xid);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "Cancel: 取消冻结", description = "TCC 回滚阶段，释放冻结的库存。Seata 全局事务回滚时自动调用，也可手动触发。")
    @PostMapping("/cancel/{xid}")
    public ApiResponse<Void> cancelDeduct(
            @Parameter(description = "事务XID") @PathVariable String xid) {
        tccService.cancelDeduct(xid);
        return ApiResponse.ok(null);
    }
}
