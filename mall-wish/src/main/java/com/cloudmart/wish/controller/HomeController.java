package com.cloudmart.wish.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.wish.service.HomeService;
import com.cloudmart.wish.vo.HomeAggregationVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 心愿宇宙首页聚合 Controller（对应文档 2.18 节）。
 *
 * <p>首页一次请求返回所有模块的轻量摘要，避免客户端多次请求。</p>
 */
@RestController
@RequestMapping("/home")
@Tag(name = "首页聚合", description = "心愿宇宙首页聚合数据接口")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    @GetMapping
    @Operation(summary = "首页聚合数据", description = "返回今日推荐、我的心愿摘要、热门共鸣、入口开关")
    @SentinelResource("HOME_AGGREGATION")
    public ApiResponse<HomeAggregationVO> getHomeAggregation(
            @Parameter(description = "当前用户 ID（网关注入，可空）")
            @RequestHeader(name = SecurityConstants.USER_ID_HEADER, required = false) Long userId) {
        HomeAggregationVO vo = homeService.getHomeAggregation(userId);
        return ApiResponse.ok(vo);
    }
}
