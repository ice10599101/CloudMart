package com.cloudmart.wish.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.wish.service.GrayscaleService;
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
import java.util.Map;

/**
 * 灰度功能开关 Controller（Sprint 2.8，文档 2.8 前端降级开关数据源）。
 *
 * <p>公开端点（permitAll）：四端启动时拉取一次（前端本地缓存），
 * 按当前用户哈希分流；匿名仅全量功能放行。降级开关语义：
 * flag=false 时前端回退到上一代体验（如 3D 关闭自动旋转/粒子减半），
 * 灰度切换对用户无感知（回滚 = 比例置 0）。</p>
 */
@RestController
@RequestMapping("/feature-flags")
@Tag(name = "灰度功能开关", description = "按用户哈希分流的功能开关（Sprint 2.8 灰度控制）")
@RequiredArgsConstructor
public class FeatureFlagController {

    private final GrayscaleService grayscaleService;

    @GetMapping
    @Operation(summary = "功能开关批量查询", description = "features 为空返回全部功能；"
            + "同一用户恒命中同一灰度档（稳定哈希）；匿名用户仅全量功能放行")
    @SentinelResource("WISH_FEATURE_FLAGS")
    public ApiResponse<Map<String, Boolean>> flags(
            @Parameter(description = "当前用户 ID（网关注入，匿名可空）")
            @RequestHeader(value = SecurityConstants.USER_ID_HEADER, required = false) Long userId,
            @Parameter(description = "功能键清单（逗号分隔，空=全部）")
            @RequestParam(required = false) String features) {
        List<String> keys = features == null || features.isBlank()
                ? List.of()
                : List.of(features.split(","));
        return ApiResponse.ok(grayscaleService.flagsOf(userId, keys));
    }
}
