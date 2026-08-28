package com.cloudmart.wish.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.dto.CreateWarmEventRequest;
import com.cloudmart.wish.dto.FenceCheckRequest;
import com.cloudmart.wish.service.WarmMapService;
import com.cloudmart.wish.vo.FenceCheckVO;
import com.cloudmart.wish.vo.MapClusterVO;
import com.cloudmart.wish.vo.WarmEventVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 城市幸福地图 + 地理围栏 Controller（Sprint 3.2，文档 2.10：/wish/map/**）。
 *
 * <p>隐私边界：围栏列表永不向用户端暴露——GET /map/fence 显式 403
 * （验收：非管理员 GET /wish/fence → 403）；围栏打卡响应无坐标字段。</p>
 */
@RestController
@RequestMapping("/map")
@Tag(name = "城市幸福地图 + 围栏", description = "温暖事件 UGC + 围栏打卡（Sprint 3.2）")
@RequiredArgsConstructor
public class WarmMapController {

    private final WarmMapService warmMapService;

    @PostMapping("/fence/check")
    @Operation(summary = "围栏打卡判定", description = "提交 (wishId, lat, lng)，服务端判定是否命中"
            + "该心愿的活跃围栏（Haversine ≤ radius 含等号 + 有效期/状态过滤），命中触发心愿绽放"
            + "（每围栏每用户每日幂等）；响应仅含结果与心愿信息，无围栏坐标。打卡坐标不存储")
    @SentinelResource("WISH_FENCE_CHECK")
    public ApiResponse<FenceCheckVO> checkFence(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @RequestBody FenceCheckRequest request) {
        return ApiResponse.ok(warmMapService.checkFence(userId, request.wishId(), request.lat(), request.lng()));
    }

    @GetMapping("/fence")
    @Operation(summary = "围栏查询（拒绝）", description = "隐私边界显式声明：围栏列表仅管理端可见，"
            + "用户端访问一律 403（文档 3.2 安全验收）")
    public ApiResponse<Void> fenceForbidden() {
        throw new BusinessException(WishErrorCodes.WISH_FORBIDDEN, "围栏信息仅管理端可见");
    }

    @PostMapping("/warm-events")
    @Operation(summary = "发布温暖事件", description = "UGC（标题≤60/内容≤500/坐标必填）；坐标服务端"
            + "geohash7 模糊化，原始坐标不留存；DFA 命中敏感词 → AUTO_HIDDEN，未命中 → PENDING 先发后审")
    @SentinelResource("WISH_WARM_EVENT_CREATE")
    public ApiResponse<WarmEventVO> publishWarmEvent(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @RequestBody CreateWarmEventRequest request) {
        return ApiResponse.ok(warmMapService.publishWarmEvent(
                userId, request.title(), request.content(), request.lat(), request.lng()));
    }

    @GetMapping("/warm-events")
    @Operation(summary = "温暖事件列表", description = "附近可见事件（模糊化坐标 + 距离升序）；"
            + "cityCode 为 geohash4 城市代理（可选按城市过滤）；空坐标默认城市兜底")
    @SentinelResource("WISH_WARM_EVENT_LIST")
    public ApiResponse<List<WarmEventVO>> listWarmEvents(
            @Parameter(description = "纬度（可空）") @RequestParam(required = false) Double lat,
            @Parameter(description = "经度（可空）") @RequestParam(required = false) Double lng,
            @Parameter(description = "半径（米，默认 5000，最大 50000）") @RequestParam(required = false) Integer radius,
            @Parameter(description = "城市代理（可选，geohash4）") @RequestParam(required = false) String cityCode) {
        return ApiResponse.ok(warmMapService.listWarmEvents(lat, lng, radius, cityCode));
    }

    @GetMapping("/warm-events/cluster")
    @Operation(summary = "温暖事件网格聚合", description = "geohash6 数量角标（坐标=网格中心）")
    @SentinelResource("WISH_WARM_EVENT_CLUSTER")
    public ApiResponse<List<MapClusterVO>> clusterWarmEvents(
            @Parameter(description = "纬度（可空）") @RequestParam(required = false) Double lat,
            @Parameter(description = "经度（可空）") @RequestParam(required = false) Double lng,
            @Parameter(description = "半径（米）") @RequestParam(required = false) Integer radius,
            @Parameter(description = "城市代理（可选）") @RequestParam(required = false) String cityCode) {
        return ApiResponse.ok(warmMapService.clusterWarmEvents(lat, lng, radius, cityCode));
    }
}
