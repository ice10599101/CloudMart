package com.cloudmart.software.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.software.service.SoftwareCompareService;
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
 * UX04-B：软件对比（公开）。对比项以 ID 写 URL 可分享；
 * 每次读取重校验可见性；缺失字段=未提供，不推断。
 */
@RestController
@RequestMapping("/software")
@RequiredArgsConstructor
@Tag(name = "小答软件-对比", description = "UX04-B 软件并排对比（2~3 款）")
public class SoftwareCompareController {

    private final SoftwareCompareService compareService;

    @GetMapping("/compare")
    @Operation(summary = "软件对比",
            description = "ids=逗号分隔 2~3 个去重 ID；返回公开字段与缺失项（null=未提供）；许可只含已知类别不含实时价格")
    public ApiResponse<List<Map<String, Object>>> compare(
            @RequestParam("ids") String ids) {
        return ApiResponse.ok(compareService.compare(ids));
    }
}
