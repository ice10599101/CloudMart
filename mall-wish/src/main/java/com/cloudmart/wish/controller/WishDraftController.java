package com.cloudmart.wish.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.wish.dto.CreateWishRequest;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.entity.WishDraft;
import com.cloudmart.wish.service.WishDraftService;
import com.cloudmart.wish.service.impl.WishDraftServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 心愿草稿（N03，/api/wish/v2/**）：自动保存、断网恢复、发布幂等。
 */
@RestController
@RequestMapping("/v2/drafts")
@Tag(name = "心愿宇宙·草稿", description = "草稿与发布（N03）")
@RequiredArgsConstructor
public class WishDraftController {

    private final WishDraftService wishDraftService;

    @PostMapping
    @Operation(summary = "保存草稿", description = "clientDraftId 幂等；version 乐观锁；每人最多 20 份；"
            + "草稿只本人可见、不进公共 feed、不发奖励")
    public ApiResponse<WishDraft> save(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @RequestBody WishDraftServiceImpl.SaveDraftRequest request) {
        return ApiResponse.ok(wishDraftService.saveDraft(userId, request.clientDraftId(), request));
    }

    @GetMapping("/my")
    @Operation(summary = "我的草稿列表", description = "仅本人；不进公共 feed")
    public ApiResponse<List<WishDraft>> my(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        return ApiResponse.ok(wishDraftService.listMyDrafts(userId, cursor, pageSize));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "编辑草稿", description = "clientDraftId 定位或路径 ID；乐观锁自动保存")
    public ApiResponse<WishDraft> patch(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("id") Long draftId,
            @RequestBody WishDraftServiceImpl.SaveDraftRequest request) {
        WishDraft existing = wishDraftService.requireOwnedDraft(userId, draftId);
        return ApiResponse.ok(wishDraftService.saveDraft(userId, existing.getClientDraftId(), request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除草稿", description = "软删；version CAS；未引用附件由文件清理任务释放")
    public ApiResponse<Void> delete(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("id") Long draftId,
            @RequestParam(required = false) Long version) {
        wishDraftService.deleteDraft(userId, draftId, version);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "发布草稿", description = "复用发布领域命令（B04 幂等/B10 统计/事件）；"
            + "同草稿仅首次发布，重复调用返回既有心愿")
    public ApiResponse<Wish> publish(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("id") Long draftId,
            @RequestBody CreateWishRequest request) {
        return ApiResponse.ok(wishDraftService.publishDraft(userId, draftId, request));
    }
}
