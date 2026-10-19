package com.cloudmart.software.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.software.dto.software.SoftwareRequestCreateInput;
import com.cloudmart.software.service.SoftwareRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * UX06：找软件需求（SOFTWARE_DISCOVERY）。公开入口只展示真实内容（≤3 条）；
 * 提交进入审核；恶意/重复需求可审核；已找到的软件下架后标注可解释。
 */
@RestController
@RequestMapping("/software/requests")
@RequiredArgsConstructor
@Tag(name = "小答软件-需求", description = "UX06 找软件需求（独立轻表，无奖励系统）")
public class SoftwareRequestController {

    private final SoftwareRequestService requestService;

    @GetMapping("/public")
    @Operation(summary = "大家在找什么", description = "APPROVED 且 OPEN/ANSWERED ≤3 条；无内容返回空数组（空态给提交入口）")
    public ApiResponse<List<Map<String, Object>>> publicFeed() {
        return ApiResponse.ok(requestService.publicFeed());
    }

    @PostMapping
    @Operation(summary = "提交需求", description = "结构化求助（用途/平台/必须功能/可接受许可）；进入审核；可带来源搜索关键词")
    public ApiResponse<Long> create(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @RequestBody SoftwareRequestCreateInput input) {
        return ApiResponse.ok(requestService.create(userId, input));
    }

    @GetMapping("/mine")
    @Operation(summary = "我的需求", description = "含审核状态与拒绝原因（本人可见）")
    public ApiResponse<List<Map<String, Object>>> mine(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(requestService.mine(userId));
    }

    /** 发起人关闭（幂等；软删除语义由 deleted_at 独立管理） */
    @PostMapping("/{requestId}/close")
    @Operation(summary = "关闭我的需求", description = "发起人本人；重复关闭幂等")
    public ApiResponse<Boolean> close(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("requestId") Long requestId) {
        return ApiResponse.ok(requestService.close(userId, requestId));
    }

    /** DELETE 语义：发起人删除自己的需求（软删除） */
    @DeleteMapping("/{requestId}")
    @Operation(summary = "删除我的需求（软删除）")
    public ApiResponse<Boolean> deleteMine(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("requestId") Long requestId) {
        // 复用 close：前台删除=关闭（保留数据供审核追溯），软删除仅后台操作
        return ApiResponse.ok(requestService.close(userId, requestId));
    }
}
