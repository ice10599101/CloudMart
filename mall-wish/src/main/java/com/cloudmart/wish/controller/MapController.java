package com.cloudmart.wish.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.wish.service.NearbyWishService;
import com.cloudmart.wish.vo.MapClusterVO;
import com.cloudmart.wish.vo.NearbyWishVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * LBS 地图 Controller（Sprint 3.1，文档 2.10：GET /wish/map/**）。
 *
 * <p>公开浏览（permitAll，仅返回 PUBLIC 心愿）；坐标经 geohash 模糊化，
 * 不返回原始精确坐标（隐私验收）；空坐标兜底默认城市。</p>
 */
@RestController
@RequestMapping("/map")
@Tag(name = "LBS 地图", description = "附近心愿（模糊化坐标）+ 网格聚合（Sprint 3.1）")
@RequiredArgsConstructor
public class MapController {

    private final NearbyWishService nearbyWishService;

    @GetMapping("/wishes")
    @Operation(summary = "附近心愿", description = "传入 lat/lng/radius 返回模糊化坐标的心愿列表；"
            + "radius null/0/负数/超 50km → 默认 5km；空坐标（null/0,0）→ 默认城市兜底；"
            + "geohash 参数优先于 lat/lng（长度<6 或非法字符 → 拒绝）。"
            + "approximateLat/Lng 为 geohash7 网格中心 + 确定性偏移（0-50m），不含精确坐标")
    @SentinelResource("WISH_MAP_NEARBY")
    public ApiResponse<List<NearbyWishVO>> nearby(
            @Parameter(description = "当前用户 ID（网关注入，匿名可空）")
            @RequestHeader(value = SecurityConstants.USER_ID_HEADER, required = false) Long userId,
            @Parameter(description = "纬度（可空）") @RequestParam(required = false) Double lat,
            @Parameter(description = "经度（可空）") @RequestParam(required = false) Double lng,
            @Parameter(description = "半径（米，默认 5000，最大 50000）")
            @RequestParam(required = false) Integer radius,
            @Parameter(description = "geohash（可选，6/7 位，优先于 lat/lng）")
            @RequestParam(required = false) String geohash) {
        return ApiResponse.ok(nearbyWishService.nearby(userId, lat, lng, radius, geohash));
    }

    @GetMapping("/cluster")
    @Operation(summary = "网格聚合", description = "附近心愿按 geohash6 网格聚合（数量角标，"
            + "坐标=网格中心，不返回单个精确点——隐私验收）；Redis 缓存命中 P95<300ms")
    @SentinelResource("WISH_MAP_CLUSTER")
    public ApiResponse<List<MapClusterVO>> cluster(
            @Parameter(description = "当前用户 ID（网关注入，匿名可空）")
            @RequestHeader(value = SecurityConstants.USER_ID_HEADER, required = false) Long userId,
            @Parameter(description = "纬度（可空）") @RequestParam(required = false) Double lat,
            @Parameter(description = "经度（可空）") @RequestParam(required = false) Double lng,
            @Parameter(description = "半径（米，默认 5000，最大 50000）")
            @RequestParam(required = false) Integer radius,
            @Parameter(description = "geohash（可选，6/7 位）") @RequestParam(required = false) String geohash) {
        return ApiResponse.ok(nearbyWishService.cluster(userId, lat, lng, radius, geohash));
    }
}
