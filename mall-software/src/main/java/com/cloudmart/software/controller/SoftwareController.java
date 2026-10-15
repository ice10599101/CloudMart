package com.cloudmart.software.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.software.dto.software.SoftwareListQuery;
import com.cloudmart.software.service.SoftwareQueryService;
import com.cloudmart.software.vo.software.SoftwareAccessPolicyVO;
import com.cloudmart.software.vo.software.SoftwareCardVO;
import com.cloudmart.software.vo.software.SoftwareCategoryVO;
import com.cloudmart.software.vo.software.SoftwareDetailVO;
import com.cloudmart.software.vo.software.SoftwareReleaseVO;
import com.cloudmart.software.vo.software.SoftwareTagVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 小答软件公开只读端点（方案 7.2）：匿名可浏览；
 * 全部响应不含真实链接/提取码/解压密码；固定路径在 SecurityConfig 先于
 * /software/{id} 动态匹配声明。登录态可选（X-User-Id 已由公共过滤器权威化，
 * 匿名请求被身份头护栏剥离）。
 */
@RestController
@RequestMapping("/software")
@RequiredArgsConstructor
@Tag(name = "小答软件-公开浏览", description = "广场/分类/标签/详情/查看条件摘要/版本来源摘要")
public class SoftwareController {

    private final SoftwareQueryService softwareQueryService;

    @GetMapping
    @Operation(summary = "软件广场", description = "keyword/categoryId/platform/license/tagId/sort 筛选；仅已发布可见")
    public ApiResponse<Page<SoftwareCardVO>> list(SoftwareListQuery query) {
        return ApiResponse.ok(softwareQueryService.pageCards(query));
    }

    @GetMapping("/categories")
    @Operation(summary = "分类字典", description = "启用中的分类（禁用分类不作为新筛选项）")
    public ApiResponse<List<SoftwareCategoryVO>> categories() {
        return ApiResponse.ok(softwareQueryService.categories());
    }

    @GetMapping("/tags")
    @Operation(summary = "标签检索", description = "仅启用标签；keyword 匹配标签名")
    public ApiResponse<Page<SoftwareTagVO>> tags(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(softwareQueryService.pageTags(keyword, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "软件详情",
            description = "介绍/截图/统计；无源 URL 和密码；登录个性化 isFavorited（游客 null）")
    public ApiResponse<SoftwareDetailVO> detail(
            @PathVariable("id") Long id,
            @RequestHeader(value = SecurityConstants.USER_ID_HEADER, required = false) Long viewerId) {
        return ApiResponse.ok(softwareQueryService.detail(id, viewerId));
    }

    @GetMapping("/{id}/access-policy")
    @Operation(summary = "查看条件摘要",
            description = "当前线上条件；无用户授权记录、付款备注、源 URL 或提取码")
    public ApiResponse<SoftwareAccessPolicyVO> accessPolicy(@PathVariable("id") Long id) {
        return ApiResponse.ok(softwareQueryService.accessPolicy(id));
    }

    @GetMapping("/{id}/releases")
    @Operation(summary = "版本与来源摘要",
            description = "稳定版在前；来源摘要无 URL；可用性按指纹匹配展示")
    public ApiResponse<Page<SoftwareReleaseVO>> releases(
            @PathVariable("id") Long id,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(softwareQueryService.releases(id, page, size));
    }
}
