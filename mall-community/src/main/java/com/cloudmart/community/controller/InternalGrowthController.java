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

    /** T09：跨域发奖来源白名单——稳定业务 ID 必填，唯一键 (user,source,bizId) 保证同事实只发一次 */
    private static final Map<String, String> ALLOWED_SOURCES = Map.of(
            "WISH_SIGNIN", "心愿每日签到",
            "WISH_MILESTONE", "心愿签到里程碑");
    private static final int MIN_GRANT_EXP = 1;
    private static final int MAX_GRANT_EXP = 500;

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
        String sourceBizId = body.get("sourceBizId") == null ? null
                : String.valueOf(body.get("sourceBizId")).trim();
        if (sourceBizId == null || sourceBizId.isBlank()) {
            throw new com.cloudmart.common.exception.BusinessException(
                    "EXP_GRANT_INVALID", "缺少稳定业务 ID sourceBizId（重试幂等依据）");
        }
        // sourceBizId 形如 {SOURCE}:{numericId}:EXP——source 服务端从其派生，不信任调用方自报
        String[] parts = sourceBizId.split(":");
        if (parts.length != 3 || !parts[2].equals("EXP")
                || !ALLOWED_SOURCES.containsKey(parts[0]) || !parts[1].matches("\\d{1,19}")) {
            throw new com.cloudmart.common.exception.BusinessException(
                    "EXP_GRANT_INVALID", "sourceBizId 非法或来源不在白名单");
        }
        String source = parts[0];
        Long bizId = Long.valueOf(parts[1]);
        if (exp < MIN_GRANT_EXP || exp > MAX_GRANT_EXP) {
            throw new com.cloudmart.common.exception.BusinessException(
                    "EXP_GRANT_INVALID", "经验数额越界（" + MIN_GRANT_EXP + "-" + MAX_GRANT_EXP + "）");
        }
        String description = body.get("description") != null
                ? String.valueOf(body.get("description")) : ALLOWED_SOURCES.get(source);

        growthService.addExp(userId, exp, source, bizId, description);
        UserLevelVO level = growthService.getUserLevel(userId);

        log.info("内部经验发放成功, userId={}, exp={}, source={}, level={}", userId, exp, source, level.level());
        return ApiResponse.ok(Map.of(
                "expReward", exp,
                "level", level.level(),
                "totalExp", level.totalExp(),
                "levelTitle", level.levelTitle() != null ? level.levelTitle() : ""));
    }

    /**
     * 等级星光联动（§6）：查询用户社区等级（growth 体系），
     * mall-wish 每日签到按等级计算星光加成。
     */
    @org.springframework.web.bind.annotation.GetMapping("/level")
    @io.swagger.v3.oas.annotations.Operation(summary = "查询用户等级",
            description = "mall-wish 每日签到等级加成用；返回 {level, levelTitle}")
    public ApiResponse<java.util.Map<String, Object>> getLevel(
            @org.springframework.web.bind.annotation.RequestParam("userId") Long userId) {
        UserLevelVO level = growthService.getUserLevel(userId);
        return ApiResponse.ok(java.util.Map.of(
                "level", level.level(),
                "levelTitle", level.levelTitle() == null ? "" : level.levelTitle()));
    }
}