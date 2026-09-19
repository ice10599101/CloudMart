package com.cloudmart.wish.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.wish.service.DriftBottleService;
import com.cloudmart.wish.service.UserStatService;
import com.cloudmart.wish.enums.ResourceLogSource;
import com.cloudmart.wish.vo.DriftBottleVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * 奖励星光直接走 {@link UserStatService#earnStarlight}（wish_resource_log 流水来源 PET_REWARD）。</p>
 */
@RestController
@RequestMapping("/internal/pet-support")
@Tag(name = "心愿宇宙·宠物支持内部端点", description = "mall-pet 专用（外部不可达）：宠物捞瓶 + 宠物奖励星光")
@RequiredArgsConstructor
public class InternalPetSupportController {

    private final DriftBottleService driftBottleService;
    private final UserStatService userStatService;

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
    @Operation(summary = "宠物奖励发放星光", description = "委托 UserStatService.earnStarlight 写余额 + PET_REWARD 流水；"
            + "返回发放后的余额")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<Integer> earnStarlight(
            @Parameter(description = "用户 ID", required = true) @RequestParam("userId") Long userId,
            @Parameter(description = "星光数量（正整数）", required = true) @RequestParam("amount") Integer amount,
            @Parameter(description = "关联业务 ID（活动/对战记录 ID，审计用）", required = true)
            @RequestParam("refId") Long refId) {
        return ApiResponse.ok(userStatService.earnStarlight(userId, amount, ResourceLogSource.PET_REWARD, refId));
    }
}
