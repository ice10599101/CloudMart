package com.cloudmart.pet.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.config.PetRequestContext;
import com.cloudmart.pet.dto.BuyItemRequest;
import com.cloudmart.pet.entity.PetPurchaseOrder;
import com.cloudmart.pet.repository.PetPurchaseOrderMapper;
import com.cloudmart.pet.wallet.impl.PetPurchaseApplicationService;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * R02 统一购买入口（§7.2）：所有商城/家具/食物购买经 {@link PetPurchaseApplicationService}
 * 编排——意图认领 → 订单 → 钱包扣款 → 资产交付 → 幂等终态同事务。
 * 必需 Idempotency-Key（缺键 400，禁止服务端随机键冒充客户端幂等）。
 * 旧入口 /shop/buy、/home/furniture/buy 为兼容适配，内部委托同一服务，不存在旁路扣款。
 */
@RestController
@RequestMapping
@Tag(name = "宠物统一购买", description = "统一购买、购买记录（R02/§7.2）")
@RequiredArgsConstructor
@Validated
public class PetPurchaseController {

    private static final int MAX_PAGE_SIZE = 50;

    private final PetPurchaseApplicationService purchaseApplicationService;
    private final PetPurchaseOrderMapper orderMapper;

    /** 统一购买请求（§7.2：客户端只提交商品身份和目标宠物，价格/门槛服务端快照） */
    public record PurchaseRequest(
            @NotNull(message = "请选择目标宠物")
            Long petId,

            @NotBlank(message = "请选择物品类型")
            @Pattern(regexp = "EQUIPMENT|SKIN|SKILL_BOOK|FURNITURE|FOOD", message = "物品类型非法")
            String itemType,

            @NotBlank(message = "请选择要购买的物品")
            String itemCode,

            @Size(max = 64, message = "期望配置版本过长")
            String expectedConfigVersion
    ) {
    }

    @PostMapping("/purchases")
    @Operation(summary = "统一购买（R02）", description = "必需幂等键；价格/门槛服务端权威；"
            + "同键重放返回原结果；同键异参 409；处理中 409 PET_REQUEST_IN_PROGRESS（按原键查询 /operations/{requestKey}）")
    @SentinelResource("PET_PURCHASE")
    public ApiResponse<PetPurchaseApplicationService.PurchaseResult> purchase(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @RequestBody PurchaseRequest request) {
        return ApiResponse.ok(purchaseApplicationService.purchase(userId, request.petId(),
                request.itemType(), request.itemCode(), PetRequestContext.idempotencyKey(),
                request.expectedConfigVersion()));
    }

    @GetMapping("/purchase-orders")
    @Operation(summary = "购买记录（R02）", description = "本人订单，按 id 倒序（雪花 ID 时间有序）；"
            + "keyset 翻页：cursor 为上一页最后一条 orderId；size 默认 20、最大 50")
    @SentinelResource("PET_QUERY")
    public ApiResponse<PurchaseOrderPageVO> purchaseOrders(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "翻页游标（上一页最后一条 orderId）") @RequestParam(required = false) String cursor,
            @Parameter(description = "页大小（1..50，默认 20）") @RequestParam(defaultValue = "20") int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new com.cloudmart.common.exception.BusinessException(PetErrorCodes.PET_VALIDATION_ERROR,
                    "size 需在 1..50");
        }
        long cursorId = 0;
        if (cursor != null && !cursor.isBlank()) {
            try {
                cursorId = Long.parseLong(cursor);
            } catch (NumberFormatException e) {
                throw new com.cloudmart.common.exception.BusinessException(PetErrorCodes.PET_VALIDATION_ERROR,
                        "cursor 非法");
            }
            if (cursorId < 0) {
                throw new com.cloudmart.common.exception.BusinessException(PetErrorCodes.PET_VALIDATION_ERROR,
                        "cursor 非法");
            }
        }
        LambdaQueryWrapper<PetPurchaseOrder> wrapper = new LambdaQueryWrapper<PetPurchaseOrder>()
                .eq(PetPurchaseOrder::getUserId, userId)
                .orderByDesc(PetPurchaseOrder::getId);
        if (cursorId > 0) {
            wrapper.lt(PetPurchaseOrder::getId, cursorId);
        }
        // 多取 1 条判断 hasMore，返回体最多 size 条（R09：内部 limit+1、公共 size 上限不变）
        List<PetPurchaseOrder> rows = orderMapper.selectList(wrapper.last("LIMIT " + (size + 1)));
        boolean hasMore = rows.size() > size;
        List<PetPurchaseOrder> page = hasMore ? rows.subList(0, size) : rows;
        String nextCursor = hasMore && !page.isEmpty() ? String.valueOf(page.get(page.size() - 1).getId()) : null;
        return ApiResponse.ok(new PurchaseOrderPageVO(
                page.stream().map(PetPurchaseController::toVo).toList(), nextCursor, hasMore));
    }

    private static PurchaseOrderVO toVo(PetPurchaseOrder order) {
        return new PurchaseOrderVO(String.valueOf(order.getId()),
                order.getPetId() == null ? null : String.valueOf(order.getPetId()),
                order.getItemType(), order.getItemCode(), order.getQuantity(),
                String.valueOf(order.getTotalAmount()), order.getCurrency(),
                order.getStatus(), order.getCompletedAt() == null ? null : order.getCompletedAt().toString());
    }

    /** 订单摘要（ID/金额为十进制字符串，R09 类型契约） */
    public record PurchaseOrderVO(String orderId, String petId, String itemType, String itemCode,
                                  Integer quantity, String totalAmount, String currency, String status,
                                  String completedAt) {
    }

    public record PurchaseOrderPageVO(List<PurchaseOrderVO> items, String nextCursor, boolean hasMore) {
    }
}
