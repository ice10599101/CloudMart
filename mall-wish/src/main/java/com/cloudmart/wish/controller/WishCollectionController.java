package com.cloudmart.wish.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.wish.entity.WishCollection;
import com.cloudmart.wish.service.WishCollectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 心愿收藏 Controller（Sprint 1.5/3.6 补齐，文档 2.12）。
 */
@RestController
@RequestMapping("/collections")
@Tag(name = "心愿收藏", description = "收藏/取消收藏/收藏列表（文档 2.12）")
@RequiredArgsConstructor
public class WishCollectionController {

    private final WishCollectionService wishCollectionService;

    @GetMapping
    @Operation(summary = "收藏列表", description = "当前用户已收藏的心愿列表（含心愿标题/类型）")
    @SentinelResource("WISH_COLLECTION_LIST")
    public ApiResponse<List<WishCollectionService.WishCollectionItemVO>> listCollections(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return ApiResponse.ok(wishCollectionService.listCollections(userId, cursor, pageSize));
    }

    @PostMapping
    @Operation(summary = "收藏心愿", description = "收藏他人心愿到个人收藏列表（不能收藏自己的心愿）；"
            + "uk(user,wish) 幂等（重复收藏 400）")
    @SentinelResource("WISH_COLLECTION_ADD")
    public ApiResponse<WishCollection> collect(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @RequestBody Map<String, Long> body) {
        Long wishId = body.get("wishId");
        return ApiResponse.ok(wishCollectionService.collect(userId, wishId));
    }

    @DeleteMapping("/{wishId}")
    @Operation(summary = "取消收藏", description = "软删除（历史保留审计）")
    public ApiResponse<Void> uncollect(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "心愿 ID", required = true) @PathVariable Long wishId) {
        wishCollectionService.uncollect(userId, wishId);
        return ApiResponse.ok(null);
    }
}
