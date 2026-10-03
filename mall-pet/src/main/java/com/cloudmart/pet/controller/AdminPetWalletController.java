package com.cloudmart.pet.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.PetWalletAccount;
import com.cloudmart.pet.entity.PetWalletAdjustment;
import com.cloudmart.pet.entity.PetWalletTransaction;
import com.cloudmart.pet.repository.PetWalletAccountMapper;
import com.cloudmart.pet.repository.PetWalletTransactionMapper;
import com.cloudmart.pet.wallet.PetWalletAdjustmentService;
import com.cloudmart.pet.wallet.PetWalletQueryService;
import com.cloudmart.pet.wallet.impl.PetWalletReconcileJob;
import com.cloudmart.pet.entity.PetWalletReconcileItem;
import com.cloudmart.pet.entity.PetWalletReconcileRun;
import com.cloudmart.pet.repository.PetWalletReconcileItemMapper;
import com.cloudmart.pet.repository.PetWalletReconcileRunMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 管理端钱包能力（W04/§8.4）：账户/流水查询、冻结/解冻、调账审批、对账批次。
 *
 * <p>安全：hasRole('INTERNAL')——仅 mall-admin 服务令牌（pet:admin）可达；
 * 操作人取 mall-admin 代理透传的可信操作者头（X-User-Id=管理员 ID，SEC-02 契约），
 * 禁止客户端任填。调账不提供直接改余额接口；对账差异人工处置。</p>
 */
@RestController
@RequestMapping("/admin/pet/wallet")
@Tag(name = "宠物管理·钱包", description = "账户/流水/冻结/调账/对账（管理员）")
@RequiredArgsConstructor
@PreAuthorize("hasRole('INTERNAL')")
public class AdminPetWalletController {

    private final PetWalletAccountMapper accountMapper;
    private final PetWalletTransactionMapper transactionMapper;
    private final PetWalletQueryService walletQueryService;
    private final PetWalletAdjustmentService adjustmentService;
    private final PetWalletReconcileRunMapper reconcileRunMapper;
    private final PetWalletReconcileItemMapper reconcileItemMapper;
    private final PetWalletReconcileJob reconcileJob;

    public record AccountView(
            Long accountId,
            Long userId,
            String currency,
            Long balance,
            String status,
            Long version,
            java.time.LocalDateTime createdAt
    ) {
    }

    private AccountView toView(PetWalletAccount account) {
        return new AccountView(account.getId(), account.getUserId(), account.getCurrency(),
                account.getBalance(), account.getStatus(), account.getVersion(), account.getCreatedAt());
    }

    @GetMapping("/accounts")
    @Operation(summary = "账户列表", description = "按用户/状态筛选 + 分页（脱敏：不含敏感字段）")
    public ApiResponse<List<AccountView>> accounts(
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        Page<PetWalletAccount> result = accountMapper.selectPage(new Page<>(Math.max(page, 1), Math.min(size, 50)),
                new LambdaQueryWrapper<PetWalletAccount>()
                        .eq(userId != null, PetWalletAccount::getUserId, userId)
                        .eq(status != null && !status.isBlank(), PetWalletAccount::getStatus, status)
                        .orderByDesc(PetWalletAccount::getId));
        return ApiResponse.ok(result.getRecords().stream().map(this::toView).toList(),
                result.getCurrent(), result.getSize(), result.getTotal());
    }

    @GetMapping("/accounts/{userId}")
    @Operation(summary = "用户账户", description = "单人视图（懒创建期初 0）")
    public ApiResponse<AccountView> accountOf(@PathVariable("userId") Long userId) {
        return ApiResponse.ok(toView(walletQueryService.getWallet(userId)));
    }

    @GetMapping("/transactions")
    @Operation(summary = "流水查询", description = "按用户/业务/方向/时间过滤 + 分页")
    public ApiResponse<List<PetWalletTransaction>> transactions(
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "bizType", required = false) String bizType,
            @RequestParam(value = "direction", required = false) String direction,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        Page<PetWalletTransaction> result = transactionMapper.selectPage(
                new Page<>(Math.max(page, 1), Math.min(size, 50)),
                new LambdaQueryWrapper<PetWalletTransaction>()
                        .eq(userId != null, PetWalletTransaction::getUserId, userId)
                        .eq(bizType != null && !bizType.isBlank(), PetWalletTransaction::getBizType, bizType)
                        .eq(direction != null && !direction.isBlank(), PetWalletTransaction::getDirection, direction)
                        .orderByDesc(PetWalletTransaction::getId));
        return ApiResponse.ok(result.getRecords(), result.getCurrent(), result.getSize(), result.getTotal());
    }

    @PostMapping("/accounts/{userId}/freeze")
    @Operation(summary = "冻结账户", description = "reason/expectedVersion 必带；冻结禁止消费，仍可退款与调账入账")
    public ApiResponse<Void> freeze(
            @PathVariable("userId") Long userId,
            @RequestBody Map<String, Object> body,
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long operatorAdminId) {
        applyStatus(userId, body, true, operatorAdminId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/accounts/{userId}/unfreeze")
    @Operation(summary = "解冻账户", description = "reason/expectedVersion 必带")
    public ApiResponse<Void> unfreeze(
            @PathVariable("userId") Long userId,
            @RequestBody Map<String, Object> body,
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long operatorAdminId) {
        applyStatus(userId, body, false, operatorAdminId);
        return ApiResponse.ok(null);
    }

    private void applyStatus(Long userId, Map<String, Object> body, boolean frozen, Long operatorAdminId) {
        String reason = String.valueOf(body.getOrDefault("reason", ""));
        Long expectedVersion = body.get("expectedVersion") == null
                ? null : Long.valueOf(String.valueOf(body.get("expectedVersion")));
        adjustmentService.setAccountStatus(userId, frozen, reason, expectedVersion, operatorAdminId);
    }

    // ---------------- 调账 ----------------

    public record AdjustmentRequest(
            Long userId,
            String delta,
            String reason,
            String ticketNo
    ) {
    }

    public record ApprovalRequest(
            String reason,
            Long expectedVersion
    ) {
    }

    @PostMapping("/adjustments")
    @Operation(summary = "创建调账申请", description = "delta 为带符号十进制字符串（§8.1）；申请人取认证上下文")
    public ApiResponse<PetWalletAdjustment> createAdjustment(
            @RequestBody AdjustmentRequest request,
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long operatorAdminId) {
        long delta;
        try {
            delta = Long.parseLong(request.delta());
        } catch (NumberFormatException e) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR,
                    "delta 必须是十进制整数字符串");
        }
        return ApiResponse.ok(adjustmentService.apply(request.userId(), delta, request.reason(),
                request.ticketNo(), operatorAdminId));
    }

    @PostMapping("/adjustments/{id}/approve")
    @Operation(summary = "审批通过", description = "必须与申请人不同；原子入账；重复审批返回原结果")
    public ApiResponse<PetWalletAdjustment> approveAdjustment(
            @PathVariable("id") Long id,
            @RequestBody(required = false) ApprovalRequest body,
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long operatorAdminId) {
        String reason = body == null ? null : body.reason();
        return ApiResponse.ok(adjustmentService.approve(id, operatorAdminId, reason));
    }

    @PostMapping("/adjustments/{id}/reject")
    @Operation(summary = "审批拒绝", description = "必须与申请人不同；重复审批返回原结果")
    public ApiResponse<PetWalletAdjustment> rejectAdjustment(
            @PathVariable("id") Long id,
            @RequestBody(required = false) ApprovalRequest body,
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long operatorAdminId) {
        String reason = body == null ? null : body.reason();
        return ApiResponse.ok(adjustmentService.reject(id, operatorAdminId, reason));
    }

    @GetMapping("/adjustments")
    @Operation(summary = "调账列表", description = "状态/用户筛选 + 分页")
    public ApiResponse<List<PetWalletAdjustment>> adjustments(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(adjustmentService.list(status, userId, page, size));
    }

    @GetMapping("/adjustments/{id}")
    @Operation(summary = "调账详情", description = "含审批审计与入账流水 ID")
    public ApiResponse<PetWalletAdjustment> adjustmentOf(@PathVariable("id") Long id) {
        return ApiResponse.ok(adjustmentService.findById(id));
    }

    // ---------------- 对账 ----------------

    @GetMapping("/reconciliations")
    @Operation(summary = "对账批次列表", description = "历史批次与差异数（只读，禁止前端改余额）")
    public ApiResponse<List<PetWalletReconcileRun>> reconciliations(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        Page<PetWalletReconcileRun> result = reconcileRunMapper.selectPage(
                new Page<>(Math.max(page, 1), Math.min(size, 50)),
                new LambdaQueryWrapper<PetWalletReconcileRun>().orderByDesc(PetWalletReconcileRun::getId));
        return ApiResponse.ok(result.getRecords(), result.getCurrent(), result.getSize(), result.getTotal());
    }

    @GetMapping("/reconciliations/{runId}")
    @Operation(summary = "对账批次详情", description = "差异明细（OPEN 人工处置）")
    public ApiResponse<List<PetWalletReconcileItem>> reconciliationOf(@PathVariable("runId") Long runId) {
        return ApiResponse.ok(reconcileItemMapper.selectList(
                new LambdaQueryWrapper<PetWalletReconcileItem>()
                        .eq(PetWalletReconcileItem::getRunId, runId)
                        .orderByDesc(PetWalletReconcileItem::getDiff)));
    }

    @PostMapping("/reconciliations/run")
    @Operation(summary = "触发对账", description = "运维触发一轮对账（每日定时另有调度）")
    public ApiResponse<Void> triggerReconcile() {
        reconcileJob.reconcile("MANUAL");
        return ApiResponse.ok(null);
    }
}
