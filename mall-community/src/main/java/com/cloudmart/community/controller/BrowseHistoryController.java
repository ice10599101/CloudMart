package com.cloudmart.community.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.community.dto.RecordBrowseRequest;
import com.cloudmart.community.service.BrowseHistoryService;
import com.cloudmart.community.vo.BrowseHistoryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 浏览足迹：商品/帖子/心愿详情页打开时上报，个人中心历史页签分页展示。
 * 上报与查询均要求登录（X-User-Id 必填），仅允许操作本人的足迹数据。
 */
@RestController
@RequestMapping("/browse-history")
@Tag(name = "浏览足迹", description = "浏览足迹上报与查询（仅本人）")
@RequiredArgsConstructor
public class BrowseHistoryController {

    private final BrowseHistoryService browseHistoryService;

    @PostMapping
    @Operation(summary = "上报浏览足迹", description = "打开商品/帖子/心愿详情页时上报；同一对象重复浏览仅刷新时间与快照")
    @SentinelResource("BROWSE_HISTORY_RECORD")
    public ApiResponse<Void> record(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @RequestBody RecordBrowseRequest request) {
        browseHistoryService.recordBrowse(
                userId, request.targetType(), request.targetId(), request.title(), request.cover());
        return ApiResponse.ok(null);
    }

    @GetMapping
    @Operation(summary = "我的浏览足迹", description = "分页查询当前用户的浏览足迹，按最近浏览时间倒序")
    @SentinelResource("BROWSE_HISTORY_LIST")
    public ApiResponse<List<BrowseHistoryVO>> listMine(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size) {
        Page<BrowseHistoryVO> result = browseHistoryService.listMyHistory(userId, page, size);
        return ApiResponse.ok(result.getRecords(), new ApiResponse.Meta(page, size, result.getTotal()));
    }
}