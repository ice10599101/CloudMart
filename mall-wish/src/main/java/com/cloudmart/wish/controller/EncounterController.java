package com.cloudmart.wish.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.wish.dto.LetterInteractRequest;
import com.cloudmart.wish.dto.NearbyModeRequest;
import com.cloudmart.wish.dto.ReportTraceRequest;
import com.cloudmart.wish.service.EncounterService;
import com.cloudmart.wish.vo.EncounterLetterVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 擦肩而过 Controller（Sprint 3.3，文档 2.10/3.3）。
 *
 * <p>路由：/map/nearby-mode（附近模式开关）、/map/trace（轨迹上报）、
 * /map/encounter-letters（信笺列表）、/encounter-letters/{id}/read（拆信）、
 * /encounter-letters/{id}/interactions（匿名互动）。全部需登录
 * （附近模式与轨迹为个人隐私功能，匿名无意义）。</p>
 */
@RestController
@Tag(name = "擦肩而过", description = "附近模式 + 轨迹上报 + 相遇信笺（Sprint 3.3）")
@RequiredArgsConstructor
public class EncounterController {

    private final EncounterService encounterService;

    @PostMapping("/map/nearby-mode")
    @Operation(summary = "附近模式开关", description = "开启后允许轨迹上报（Redis 开关 24h 有效，"
            + "上报自动续期）；关闭立即生效（开关键立即删除，客户端同时停止上报）")
    @SentinelResource("WISH_NEARBY_MODE")
    public ApiResponse<Void> setNearbyMode(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @RequestBody NearbyModeRequest request) {
        encounterService.setNearbyMode(userId, request.enabled());
        return ApiResponse.ok(null);
    }

    @PostMapping("/map/trace")
    @Operation(summary = "轨迹上报", description = "附近模式开启后每 5 分钟上报一次；坐标转 geohash6 "
            + "入 Redis（TTL 25h，无原始坐标）；频率限制 5 分钟 >10 次 → 429；"
            + "位置伪造检测（>15km/h 可疑，枢纽放宽，连续 3 次 → 冻结 24h）")
    @SentinelResource("WISH_TRACE_REPORT")
    public ApiResponse<Void> reportTrace(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @RequestBody ReportTraceRequest request) {
        encounterService.reportTrace(userId, request.lat(), request.lng());
        return ApiResponse.ok(null);
    }

    @GetMapping("/map/encounter-letters")
    @Operation(summary = "信笺列表", description = "历史信笺（匿名化，无对方身份信息）；"
            + "status=PENDING 时 content 返回 null（契约）；DELIVERED 可拆信/互动")
    @SentinelResource("WISH_ENCOUNTER_LIST")
    public ApiResponse<List<EncounterLetterVO>> listLetters(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(encounterService.listLetters(userId));
    }

    @PutMapping("/encounter-letters/{id}/read")
    @Operation(summary = "拆信", description = "DELIVERED → READ；PENDING 信笺不可拆")
    @SentinelResource("WISH_ENCOUNTER_READ")
    public ApiResponse<EncounterLetterVO> markRead(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "信笺 ID", required = true) @PathVariable Long id) {
        return ApiResponse.ok(encounterService.markRead(userId, id));
    }

    @PostMapping("/encounter-letters/{id}/interactions")
    @Operation(summary = "信笺匿名互动", description = "type=BLESS 匿名祝福（免费）/ LIGHT 点亮对方心愿"
            + "（扣星光 2，对方心愿 support_count+1）；单信笺每日 1 次（429）；"
            + "对方收到匿名通知（不含 letterId/userId，无法反查）")
    @SentinelResource("WISH_ENCOUNTER_INTERACT")
    public ApiResponse<EncounterLetterVO> interact(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "信笺 ID", required = true) @PathVariable Long id,
            @Valid @RequestBody LetterInteractRequest request) {
        return ApiResponse.ok(encounterService.interact(userId, id, request.type(), request.content()));
    }
}
