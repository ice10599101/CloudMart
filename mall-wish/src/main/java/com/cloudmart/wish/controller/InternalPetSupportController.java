package com.cloudmart.wish.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.wish.service.DriftBottleService;
import com.cloudmart.wish.service.PetSupportService;
import com.cloudmart.wish.vo.DriftBottleVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 面向内部微服务的宠物支持端点（mall-pet Feign 调用）。
 *
 * <p>安全：{@code hasRole('INTERNAL')}——仅携带 {@code X-Internal-Call: true} 的
 * 内部请求可达（见 InternalCallAuthenticationFilter），外部请求 403。</p>
 *
 * <p>复用边界（社区宠物实施文档 §0.1 原则 2/3）：宠物不建第二套漂流瓶/钱包——
 * 捞瓶直接委托 {@link DriftBottleService#fishBottle}（计入用户每日打捞配额，防刷），
 * 星光发放/扣减委托 {@link PetSupportService}（流水来源 PET_REWARD / PET_SHOP）。
 * 星光方法以 {@code Propagation.MANDATORY} 声明，事务边界由 PetSupportService 提供。</p>
 */
@RestController
@RequestMapping("/internal/pet-support")
@Tag(name = "心愿宇宙·宠物支持内部端点", description = "mall-pet 专用（外部不可达）：宠物捞瓶 + 星光发放/扣减/余额")
@RequiredArgsConstructor
public class InternalPetSupportController {

    private final DriftBottleService driftBottleService;
    private final PetSupportService petSupportService;

    @PostMapping("/drift-bottles/fish")
    @Operation(summary = "宠物代主人捞瓶", description = "委托 DriftBottleService.fishBottle：随机候选 + CAS 抢瓶，"
            + "计入用户每日 20 次打捞配额；海里无瓶返回 data=null（宠物空手而归）")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<DriftBottleVO> fishForPet(
            @Parameter(description = "主人用户 ID（网关注入）", required = true)
            @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.ok(driftBottleService.fishBottle(userId));
    }

    @PostMapping("/starlight/earn")
    @Operation(summary = "宠物奖励发放星光", description = "PET_REWARD 流水；返回实际入账量"
            + "（余额达上限时截断，0 表示未入账）")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<Integer> earnStarlight(
            @Parameter(description = "用户 ID", required = true) @RequestParam("userId") Long userId,
            @Parameter(description = "星光数量（正整数）", required = true) @RequestParam("amount") Integer amount,
            @Parameter(description = "关联业务 ID（活动/对战记录 ID，审计用）", required = true)
            @RequestParam("refId") Long refId) {
        return ApiResponse.ok(petSupportService.earnForPet(userId, amount, refId));
    }

    @PostMapping("/starlight/spend")
    @Operation(summary = "宠物商城扣减星光", description = "PET_SHOP 流水；条件 UPDATE 原子扣减，"
            + "余额不足返回 WISH_STARLIGHT_INSUFFICIENT(402)；返回扣减后余额")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<Integer> spendStarlight(
            @Parameter(description = "用户 ID", required = true) @RequestParam("userId") Long userId,
            @Parameter(description = "星光数量（正整数）", required = true) @RequestParam("amount") Integer amount,
            @Parameter(description = "关联业务 ID（背包/进化记录 ID，审计用）", required = true)
            @RequestParam("refId") Long refId) {
        return ApiResponse.ok(petSupportService.spendForPet(userId, amount, refId));
    }

    @GetMapping("/starlight/balance")
    @Operation(summary = "星光余额", description = "宠物商城展示余额（只读）")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<Integer> starlightBalance(
            @Parameter(description = "用户 ID", required = true) @RequestParam("userId") Long userId) {
        return ApiResponse.ok(petSupportService.petStarlightBalance(userId));
    }
}
