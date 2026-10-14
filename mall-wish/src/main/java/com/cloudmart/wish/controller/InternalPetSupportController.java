package com.cloudmart.wish.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.wish.service.DriftBottleService;
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
 * <p>安全：{@code hasRole('INTERNAL')}——仅携带合法 {@code X-Service-Token} 服务令牌的
 * 内部请求可达（见 ServiceTokenAuthenticationFilter），外部请求 403。</p>
 *
 * <p>C11：宠物收支已改为 mall-pet 本地宠物币账本，原五个 /starlight/* 星光桥接端点
 * （earn/spend/refund/operations/balance）与 PetSupportService 全部删除；本控制器
 * 仅保留捞瓶代理——宠物不建第二套漂流瓶，捞瓶委托 {@link DriftBottleService}。</p>
 */
@RestController
@RequestMapping("/internal/pet-support")
@Tag(name = "心愿宇宙·宠物支持内部端点", description = "mall-pet 专用（外部不可达）：宠物捞瓶")
@RequiredArgsConstructor
public class InternalPetSupportController {

    private final DriftBottleService driftBottleService;

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
}
