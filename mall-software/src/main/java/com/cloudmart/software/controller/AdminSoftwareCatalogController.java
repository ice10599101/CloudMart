package com.cloudmart.software.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.software.dto.software.AdminCategorySaveRequest;
import com.cloudmart.software.dto.software.AdminFeaturedRequest;
import com.cloudmart.software.dto.software.AdminItemQuery;
import com.cloudmart.software.dto.software.AdminSoftwareBanRequest;
import com.cloudmart.software.dto.software.AdminTagSaveRequest;
import com.cloudmart.software.service.AdminSoftwareCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 管理端软件目录与分类标签（方案 7.5/10.1/10.5）：仅 mall-admin 代理可达。
 * 权限裁决（software:list/moderate/feature/category）在代理层 @RequiresPermission，
 * 本层以 INTERNAL 服务身份为边界（纵深防御双层）。
 */
@RestController
@RequestMapping("/admin/software")
@RequiredArgsConstructor
@PreAuthorize("hasRole('INTERNAL')")
@Tag(name = "管理端-软件目录", description = "目录查询/封禁解除/精选排序/分类标签维护")
public class AdminSoftwareCatalogController {

    private final AdminSoftwareCatalogService adminSoftwareCatalogService;
    private final com.cloudmart.software.service.SoftwareRelationService relationService;
    private final com.cloudmart.software.service.SoftwareCompareService compareService;

    // ---------------- 目录（software:list / moderate / feature） ----------------

    @GetMapping("/items")
    @Operation(summary = "软件目录",
            description = "keyword/ownerId/categoryId/license/status 筛选；"
                    + "含线上、待审、精选、下架、封禁（不含已删除）")
    public ApiResponse<Page<Map<String, Object>>> items(AdminItemQuery query) {
        return ApiResponse.ok(adminSoftwareCatalogService.itemPage(query));
    }

    @PostMapping("/items/{softwareId}/ban")
    @Operation(summary = "独立封禁",
            description = "必须原因；BANNED 后作者不可自行恢复；服务端行锁+审计+通知分享者")
    public ApiResponse<Boolean> ban(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId,
            @PathVariable("softwareId") Long softwareId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody AdminSoftwareBanRequest request) {
        return ApiResponse.ok(adminSoftwareCatalogService.ban(
                adminUserId, softwareId, idempotencyKey,
                request.getReason()));
    }

    @PostMapping("/items/{softwareId}/unban")
    @Operation(summary = "解除封禁",
            description = "BANNED → UNLISTED，由作者恢复展示；非封禁状态返回冲突")
    public ApiResponse<Boolean> unban(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId,
            @PathVariable("softwareId") Long softwareId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody AdminSoftwareBanRequest request) {
        return ApiResponse.ok(adminSoftwareCatalogService.unban(
                adminUserId, softwareId, idempotencyKey,
                request.getReason()));
    }

    @PutMapping("/items/{softwareId}/featured")
    @Operation(summary = "精选排序",
            description = "featuredRank=0 取消精选；值越大越靠前；服务端行锁与幂等键保证")
    public ApiResponse<Boolean> featured(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId,
            @PathVariable("softwareId") Long softwareId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody AdminFeaturedRequest request) {
        return ApiResponse.ok(adminSoftwareCatalogService.feature(
                adminUserId, softwareId, idempotencyKey,
                request.getFeaturedRank()));
    }

    // ---------------- 结构化声明（UX04-B；software:manage 守卫沿用类级 INTERNAL） ----------------

    @GetMapping("/items/{softwareId}/declared")
    @Operation(summary = "结构化声明读取", description = "对比用客观字段；null=未提供")
    public ApiResponse<java.util.Map<String, Object>> declared(@PathVariable("softwareId") Long softwareId) {
        return ApiResponse.ok(compareService.declaredOf(softwareId));
    }

    @PutMapping("/items/{softwareId}/declared")
    @Operation(summary = "结构化声明保存", description = "是否开源/离线可用（null=清除为未提供）；不伪造测试结论")
    public ApiResponse<Boolean> saveDeclared(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId,
            @PathVariable("softwareId") Long softwareId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody java.util.Map<String, Object> body) {
        Object open = body.get("openSource");
        Object offline = body.get("offlineCapable");
        Object note = body.get("declaredNote");
        compareService.saveDeclared(softwareId,
                open instanceof Boolean b ? b : null,
                offline instanceof Boolean b2 ? b2 : null,
                note == null ? null : String.valueOf(note),
                adminUserId, idempotencyKey);
        return ApiResponse.ok(true);
    }

    // ---------------- 同类关系（UX03；software:manage 守卫沿用类级 INTERNAL） ----------------

    @GetMapping("/items/{softwareId}/relations")
    @Operation(summary = "同类关系清单", description = "管理视图全量（不过滤可见性，供编辑检查目标状态）")
    public ApiResponse<List<Map<String, Object>>> relations(@PathVariable("softwareId") Long softwareId) {
        return ApiResponse.ok(relationService.listRelations(softwareId));
    }

    @PutMapping("/items/{softwareId}/relations")
    @Operation(summary = "同类关系全量替换",
            description = "≤4 条；禁自关联/去重/目标须已发布；type=SIMILAR_USE/ALTERNATIVE_PLATFORM")
    public ApiResponse<Integer> saveRelations(
            @PathVariable("softwareId") Long softwareId,
            @RequestBody com.cloudmart.software.dto.software.AdminRelationSaveRequest request) {
        return ApiResponse.ok(relationService.saveRelations(softwareId, request));
    }

    // ---------------- 分类与标签（software:category） ----------------

    @GetMapping("/categories")
    @Operation(summary = "分类列表（含禁用）", description = "运营视图；按 sortOrder 升序")
    public ApiResponse<List<Map<String, Object>>> categories() {
        return ApiResponse.ok(adminSoftwareCatalogService.categories());
    }

    @PostMapping("/categories")
    @Operation(summary = "新增分类", description = "slug 唯一；禁用后不得新选")
    public ApiResponse<Long> createCategory(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody AdminCategorySaveRequest request) {
        return ApiResponse.ok(adminSoftwareCatalogService.saveCategory(
                adminUserId, null, idempotencyKey, request));
    }

    @PutMapping("/categories/{categoryId}")
    @Operation(summary = "更新分类", description = "含启用/禁用与排序调整")
    public ApiResponse<Long> updateCategory(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId,
            @PathVariable("categoryId") Long categoryId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody AdminCategorySaveRequest request) {
        return ApiResponse.ok(adminSoftwareCatalogService.saveCategory(
                adminUserId, categoryId, idempotencyKey, request));
    }

    @DeleteMapping("/categories/{categoryId}")
    @Operation(summary = "禁用分类",
            description = "DELETE 语义为禁用；不做物理删除（被引用分类删除会让详情报错）")
    public ApiResponse<Boolean> disableCategory(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId,
            @PathVariable("categoryId") Long categoryId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(adminSoftwareCatalogService.disableCategory(
                adminUserId, categoryId, idempotencyKey));
    }

    @GetMapping("/tags")
    @Operation(summary = "标签分页（含禁用）", description = "keyword 匹配标签名")
    public ApiResponse<Page<Map<String, Object>>> tags(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(adminSoftwareCatalogService.tags(keyword, page, size));
    }

    @PostMapping("/tags")
    @Operation(summary = "新增标签", description = "规范化去重；作者只能从启用标签中选择")
    public ApiResponse<Long> createTag(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody AdminTagSaveRequest request) {
        return ApiResponse.ok(adminSoftwareCatalogService.saveTag(
                adminUserId, null, idempotencyKey, request));
    }

    @PutMapping("/tags/{tagId}")
    @Operation(summary = "更新标签", description = "含启用/禁用")
    public ApiResponse<Long> updateTag(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId,
            @PathVariable("tagId") Long tagId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody AdminTagSaveRequest request) {
        return ApiResponse.ok(adminSoftwareCatalogService.saveTag(
                adminUserId, tagId, idempotencyKey, request));
    }

    @DeleteMapping("/tags/{tagId}")
    @Operation(summary = "禁用标签", description = "DELETE 语义为禁用；不做物理删除")
    public ApiResponse<Boolean> disableTag(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId,
            @PathVariable("tagId") Long tagId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(adminSoftwareCatalogService.disableTag(
                adminUserId, tagId, idempotencyKey));
    }
}
