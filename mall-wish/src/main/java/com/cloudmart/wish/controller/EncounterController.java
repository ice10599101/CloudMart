package com.cloudmart.wish.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 擦肩而过 Controller（Sprint 3.3）—— **产品已下线**（2026-10 产品决策）。
 *
 * <p>下线原因：信笺玩法已被漂流瓶（DriftBottleController，三端已实装）替代，
 * 附近模式/轨迹上报唯一产出即为信笺，保留入口会形成"上报轨迹却永远等不到信"
 * 的伪闭环（方案 T22：未做的行必须关闭入口）。</p>
 *
 * <p>下线方式：端点显式 410（{@code WISH_FEATURE_OFFLINE}），而非静默 404——
 * 客户端与运营可明确区分"功能下线"与"资源不存在"。</p>
 *
 * <p>数据与恢复：{@code encounter_letter} 表、轨迹 Redis 键（24h TTL 自然过期）
 * 与 {@code EncounterService/EncounterMatcher} 实现均保留未删（信笺历史数据可查），
 * 产品恢复时回退本提交即可重新暴露端点。</p>
 */
@RestController
@Tag(name = "擦肩而过", description = "产品已下线（信笺被漂流瓶替代）；端点保留路由语义，统一 410")
public class EncounterController {

    private static final BusinessException OFFLINE =
            new BusinessException("WISH_FEATURE_OFFLINE", "擦肩而过功能已下线（信笺玩法已由漂流瓶替代）");

    @PostMapping("/map/nearby-mode")
    @Operation(summary = "附近模式开关（已下线）", description = "功能已下线，统一返回 WISH_FEATURE_OFFLINE")
    public ApiResponse<Void> setNearbyMode() {
        throw OFFLINE;
    }

    @GetMapping("/map/nearby-mode")
    @Operation(summary = "附近模式状态查询（已下线）")
    public ApiResponse<Boolean> getNearbyMode() {
        throw OFFLINE;
    }

    @PostMapping("/map/trace")
    @Operation(summary = "轨迹上报（已下线）")
    public ApiResponse<Void> reportTrace() {
        throw OFFLINE;
    }

    @GetMapping("/map/encounter-letters")
    @Operation(summary = "信笺列表（已下线）")
    public ApiResponse<Void> listLetters() {
        throw OFFLINE;
    }

    @PutMapping("/encounter-letters/{id}/read")
    @Operation(summary = "拆信（已下线）")
    public ApiResponse<Void> markRead(@PathVariable Long id) {
        throw OFFLINE;
    }

    @PostMapping("/encounter-letters/{id}/interactions")
    @Operation(summary = "信笺匿名互动（已下线）")
    public ApiResponse<Void> interact(@PathVariable Long id) {
        throw OFFLINE;
    }
}
