package com.cloudmart.admin.controller;

import com.cloudmart.admin.feign.PetFeignClient;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.annotation.OperLog;
import com.cloudmart.common.annotation.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端钱包代理（W04/§8.4）：账户/流水/冻结/调账/对账，转发 mall-pet /admin/pet/wallet/**。
 *
 * <p>权限（§4.1）：钱包查询 {@code business:pet:wallet:read}、调账申请
 * {@code business:pet:wallet:adjust:request}、审批 {@code business:pet:wallet:adjust:approve}
 * （菜单随 V11 迁移落库，超管默认持有）；调账申请人/审批人取认证上下文经 Feign 可信透传，
 * 禁止客户端任填（SEC-02 契约）。所有变更操作带 OperLog 审计。</p>
 */
@RestController
@RequestMapping("/pet/wallet")
@Tag(name = "宠物钱包管理", description = "账户/流水/冻结/调账/对账代理（§8.4）")
@RequiredArgsConstructor
public class AdminPetWalletController {

    private final PetFeignClient petFeignClient;

    @GetMapping("/accounts")
    @RequiresPermission("business:pet:wallet:read")
    @Operation(summary = "账户列表", description = "按用户/状态筛选 + 分页（脱敏）")
    public ApiResponse<Object> accounts(@RequestParam(value = "userId", required = false) Long userId,
                                        @RequestParam(value = "status", required = false) String status,
                                        @RequestParam(value = "page", defaultValue = "1") int page,
                                        @RequestParam(value = "size", defaultValue = "20") int size) {
        return petFeignClient.listWalletAccounts(userId, status, page, size);
    }

    @GetMapping("/accounts/{userId}")
    @RequiresPermission("business:pet:wallet:read")
    @Operation(summary = "用户账户")
    public ApiResponse<Object> accountOf(@PathVariable("userId") Long userId) {
        return petFeignClient.walletAccountOf(userId);
    }

    @GetMapping("/transactions")
    @RequiresPermission("business:pet:wallet:read")
    @Operation(summary = "流水查询", description = "按用户/业务/方向过滤 + 分页")
    public ApiResponse<Object> transactions(@RequestParam(value = "userId", required = false) Long userId,
                                            @RequestParam(value = "bizType", required = false) String bizType,
                                            @RequestParam(value = "direction", required = false) String direction,
                                            @RequestParam(value = "page", defaultValue = "1") int page,
                                            @RequestParam(value = "size", defaultValue = "20") int size) {
        return petFeignClient.listWalletTransactions(userId, bizType, direction, page, size);
    }

    @PostMapping("/accounts/{userId}/freeze")
    @OperLog(title = "宠物钱包冻结", businessType = 2)
    @RequiresPermission("business:pet:wallet:adjust:approve")
    @Operation(summary = "冻结账户", description = "reason/expectedVersion 必带；强管理动作，与调账审批同级权限（§4.1）")
    public ApiResponse<Void> freeze(@PathVariable("userId") Long userId,
                                    @RequestBody Map<String, Object> body) {
        return petFeignClient.freezeWalletAccount(userId, body);
    }

    @PostMapping("/accounts/{userId}/unfreeze")
    @OperLog(title = "宠物钱包解冻", businessType = 2)
    @RequiresPermission("business:pet:wallet:adjust:approve")
    @Operation(summary = "解冻账户", description = "reason/expectedVersion 必带")
    public ApiResponse<Void> unfreeze(@PathVariable("userId") Long userId,
                                      @RequestBody Map<String, Object> body) {
        return petFeignClient.unfreezeWalletAccount(userId, body);
    }

    @PostMapping("/adjustments")
    @OperLog(title = "宠物币调账申请", businessType = 1)
    @RequiresPermission("business:pet:wallet:adjust:request")
    @Operation(summary = "创建调账申请", description = "delta 带符号字符串；申请人取认证上下文")
    public ApiResponse<Object> createAdjustment(@RequestBody Map<String, Object> body) {
        return petFeignClient.createWalletAdjustment(body);
    }

    @PostMapping("/adjustments/{id}/approve")
    @OperLog(title = "宠物币调账审批", businessType = 2)
    @RequiresPermission("business:pet:wallet:adjust:approve")
    @Operation(summary = "审批通过", description = "与申请人不同；原子入账；重复审批返回原结果")
    public ApiResponse<Object> approveAdjustment(@PathVariable("id") Long id,
                                                 @RequestBody(required = false) Map<String, Object> body) {
        return petFeignClient.approveWalletAdjustment(id, body);
    }

    @PostMapping("/adjustments/{id}/reject")
    @OperLog(title = "宠物币调账审批", businessType = 2)
    @RequiresPermission("business:pet:wallet:adjust:approve")
    @Operation(summary = "审批拒绝", description = "与申请人不同；重复审批返回原结果")
    public ApiResponse<Object> rejectAdjustment(@PathVariable("id") Long id,
                                                @RequestBody(required = false) Map<String, Object> body) {
        return petFeignClient.rejectWalletAdjustment(id, body);
    }

    @GetMapping("/adjustments")
    @RequiresPermission("business:pet:wallet:read")
    @Operation(summary = "调账列表")
    public ApiResponse<Object> adjustments(@RequestParam(value = "status", required = false) String status,
                                           @RequestParam(value = "userId", required = false) Long userId,
                                           @RequestParam(value = "page", defaultValue = "1") int page,
                                           @RequestParam(value = "size", defaultValue = "20") int size) {
        return petFeignClient.listWalletAdjustments(status, userId, page, size);
    }

    @GetMapping("/adjustments/{id}")
    @RequiresPermission("business:pet:wallet:read")
    @Operation(summary = "调账详情")
    public ApiResponse<Object> adjustmentOf(@PathVariable("id") Long id) {
        return petFeignClient.walletAdjustmentOf(id);
    }

    @GetMapping("/reconciliations")
    @RequiresPermission("business:pet:wallet:read")
    @Operation(summary = "对账批次列表", description = "历史批次与差异数（只读）")
    public ApiResponse<Object> reconciliations(@RequestParam(value = "page", defaultValue = "1") int page,
                                               @RequestParam(value = "size", defaultValue = "20") int size) {
        return petFeignClient.listWalletReconciliations(page, size);
    }

    @GetMapping("/reconciliations/{runId}")
    @RequiresPermission("business:pet:wallet:read")
    @Operation(summary = "对账批次详情", description = "差异明细（OPEN 人工处置）")
    public ApiResponse<Object> reconciliationOf(@PathVariable("runId") Long runId) {
        return petFeignClient.walletReconciliationOf(runId);
    }

    @PostMapping("/reconciliations/run")
    @OperLog(title = "宠物钱包对账触发", businessType = 2)
    @RequiresPermission("business:pet:wallet:read")
    @Operation(summary = "触发对账", description = "运维手动触发一轮对账（每日另有定时）")
    public ApiResponse<Void> triggerReconcile() {
        return petFeignClient.triggerWalletReconcile();
    }
}
