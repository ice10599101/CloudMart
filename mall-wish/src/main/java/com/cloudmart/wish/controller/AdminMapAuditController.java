package com.cloudmart.wish.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.enums.AuditStatus;
import com.cloudmart.wish.enums.WishVisibility;
import com.cloudmart.wish.repository.WishMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 管理后台 LBS 隐私审计 Controller（Sprint 3.1 管理后台：隐私审计面板）。
 *
 * <p>路由前缀 /admin/map，仅内部服务调用；权限点 {@code business:map:audit}。</p>
 */
@RestController
@RequestMapping("/admin/map")
@PreAuthorize("hasRole('INTERNAL')")
@RequiredArgsConstructor
@Tag(name = "管理后台-LBS 隐私审计", description = "地图心愿分布统计 + geohash 覆盖审计（Sprint 3.1）")
public class AdminMapAuditController {

    private final WishMapper wishMapper;

    @GetMapping("/audit")
    @Operation(summary = "隐私审计面板", description = "PUBLIC 心愿 geohash 覆盖统计 + 模糊化策略说明；"
            + "审计依据：DB 仅存 geohash7（wish.geohash 列），无 lat/lng 原始坐标列")
    public ApiResponse<Map<String, Object>> audit() {
        long publicCount = wishMapper.selectCount(new LambdaQueryWrapper<Wish>()
                .eq(Wish::getVisibility, WishVisibility.PUBLIC)
                .eq(Wish::getAuditStatus, AuditStatus.APPROVED)
                .eq(Wish::getIsVisible, true));
        long covered = wishMapper.selectCount(new LambdaQueryWrapper<Wish>()
                .eq(Wish::getVisibility, WishVisibility.PUBLIC)
                .eq(Wish::getAuditStatus, AuditStatus.APPROVED)
                .eq(Wish::getIsVisible, true)
                .isNotNull(Wish::getGeohash));
        long distinctCells = wishMapper.selectList(new LambdaQueryWrapper<Wish>()
                        .eq(Wish::getVisibility, WishVisibility.PUBLIC)
                        .eq(Wish::getAuditStatus, AuditStatus.APPROVED)
                        .eq(Wish::getIsVisible, true)
                        .isNotNull(Wish::getGeohash)
                        .select(Wish::getGeohash))
                .stream()
                .map(w -> w.getGeohash() == null || w.getGeohash().length() < 6
                        ? null : w.getGeohash().substring(0, 6))
                .distinct()
                .filter(java.util.Objects::nonNull)
                .count();

        Map<String, Object> result = new HashMap<>();
        result.put("publicWishCount", publicCount);
        result.put("geohashCovered", covered);
        result.put("geohashMissing", publicCount - covered);
        result.put("distinctCell6", distinctCells);
        result.put("strategy", Map.of(
                "storage", "DB 仅存 wish.geohash（geohash7，约 153m 网格），无 lat/lng 原始坐标列",
                "offset", "展示坐标 = geohash7 网格中心 + wishId 种子确定性偏移（0-50m，可复现）",
                "cluster", "网格聚合返回 geohash6 中心 + 数量角标，不返回单个精确点",
                "logging", "日志全链路不打印原始坐标（原始坐标仅请求处理期间内存存在）"));
        return ApiResponse.ok(result);
    }
}
