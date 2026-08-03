package com.cloudmart.community.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.api.ApiResponse.Meta;
import com.cloudmart.community.service.TagService;
import com.cloudmart.community.vo.TagVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tags")
@Tag(name = "标签管理", description = "话题标签接口")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @GetMapping("/hot")
    @Operation(summary = "热门标签", description = "获取热门标签列表")
    public ApiResponse<List<TagVO>> getHotTags() {
        List<TagVO> tags = tagService.getHotTags();
        return ApiResponse.ok(tags);
    }

    @GetMapping("/trending")
    @Operation(summary = "热门话题排行", description = "获取基于帖子数量的热门话题排行")
    public ApiResponse<List<TagVO>> getTrendingTopics(
            @Parameter(description = "数量限制") @RequestParam(defaultValue = "10") int limit) {
        List<TagVO> tags = tagService.getTrendingTopics(limit);
        return ApiResponse.ok(tags);
    }

    @GetMapping
    @Operation(summary = "标签列表", description = "分页获取标签列表")
    public ApiResponse<List<TagVO>> listTags(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size) {
        Page<TagVO> result = tagService.listTags(page, size);
        return ApiResponse.ok(result.getRecords(), new Meta(page, size, result.getTotal()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "标签详情", description = "根据ID获取标签信息")
    public ApiResponse<TagVO> getTagById(
            @Parameter(description = "标签ID", required = true) @PathVariable("id") Long tagId) {
        TagVO vo = tagService.getTagById(tagId);
        return ApiResponse.ok(vo);
    }
}
