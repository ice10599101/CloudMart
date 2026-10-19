package com.cloudmart.software.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.software.service.SoftwareGuideService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * UX04-A：软件教程公开读取。正文在社区（客户端跳转社区文章页），
 * 软件入口实时校验文章公开状态——文章撤回立即隐藏。
 */
@RestController
@RequestMapping("/software/guides")
@RequiredArgsConstructor
@Tag(name = "小答软件-教程", description = "UX04 教程/选型攻略（公开；正文真源在社区）")
public class SoftwareGuideController {

    private final SoftwareGuideService guideService;

    @GetMapping
    @Operation(summary = "教程分页",
            description = "guideType=INSTALL_GUIDE/WORKFLOW/COMPARISON/TROUBLESHOOTING；仅 enabled 且文章公开")
    public ApiResponse<Map<String, Object>> guides(
            @RequestParam(value = "guideType", required = false) String guideType,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "12") int size) {
        return ApiResponse.ok(guideService.publicGuides(guideType, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "教程详情",
            description = "含适用平台/业务版本范围/检查日期/关联软件（实时过滤不可见）与社区文章跳转")
    public ApiResponse<Map<String, Object>> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(guideService.publicGuideDetail(id));
    }
}
