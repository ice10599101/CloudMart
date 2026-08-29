package com.cloudmart.wish.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.wish.service.LiveWidgetService;
import com.cloudmart.wish.vo.LiveWidgetVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 直播心愿挂件 Controller（Sprint 3.4，文档 3.4：GET /wish/live/widget/**）。
 *
 * <p>公开端点（permitAll）：数据源与 live 模块解耦——直播间前端叠加挂件时
 * 直接调 wish 服务；10s 缓存支撑千级观众轮询；主播无心愿返回
 * hasWish=false（前端"去许愿"引导）；全局降级 → visible=false。</p>
 */
@RestController
@RequestMapping("/live/widget")
@Tag(name = "直播心愿挂件", description = "主播心愿进度/打卡天数/星光挂件数据（Sprint 3.4）")
@RequiredArgsConstructor
public class LiveWidgetController {

    private final LiveWidgetService liveWidgetService;

    @GetMapping("/{streamerId}")
    @Operation(summary = "挂件数据", description = "公开；Redis 缓存 TTL 10s；"
            + "visible=false 时前端隐藏挂件；hasWish=false 时前端展示去许愿引导；"
            + "不含主播手机号/邮箱等隐私字段")
    @SentinelResource("WISH_LIVE_WIDGET")
    public ApiResponse<LiveWidgetVO> widget(
            @Parameter(description = "主播用户 ID（直播间 anchorUserId）", required = true)
            @PathVariable Long streamerId) {
        return ApiResponse.ok(liveWidgetService.getWidgetData(streamerId));
    }
}
