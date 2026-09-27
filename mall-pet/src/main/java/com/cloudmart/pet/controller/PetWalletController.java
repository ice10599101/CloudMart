package com.cloudmart.pet.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.pet.entity.PetWalletAccount;
import com.cloudmart.pet.entity.PetWalletTransaction;
import com.cloudmart.pet.wallet.PetWalletQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 宠物币钱包用户接口（W01/§8.2）。
 *
 * <p>只读无副作用：查看余额/流水不触发领奖；无宠物也可读本人账户。
 * 契约（§8.1）：accountId/version 为字符串、balance 为十进制字符串（由 Long→String
 * 序列化保证）、数量类为 number；越权不存在语义一致（仅本人数据）。</p>
 */
@RestController
@RequestMapping
@Tag(name = "宠物币钱包", description = "余额与收支明细（PET_COIN，只读）")
@RequiredArgsConstructor
public class PetWalletController {

    private final PetWalletQueryService walletQueryService;

    /** 钱包视图（§8.2 PetWallet） */
    public record PetWalletVO(
            Long accountId,
            String currency,
            Long balance,
            String status,
            Long version,
            LocalDateTime serverNow
    ) {
    }

    /** 流水项（§8.2：transactionId/operationId/petId 为 ID，amount/delta 数值契约由序列化层保证） */
    public record PetWalletTransactionVO(
            Long transactionId,
            String operationId,
            Long petId,
            String bizType,
            String direction,
            Long amount,
            String status,
            String currency,
            LocalDateTime occurredAt
    ) {
    }

    @GetMapping("/wallet")
    @Operation(summary = "宠物币余额", description = "本人账户（懒创建期初 0）；冻结时 status=FROZEN 仍可读")
    public ApiResponse<PetWalletVO> wallet(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        PetWalletAccount account = walletQueryService.getWallet(userId);
        return ApiResponse.ok(new PetWalletVO(account.getId(), account.getCurrency(),
                account.getBalance(), account.getStatus(), account.getVersion(),
                LocalDateTime.now(ZoneOffset.UTC)));
    }

    @GetMapping("/wallet/transactions")
    @Operation(summary = "收支明细", description = "游标分页（新→旧），可按 direction/bizType 过滤；"
            + "meta.nextCursor/hasMore 表达分页状态")
    public ApiResponse<List<PetWalletTransactionVO>> transactions(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "游标（上一页最后一条 id）") @RequestParam(value = "cursor", required = false) Long cursor,
            @RequestParam(value = "size", defaultValue = "20") Integer size,
            @Parameter(description = "方向过滤 EARN/SPEND/REFUND/ADJUSTMENT")
            @RequestParam(value = "direction", required = false) String direction,
            @Parameter(description = "业务类型过滤") @RequestParam(value = "bizType", required = false) String bizType) {
        int safeSize = size == null || size < 1 ? 20 : Math.min(size, 50);
        List<PetWalletTransaction> rows = walletQueryService.listTransactions(
                userId, cursor, safeSize + 1, direction, bizType);
        boolean hasMore = rows.size() > safeSize;
        List<PetWalletTransaction> page = hasMore ? rows.subList(0, safeSize) : rows;
        String nextCursor = hasMore && !page.isEmpty() ? String.valueOf(page.getLast().getId()) : null;
        List<PetWalletTransactionVO> items = page.stream()
                .map(t -> new PetWalletTransactionVO(t.getId(), t.getOperationId(), t.getPetId(),
                        t.getBizType(), t.getDirection(), t.getAmount(), t.getStatus(), t.getCurrency(),
                        t.getCreatedAt()))
                .toList();
        return ApiResponse.okWithCursor(items, safeSize, nextCursor, hasMore);
    }
}
