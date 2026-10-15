package com.cloudmart.software.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.software.service.AdminSoftwareStatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端运营统计（方案 7.5/10.7，P1）：仅 mall-admin 代理可达；
 * 权限裁决（software:stats）在代理层。
 */
@RestController
@RequestMapping("/admin/software")
@RequiredArgsConstructor
@PreAuthorize("hasRole('INTERNAL')")
@Tag(name = "管理端-统计", description = "发布/审核时长/获取链接/收藏/评分/待办（P1）")
public class AdminSoftwareStatisticsController {

    private final AdminSoftwareStatisticsService adminSoftwareStatisticsService;

    @GetMapping("/statistics")
    @Operation(summary = "运营统计总览",
            description = "在架数/待审数/平均审核分钟/获取链接次数与人数/收藏/新评分/待办；"
                    + "口径与窗口随响应返回，不显示外部下载完成率")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.ok(adminSoftwareStatisticsService.overview());
    }
}
