package com.cloudmart.community.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.api.ApiResponse.Meta;
import com.cloudmart.community.dto.CreateTagRequest;
import com.cloudmart.community.dto.UpdateTagRequest;
import com.cloudmart.community.service.TagService;
import com.cloudmart.community.vo.TagVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/tags")
@Tag(name = "标签管理(后台)", description = "管理后台标签管理接口")
@RequiredArgsConstructor
public class AdminTagController {

    private final TagService tagService;

    @GetMapping
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "标签列表", description = "管理后台分页查询标签列表")
    public ApiResponse<List<TagVO>> listTags(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size) {
        Page<TagVO> result = tagService.listTags(page, size);
        return ApiResponse.ok(result.getRecords(), new Meta(page, size, result.getTotal()));
    }

    @PostMapping
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "创建标签", description = "管理后台创建新标签")
    public ApiResponse<TagVO> createTag(
            @Parameter(description = "创建标签请求") @Valid @RequestBody CreateTagRequest request) {
        TagVO vo = tagService.createTag(request);
        return ApiResponse.ok(vo);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "更新标签", description = "管理后台更新标签信息")
    public ApiResponse<TagVO> updateTag(
            @Parameter(description = "标签ID", required = true) @PathVariable("id") Long tagId,
            @Parameter(description = "更新标签请求") @Valid @RequestBody UpdateTagRequest request) {
        TagVO vo = tagService.updateTag(tagId, request);
        return ApiResponse.ok(vo);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "删除标签", description = "管理后台删除标签")
    public ApiResponse<Void> deleteTag(
            @Parameter(description = "标签ID", required = true) @PathVariable("id") Long tagId) {
        tagService.deleteTag(tagId);
        return ApiResponse.ok(null);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "切换标签状态", description = "管理后台切换标签启用/禁用状态")
    public ApiResponse<Void> updateTagStatus(
            @Parameter(description = "标签ID", required = true) @PathVariable("id") Long tagId,
            @Parameter(description = "状态值") @RequestParam Integer status) {
        tagService.updateTagStatus(tagId, status);
        return ApiResponse.ok(null);
    }
}
