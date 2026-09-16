package com.cloudmart.community.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.community.service.GrowthService;
import com.cloudmart.community.vo.UserLevelVO;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 内部成长体系 Controller（mall-wish 每日签到发放经验专用）。
 *
 * <p>路由前缀 /internal/growth，仅内部服务调用
 * （mall-wish 经 Feign 转发，hasRole('INTERNAL') 由 X-Internal-Call 头授予）。
 * 签到经验由 mall-wish 每日签到入口统一发放，避免与 mall-community
 * 原生签到（/growth/check-in）重复计入。</p>
 */
@Slf4j
@RestController
@RequestMapping("/internal/growth")
@PreAuthorize("hasRole('INTERNAL')")
@RequiredArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(name = "内部-成长体系", description = "mall-wish 每日签到经验发放")
public class InternalGrowthController {

    private final GrowthService growthService;

    /**
     * 发放经验并返回最新等级信息。
     *
     * @param body {userId, exp, source?, description?}
     * @return {expReward, level, totalExp, levelTitle}
     */
    @PostMapping("/exp")
    @Operation(summary = "发放经验", description = "由 mall-wish 每日签到调用；返回发放后最新等级信息")
    public ApiResponse<Map<String, Object>> grantExp(@RequestBody Map<String, Object> body) {
        Long userId = ((Number) body.get("userId")).longValue();
        int exp = ((Number) body.get("exp")).intValue();
        String source = body.get("source") != null ? (String) body.get("source") : "CHECK_IN";
        String description = body.get("description") != null ? (String) body.get("description") : "每日签到";

        growthService.addExp(userId, exp, source, null, description);
        UserLevelVO level = growthService.getUserLevel(userId);

        log.info("内部经验发放成功, userId={}, exp={}, source={}, level={}", userId, exp, source, level.level());
        return ApiResponse.ok(Map.of(
                "expReward", exp,
                "level", level.level(),
                "totalExp", level.totalExp(),
                "levelTitle", level.levelTitle() != null ? level.levelTitle() : ""));
    }
}