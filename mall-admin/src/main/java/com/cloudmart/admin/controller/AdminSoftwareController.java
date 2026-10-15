package com.cloudmart.admin.controller;

import com.cloudmart.admin.feign.SoftwareFeignClient;
import com.cloudmart.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 小答软件管理代理（方案 7.1/7.5）：外部 /api/admin/software/** 经 admin 路由
 * 到本控制器，由 SoftwareFeignClient 内部转发 mall-software /admin/software/**。
 * 审核 decision 权限为 software:review；终端不可直调软件原生 /admin/**（网关拦截）。
 * S04 先交付审核决策；资格/授权/举报等 S09 扩展。
 */
@RestController
@RequestMapping("/software")
@RequiredArgsConstructor
@Tag(name = "管理端-小答软件", description = "软件审核等管理代理（S04 审核决策，S09 扩展）")
public class AdminSoftwareController {

    private final SoftwareFeignClient softwareFeignClient;

    @PostMapping("/revisions/{revisionId}/decision")
    @Operation(summary = "审核决策",
            description = "通过/退回绑定提交批次；操作留痕于软件域审核日志与权限审计")
    public ApiResponse<Object> decision(
            @PathVariable("revisionId") Long revisionId,
            @RequestBody Map<String, Object> body) {
        return softwareFeignClient.decision(revisionId, body);
    }
}
