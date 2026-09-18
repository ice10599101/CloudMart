package com.cloudmart.community.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.community.dto.CreatePollRequest;
import com.cloudmart.community.dto.PollVoteRequest;
import com.cloudmart.community.service.PollService;
import com.cloudmart.community.vo.PollVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 投票 Controller（编辑器附件，V10）。
 *
 * <p>创建/投票需登录；详情对匿名开放（帖子公开可读，正文中的投票同样可看）。
 * 创建幂等：主键为客户端 UUID，重复提交返回既有投票。</p>
 */
@RestController
@RequestMapping("/polls")
@Tag(name = "投票", description = "编辑器投票附件：创建/详情/投票")
@RequiredArgsConstructor
public class PollController {

    private final PollService pollService;

    @PostMapping
    @Operation(summary = "创建投票", description = "发布含投票的正文时由前端调用；主键为客户端 UUID，幂等落库")
    public ApiResponse<PollVO> createPoll(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @RequestBody CreatePollRequest request) {
        return ApiResponse.ok(pollService.createPoll(userId, request));
    }

    @GetMapping("/{pollId}")
    @Operation(summary = "投票详情", description = "含各选项票数与参与人数；已登录时返回我的选择")
    public ApiResponse<PollVO> getPoll(
            @Parameter(description = "投票 ID", required = true) @PathVariable("pollId") String pollId,
            @Parameter(description = "当前用户 ID（网关注入，匿名查看时缺失）")
            @RequestHeader(value = SecurityConstants.USER_ID_HEADER, required = false) Long userId) {
        return ApiResponse.ok(pollService.getPoll(pollId, userId));
    }

    @PostMapping("/{pollId}/vote")
    @Operation(summary = "提交投票", description = "单选恰好 1 项，多选 1-10 项；重复投票返回 409")
    public ApiResponse<Void> vote(
            @Parameter(description = "投票 ID", required = true) @PathVariable("pollId") String pollId,
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @RequestBody PollVoteRequest request) {
        pollService.vote(pollId, userId, request.optionIds());
        return ApiResponse.ok(null);
    }
}
