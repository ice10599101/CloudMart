package com.cloudmart.wish.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.annotation.Idempotent;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.wish.dto.AiConversationListQuery;
import com.cloudmart.wish.dto.TreeHoleMessageRequest;
import com.cloudmart.wish.service.TreeHoleService;
import com.cloudmart.wish.vo.AiConversationVO;
import com.cloudmart.wish.vo.TreeHoleReplyVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 心愿 AI Controller（文档 2.11 节，Sprint 1.3）。
 *
 * <p>错误码：403 WISH_CONSENT_REQUIRED（未同意 AI 数据处理协议）/
 * 403 WISH_NOT_AUTHOR（非树洞作者）/ 400 WISH_VALIDATION_ERROR（心愿未启用 AI 回复）/
 * 404 WISH_NOT_FOUND / 429 WISH_AI_RATE_LIMITED（10 次/日）/
 * 503 WISH_AI_UNAVAILABLE（AI 服务不可用）。</p>
 */
@RestController
@RequestMapping("/ai")
@Tag(name = "心愿 AI", description = "树洞治愈回复与 AI 对话历史")
@RequiredArgsConstructor
public class WishAiController {

    private final TreeHoleService treeHoleService;

    @PostMapping("/tree-hole")
    @Operation(summary = "树洞治愈回复", description = "向树洞心愿倾诉并获取 AI 治愈回复；"
            + "使用前须同意 AI 数据处理协议（POST /my/consents）；单用户 10 次/日；"
            + "危机内容本地拦截并返回心理援助热线。重复提交请携带 X-Idempotency-Key 请求头")
    @SentinelResource("WISH_AI_TREE_HOLE")
    @Idempotent(prefix = "wish-tree-hole", ttl = 10)
    public ApiResponse<TreeHoleReplyVO> sendTreeHoleMessage(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "树洞消息请求") @Valid @RequestBody TreeHoleMessageRequest request) {
        TreeHoleReplyVO vo = treeHoleService.sendTreeHoleMessage(userId, request);
        return ApiResponse.ok(vo);
    }

    @GetMapping("/conversations")
    @Operation(summary = "AI 对话历史", description = "当前用户的 AI 对话记录，cursor 分页（id 倒序），"
            + "可按场景筛选（默认 TREE_HOLE）")
    @SentinelResource("WISH_AI_CONVERSATIONS")
    public ApiResponse<List<AiConversationVO>> listConversations(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "查询参数") AiConversationListQuery query) {
        TreeHoleService.ConversationPage page = treeHoleService.listConversations(userId, query);
        return ApiResponse.okWithCursor(page.records(), query.safePageSize(),
                page.nextCursor(), page.hasMore());
    }
}
