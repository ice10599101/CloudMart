package com.cloudmart.wish.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.wish.dto.ExchangeAssetRequest;
import com.cloudmart.wish.entity.UserAsset;
import com.cloudmart.wish.service.CollectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 虚拟收藏 + 品牌合作 Controller（Sprint 3.6，文档 2.22/2.16）。
 *
 * <p>工坊列表公开浏览；兑换/收藏馆/切换/收藏/品牌池需登录。</p>
 */
@RestController
@Tag(name = "虚拟收藏 + 品牌合作", description = "收藏馆/虚拟工坊/皮肤切换/品牌许愿池（Sprint 3.6）")
@RequiredArgsConstructor
public class CollectionController {

    private final CollectionService collectionService;

    @GetMapping("/collections/assets")
    @Operation(summary = "收藏馆", description = "按 BADGE/SKIN/BGM/SPECIAL_FRUIT 分组；星火收藏品含关联心愿")
    @SentinelResource("WISH_COLLECTIONS")
    public ApiResponse<Map<String, List<Map<String, Object>>>> collections(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(collectionService.collections(userId));
    }

    @GetMapping("/workshop/assets")
    @Operation(summary = "工坊资产列表", description = "上架中资产 + 已拥有标记；过期自动下架")
    @SentinelResource("WISH_WORKSHOP_LIST")
    public ApiResponse<List<Map<String, Object>>> workshopAssets(
            @Parameter(description = "当前用户 ID（网关注入，可空=未登录浏览）")
            @RequestHeader(value = SecurityConstants.USER_ID_HEADER, required = false) Long userId) {
        return ApiResponse.ok(collectionService.workshopAssets(userId));
    }

    @PostMapping("/workshop/exchange")
    @Operation(summary = "星光兑换", description = "幂等（重复兑换 409 已拥有）；限量 Redis DECR 预扣；"
            + "余额不足 402；RMB 通道偏差留档")
    @SentinelResource("WISH_WORKSHOP_EXCHANGE")
    public ApiResponse<UserAsset> exchange(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @jakarta.validation.Valid @RequestBody ExchangeAssetRequest request) {
        String paymentMethod = request.paymentMethod() == null ? "STARLIGHT" : request.paymentMethod();
        return ApiResponse.ok(collectionService.exchange(userId, request.assetId(), paymentMethod));
    }

    @PostMapping("/collections/spark/{wishId}")
    @Operation(summary = "收藏星火心愿", description = "SPARK 心愿 → 收藏馆 SPECIAL_FRUIT 资产（幂等）")
    public ApiResponse<UserAsset> collectSpark(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "心愿 ID", required = true) @PathVariable Long wishId) {
        return ApiResponse.ok(collectionService.collectSpark(userId, wishId));
    }

    @PutMapping("/my/active-skin/{assetId}")
    @Operation(summary = "切换皮肤", description = "同类型互斥，即时生效")
    public ApiResponse<Void> setActiveSkin(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "资产 ID", required = true) @PathVariable Long assetId) {
        collectionService.setActiveAsset(userId, assetId);
        return ApiResponse.ok(null);
    }

    @PutMapping("/my/active-bgm/{assetId}")
    @Operation(summary = "切换 BGM", description = "同类型互斥，即时生效")
    public ApiResponse<Void> setActiveBgm(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "资产 ID", required = true) @PathVariable Long assetId) {
        collectionService.setActiveAsset(userId, assetId);
        return ApiResponse.ok(null);
    }

    // ---------------- 品牌许愿池 ----------------

    @GetMapping("/brands")
    @Operation(summary = "品牌列表", description = "APPROVED 品牌公开浏览")
    public ApiResponse<Object> listBrands() {
        return ApiResponse.ok(collectionService.listBrands());
    }

    @GetMapping("/brands/{brandId}/pools")
    @Operation(summary = "品牌许愿池列表", description = "ACTIVE 池公开浏览")
    public ApiResponse<Object> listPools(
            @Parameter(description = "品牌 ID", required = true) @PathVariable Long brandId) {
        return ApiResponse.ok(collectionService.listPools(brandId));
    }

    @PostMapping("/brands/{brandId}/pools/{poolId}/join")
    @Operation(summary = "加入许愿池", description = "uk 池×用户 防重复")
    public ApiResponse<Void> joinPool(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "品牌 ID", required = true) @PathVariable Long brandId,
            @Parameter(description = "许愿池 ID", required = true) @PathVariable Long poolId) {
        collectionService.joinPool(userId, poolId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/brands/{brandId}/pools/{poolId}")
    @Operation(summary = "许愿池详情", description = "池信息 + 当前进度")
    public ApiResponse<Object> getPoolDetail(
            @Parameter(description = "品牌 ID", required = true) @PathVariable Long brandId,
            @Parameter(description = "许愿池 ID", required = true) @PathVariable Long poolId) {
        return ApiResponse.ok(collectionService.getPoolDetail(poolId));
    }

    @GetMapping("/brands/{brandId}/pools/{poolId}/rewards")
    @Operation(summary = "许愿池奖励记录", description = "品牌奖励发放记录（脱敏，仅返回匿名用户 ID + 数量）")
    public ApiResponse<List<Map<String, Object>>> poolRewards(
            @Parameter(description = "品牌 ID", required = true) @PathVariable Long brandId,
            @Parameter(description = "许愿池 ID", required = true) @PathVariable Long poolId) {
        return ApiResponse.ok(collectionService.poolRewards(poolId));
    }

    @GetMapping("/brands/{brandId}/audit-logs")
    @Operation(summary = "品牌数据访问审计日志", description = "数据共享脱敏：仅返回匿名化操作记录")
    public ApiResponse<List<Map<String, Object>>> brandAuditLogs(
            @Parameter(description = "品牌 ID", required = true) @PathVariable Long brandId) {
        return ApiResponse.ok(collectionService.brandAuditLogs(brandId));
    }
}
