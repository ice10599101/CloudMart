package com.cloudmart.pet.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.pet.service.PetLinkedProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 实物商品联动（§6）：兑换码核销 → 双倍喂食权益。
 *
 * <p>用户在商城购买实物零食（收货后获得兑换码）→ 在宠物侧核销换取
 * "下一次喂食效果 x2" 权益。兑换码所有权（是否归属本人/是否有效）由
 * mall-coupon /user-coupons/exchange 在发券环节校验；本端点信任
 * 发券完成后的 code（幂等 uk 兜底重复核销）。</p>
 */
@RestController
@RequestMapping("/linked-product")
@RequiredArgsConstructor
@Validated
@Tag(name = "实物商品联动", description = "§6：兑换码换双倍喂食权益")
public class PetLinkedProductController {

    private final PetLinkedProductService linkedProductService;

    public record RedeemRequest(
            @NotBlank(message = "兑换码不能为空") String redemptionCode,
            String itemCode) {}

    @PostMapping("/redeem")
    @Operation(summary = "核销兑换码", description = "兑换码换'下一次喂食效果 x2'权益（一码一次，幂等 uk）")
    public ApiResponse<Map<String, Object>> redeem(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @RequestBody RedeemRequest request) {
        Long entitlementId = linkedProductService.redeem(userId,
                request.redemptionCode(), null, request.itemCode());
        return ApiResponse.ok(Map.of("entitlementId", entitlementId,
                "usable", linkedProductService.hasUsableEntitlement(userId)));
    }

    @GetMapping("/entitlement")
    @Operation(summary = "我的权益状态", description = "是否持有未使用的双倍喂食权益")
    public ApiResponse<Map<String, Object>> entitlement(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(Map.of("usable", linkedProductService.hasUsableEntitlement(userId)));
    }
}
