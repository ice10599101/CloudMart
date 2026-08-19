package com.cloudmart.wish.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.annotation.Idempotent;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.wish.dto.CreateInteractionRequest;
import com.cloudmart.wish.dto.InteractionListQuery;
import com.cloudmart.wish.service.InteractionService;
import com.cloudmart.wish.vo.InteractionItemVO;
import com.cloudmart.wish.vo.InteractionResultVO;
import com.cloudmart.wish.vo.InteractionRevokeVO;
import com.cloudmart.wish.vo.MyInteractionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 心愿互动 Controller（文档 2.2 节，Sprint 1.2）。
 *
 * <p>错误码：400 WISH_INTERACTION_TYPE_INVALID / 402 WISH_STARLIGHT_INSUFFICIENT /
 * 409 WISH_ALREADY_INTERACTED / 429 WISH_RATE_LIMITED / 404 WISH_NOT_FOUND /
 * 403 WISH_FORBIDDEN（取消他人互动）。</p>
 */
@RestController
@RequestMapping("/wishes/{wishId}/interactions")
@Tag(name = "心愿互动", description = "点亮/同求/祝福互动与取消、互动列表")
@RequiredArgsConstructor
public class InteractionController {

    private final InteractionService interactionService;

    @PostMapping
    @Operation(summary = "发起互动", description = "点亮（扣 2 星光，可重复）/同求（每愿望唯一）/"
            + "祝福（每愿望每日 1 次）；匿名星光 Sprint 2.6 启用。"
            + "重复提交请携带 X-Idempotency-Key 请求头")
    @SentinelResource("WISH_INTERACTION_CREATE")
    @Idempotent(prefix = "wish-interaction", ttl = 10)
    public ApiResponse<InteractionResultVO> createInteraction(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "心愿 ID", required = true) @PathVariable Long wishId,
            @Parameter(description = "互动请求") @Valid @RequestBody CreateInteractionRequest request) {
        InteractionResultVO vo = interactionService.createInteraction(userId, wishId, request);
        return ApiResponse.ok(vo);
    }

    @DeleteMapping("/{interactionId}")
    @Operation(summary = "取消互动", description = "按互动记录 ID 取消（仅可取消自己的互动）；"
            + "计数回滚，已消耗/已发放星光不退还；取消同求后可重新同求")
    @SentinelResource("WISH_INTERACTION_REVOKE")
    public ApiResponse<InteractionRevokeVO> revokeInteraction(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "心愿 ID", required = true) @PathVariable Long wishId,
            @Parameter(description = "互动记录 ID", required = true) @PathVariable Long interactionId) {
        InteractionRevokeVO vo = interactionService.revokeInteraction(userId, wishId, interactionId);
        return ApiResponse.ok(vo);
    }

    @GetMapping
    @Operation(summary = "互动列表", description = "cursor 分页，时间倒序，可按类型筛选；"
            + "PRIVATE/TREE_HOLE 心愿仅作者可查看")
    @SentinelResource("WISH_INTERACTION_LIST")
    public ApiResponse<List<InteractionItemVO>> listInteractions(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long viewerId,
            @Parameter(description = "心愿 ID", required = true) @PathVariable Long wishId,
            @Parameter(description = "查询参数") InteractionListQuery query) {
        InteractionService.InteractionPage page =
                interactionService.listInteractions(wishId, viewerId, query);
        return ApiResponse.okWithCursor(page.records(), query.safePageSize(),
                page.nextCursor(), page.hasMore());
    }

    @GetMapping("/my")
    @Operation(summary = "我的互动状态", description = "当前用户在该心愿的全部未删互动记录；"
            + "SAME_WISH 存在即已同求，BLESS 存在 createdToday=true 即今日已祝福；"
            + "返回的 id 用于取消互动")
    @SentinelResource("WISH_INTERACTION_MY")
    public ApiResponse<List<MyInteractionVO>> listMyInteractions(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "心愿 ID", required = true) @PathVariable Long wishId) {
        return ApiResponse.ok(interactionService.listMyInteractions(userId, wishId));
    }
}
