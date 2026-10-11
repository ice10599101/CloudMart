package com.cloudmart.pet.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.pet.service.PetLinkedProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 实物商品联动（§6/R01/R02）：兑换码权威核验 → 双倍喂食权益。
 *
 * <p>R01 契约：请求只收 redemptionCode 与 requestKey——码必须先在 mall-coupon
 * /user-coupons/exchange 由本人完成兑换；本端点经服务令牌向 coupon 核验权威事实
 * （存在/已兑换/归属本人）后才发放权益；itemCode 服务端推导，客户端不可指定。
 * coupon 不可达时操作留 PENDING，凭 operationId 重查。</p>
 */
@RestController
@RequestMapping("/linked-product")
@RequiredArgsConstructor
@Validated
@Tag(name = "实物商品联动", description = "§6/R01：兑换码换双倍喂食权益（权威核验+可恢复台账）")
public class PetLinkedProductController {

    private final PetLinkedProductService linkedProductService;

    public record RedeemRequest(
            @NotBlank(message = "兑换码不能为空") @Size(max = 64, message = "兑换码过长") String redemptionCode,
            @Size(max = 64, message = "请求键过长") String requestKey) {
    }

    @PostMapping("/redeem")
    @Operation(summary = "核销兑换码", description = "权威核验后发放'下一次喂食效果 x2'权益；"
            + "返回操作视图（VERIFYING/DELIVERED/REJECTED/PENDING），同 requestKey 重放返回原操作")
    public ApiResponse<Map<String, Object>> redeem(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @RequestBody RedeemRequest request) {
        PetLinkedProductService.OperationView view =
                linkedProductService.redeem(userId, request.redemptionCode(), request.requestKey());
        return ApiResponse.ok(operationBody(view));
    }

    @GetMapping("/operations/{operationId}")
    @Operation(summary = "核销操作结果查询", description = "仅本人；PENDING 操作顺带原单重试核验（幂等）")
    public ApiResponse<Map<String, Object>> operation(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "核销操作 ID", required = true) @PathVariable("operationId") String operationId) {
        return ApiResponse.ok(operationBody(linkedProductService.getOperation(userId, operationId)));
    }

    @GetMapping("/entitlement")
    @Operation(summary = "我的权益状态", description = "可用份数 + 明细（来源/失效时刻）；usable 为列表快捷判定")
    public ApiResponse<Map<String, Object>> entitlement(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        PetLinkedProductService.EntitlementSummary summary = linkedProductService.entitlementSummary(userId);
        return ApiResponse.ok(Map.of(
                "usable", summary.usableCount() > 0,
                "usableCount", summary.usableCount(),
                "items", summary.items()));
    }

    private Map<String, Object> operationBody(PetLinkedProductService.OperationView view) {
        return Map.of(
                "operationId", view.operationId(),
                "status", view.status(),
                "entitlementId", view.entitlementId() == null ? "" : view.entitlementId(),
                "rejectReasonCode", view.rejectReasonCode() == null ? "" : view.rejectReasonCode(),
                "usable", view.usable());
    }
}
