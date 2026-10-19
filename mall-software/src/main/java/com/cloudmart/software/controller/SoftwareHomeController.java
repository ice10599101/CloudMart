package com.cloudmart.software.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.software.service.SoftwareHomeSectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 公共首页聚合（UX01）：enabled + 投放期内 + 平台匹配的固定区块。
 * 每块独立失败协议（state=ERROR 仅标记本块）；只含公开 DTO；
 * 不可见目标实时过滤，公共配置缓存与软件可见性过滤分离。
 */
@RestController
@RequestMapping("/software")
@RequiredArgsConstructor
@Tag(name = "小答软件-首页区块", description = "UX01 首页固定区块聚合（公共）")
public class SoftwareHomeController {

    private final SoftwareHomeSectionService homeSectionService;

    @GetMapping("/home")
    @Operation(summary = "首页区块聚合",
            description = "platform=WEB/APP/TARO（缺省 WEB）；每块独立失败协议；缺内容区块按策略隐藏或显式空态")
    public ApiResponse<List<Map<String, Object>>> home(
            @RequestParam(value = "platform", required = false) String platform) {
        return ApiResponse.ok(homeSectionService.publicHome(platform));
    }
}
