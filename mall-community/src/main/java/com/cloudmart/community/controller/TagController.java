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

    /**
     * 按名称解析标签（不存在则创建，幂等）。
     * 发布帖子时用户输入的是标签名，后端 CreatePostRequest 接收 tagIds，
     * 三端发布前先经此接口把标签名解析为 tagIds。
     */
    @PostMapping("/resolve")
    @Operation(summary = "解析标签", description = "按名称解析标签，不存在则创建（幂等）；返回顺序与输入去重后一致")
    public ApiResponse<List<TagVO>> resolveTags(
            @RequestBody ResolveTagsRequest request) {
        List<TagVO> tags = tagService.resolveTags(request.names());
        return ApiResponse.ok(tags);
    }

    /** 标签名解析请求体 */
    public record ResolveTagsRequest(List<String> names) {
    }

    @GetMapping("/{id}")
    @Operation(summary = "标签详情", description = "根据ID获取标签信息")
    public ApiResponse<TagVO> getTagById(
            @Parameter(description = "标签ID", required = true) @PathVariable("id") Long tagId) {
        TagVO vo = tagService.getTagById(tagId);
        return ApiResponse.ok(vo);
    }
}
