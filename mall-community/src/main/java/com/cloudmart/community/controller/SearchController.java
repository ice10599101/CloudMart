package com.cloudmart.community.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.community.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/search")
@Tag(name = "搜索增强", description = "搜索历史与热搜词接口")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping("/history")
    @Operation(summary = "搜索历史", description = "获取当前用户的搜索历史")
    public ApiResponse<List<String>> getSearchHistory(
            @Parameter(hidden = true) @RequestHeader(name = SecurityConstants.USER_ID_HEADER, required = false) Long userId,
            @Parameter(description = "数量限制") @RequestParam(defaultValue = "10") int limit) {
        if (userId == null) {
            return ApiResponse.ok(List.of());
        }
        List<String> history = searchService.getUserSearchHistory(userId, limit);
        return ApiResponse.ok(history);
    }

    @DeleteMapping("/history")
    @Operation(summary = "清空搜索历史", description = "清空当前用户的搜索历史")
    public ApiResponse<Void> clearSearchHistory(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        searchService.clearUserSearchHistory(userId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/hot")
    @Operation(summary = "热搜词", description = "获取热搜词列表")
    public ApiResponse<List<String>> getHotSearches(
            @Parameter(description = "数量限制") @RequestParam(defaultValue = "10") int limit) {
        List<String> hotSearches = searchService.getHotSearches(limit);
        return ApiResponse.ok(hotSearches);
    }
}
