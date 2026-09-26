package com.cloudmart.wish.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.wish.service.DriftBottleService;
import com.cloudmart.wish.service.PetSupportService;
import com.cloudmart.wish.vo.DriftBottleVO;
import com.cloudmart.wish.vo.PetWalletOperationVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    @Operation(summary = "宠物代主人捞瓶", description = "委托 DriftBottleService.fishBottleForPet：随机候选 + CAS 抢瓶，"
            + "计入用户每日 20 次打捞配额；海里无瓶返回 data=null（宠物空手而归）。"
            + "userId 显式传入（定时任务线程无登录头）；requestId 为稳定业务请求标识（B11 幂等重放）")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<DriftBottleVO> fishForPet(
            @Parameter(description = "主人用户 ID（显式传入，缺失时回退请求头）")
            @RequestParam(value = "userId", required = false) Long userIdParam,
            @Parameter(description = "主人用户 ID（网关注入）", required = true)
            @RequestHeader("X-User-Id") Long userIdHeader,
            @Parameter(description = "业务请求标识（同标识重入返回原结果，不二次抢瓶）")
            @RequestParam(value = "requestId", required = false) String requestId) {
        Long userId = userIdParam != null ? userIdParam : userIdHeader;
        return ApiResponse.ok(driftBottleService.fishBottleForPet(userId, requestId));
    }

    @PostMapping("/starlight/earn")
    @Operation(summary = "宠物奖励发放星光", description = "PET_REWARD 流水；返回实际入账量"
            + "（余额达上限时截断，0 表示未入账）。携带 operationId 时走幂等路径："
            + "重复相同请求返回原结果，同键不同内容返回 WISH_OPERATION_CONFLICT(409)")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<PetWalletOperationVO> earnStarlight(
            @Parameter(description = "用户 ID", required = true) @RequestParam("userId") Long userId,
            @Parameter(description = "星光数量（正整数）", required = true) @RequestParam("amount") Integer amount,
            @Parameter(description = "关联业务 ID（活动/对战记录 ID，审计用；购买场景可空）")
            @RequestParam(value = "refId", required = false) Long refId,
            @Parameter(description = "业务操作唯一键（B01 幂等；缺失时兼容旧非幂等路径）")
            @RequestParam(value = "operationId", required = false) String operationId) {
        if (operationId == null || operationId.isBlank()) {
            int credited = petSupportService.earnForPet(userId, amount, refId);
            return ApiResponse.ok(new PetWalletOperationVO(null, "EARN", amount, credited, null,
                    "PET_REWARD", refId, "COMPLETED", false));
        }
        return ApiResponse.ok(petSupportService.earnForPetIdempotent(userId, amount, refId, operationId));
    }

    @PostMapping("/starlight/spend")
    @Operation(summary = "宠物商城扣减星光", description = "PET_SHOP 流水；条件 UPDATE 原子扣减，"
            + "余额不足返回 WISH_STARLIGHT_INSUFFICIENT(402)；返回扣减后余额。"
            + "携带 operationId 时走幂等路径（重复请求返回原结果，失败可原单重试）")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<PetWalletOperationVO> spendStarlight(
            @Parameter(description = "用户 ID", required = true) @RequestParam("userId") Long userId,
            @Parameter(description = "星光数量（正整数）", required = true) @RequestParam("amount") Integer amount,
            @Parameter(description = "关联业务 ID（背包/进化记录 ID，审计用；购买场景可空）")
            @RequestParam(value = "refId", required = false) Long refId,
            @Parameter(description = "业务操作唯一键（B01 幂等；缺失时兼容旧非幂等路径）")
            @RequestParam(value = "operationId", required = false) String operationId) {
        if (operationId == null || operationId.isBlank()) {
            int balanceAfter = petSupportService.spendForPet(userId, amount, refId);
            return ApiResponse.ok(new PetWalletOperationVO(null, "SPEND", amount, amount, balanceAfter,
                    "PET_SHOP", refId, "COMPLETED", false));
        }
        return ApiResponse.ok(petSupportService.spendForPetIdempotent(userId, amount, refId, operationId));
    }

    @GetMapping("/starlight/operations/{operationId}")
    @Operation(summary = "交易结果查询", description = "按业务操作键查询已完成交易（B01 内部结果查询）；"
            + "data=null 表示结果未知（未执行或处理中），调用方可按原单安全重试")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<PetWalletOperationVO> findOperation(
            @Parameter(description = "业务操作唯一键", required = true)
            @PathVariable("operationId") String operationId) {
        return ApiResponse.ok(petSupportService.findPetOperation(operationId));
    }

    @GetMapping("/starlight/balance")
    @Operation(summary = "星光余额", description = "宠物商城展示余额（只读）")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<Integer> starlightBalance(
            @Parameter(description = "用户 ID", required = true) @RequestParam("userId") Long userId) {
        return ApiResponse.ok(petSupportService.petStarlightBalance(userId));
    }
}
