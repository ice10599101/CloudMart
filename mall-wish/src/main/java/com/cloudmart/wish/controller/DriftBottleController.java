package com.cloudmart.wish.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.wish.dto.BottleCommentRequest;
import com.cloudmart.wish.dto.DriftBottleInteractRequest;
import com.cloudmart.wish.dto.PickerAnonymityRequest;
import com.cloudmart.wish.dto.ThrowBottleRequest;
import com.cloudmart.wish.service.DriftBottleService;
import com.cloudmart.wish.vo.DriftBottleCandidateWishVO;
import com.cloudmart.wish.vo.DriftBottleCommentVO;
import com.cloudmart.wish.vo.DriftBottleQuotaVO;
import com.cloudmart.wish.vo.DriftBottleVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

/**
 * 漂流瓶 Controller（替代附近模式相遇信笺的用户侧体验）。
 *
 * <p>路由：POST /drift-bottles（投瓶）、POST /drift-bottles/fish（捞瓶）、
 * GET /drift-bottles/mine（我的漂流瓶）、GET /drift-bottles/quota（每日配额）、
 * POST /drift-bottles/{id}/return（扔回海里）、POST /drift-bottles/{id}/collect（收藏）、
 * PUT /drift-bottles/{id}/picker-anonymity（捞瓶人匿名开关）、
 * POST /drift-bottles/{id}/interactions（匿名回应）。全部需登录。</p>
 */
@RestController
@RequestMapping("/drift-bottles")
@Tag(name = "漂流瓶", description = "匿名随机漂流：投瓶 / 捞瓶 / 扔回海里 / 收藏 / 我的漂流瓶 / 匿名回应")
@RequiredArgsConstructor
public class DriftBottleController {

    private final DriftBottleService driftBottleService;

    @GetMapping("/quota")
    @Operation(summary = "每日配额", description = "今日已投瓶/已打捞次数与上限（投瓶 10 个/天、打捞 20 次/天，UTC 自然日），页面计数展示用")
    @SentinelResource("WISH_DRIFT_QUOTA")
    public ApiResponse<DriftBottleQuotaVO> getQuota(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(driftBottleService.getQuota(userId));
    }

    @PostMapping
    @Operation(summary = "投瓶", description = "自由匿名富文本或关联一个公开进行中心愿（content 与 wishId 二选一）；"
            + "投出后进入全局海面池；每日上限 10 个（429）")
    @SentinelResource("WISH_DRIFT_THROW")
    public ApiResponse<DriftBottleVO> throwBottle(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @RequestBody ThrowBottleRequest request) {
        return ApiResponse.ok(driftBottleService.throwBottle(userId, request));
    }

    @GetMapping("/candidate-wishes")
    @Operation(summary = "可关联心愿候选", description = "我最近发布的至多 30 个可关联心愿（公开进行中），按发布时间倒序；投瓶下拉选择用")
    @SentinelResource("WISH_DRIFT_CANDIDATE")
    public ApiResponse<List<DriftBottleCandidateWishVO>> listCandidateWishes(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(driftBottleService.listCandidateWishes(userId));
    }

    @PostMapping("/fish")
    @Operation(summary = "捞瓶", description = "随机捞取一个非自己的漂浮漂流瓶（含被扔回海里的）；海里无瓶时 data 返回 null；"
            + "每日上限 20 次（429）；重新捞起时捞起人匿名默认开启")
    @SentinelResource("WISH_DRIFT_FISH")
    public ApiResponse<DriftBottleVO> fishBottle(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(driftBottleService.fishBottle(userId));
    }

    @GetMapping("/mine")
    @Operation(summary = "我的漂流瓶", description = "我投出的（THROWN）+ 我捞到的（PICKED），按时间倒序；含投瓶/捞瓶时间")
    @SentinelResource("WISH_DRIFT_MINE")
    public ApiResponse<List<DriftBottleVO>> listMine(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(driftBottleService.listMine(userId));
    }

    @GetMapping("/collected")
    @Operation(summary = "我收藏的漂流瓶", description = "我捞起并收藏的漂流瓶（倒序）；个人页收藏面板数据源")
    @SentinelResource("WISH_DRIFT_COLLECTED")
    public ApiResponse<List<DriftBottleVO>> listCollected(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(driftBottleService.listCollected(userId));
    }

    @PostMapping("/{id}/return")
    @Operation(summary = "扔回海里", description = "仅捞起人可操作（PICKED → RETURNED）：瓶子回到海面可再被捞起，"
            + "捞起人与收藏清空；评论保留")
    @SentinelResource("WISH_DRIFT_RETURN")
    public ApiResponse<Void> returnBottle(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "漂流瓶 ID", required = true) @PathVariable Long id) {
        driftBottleService.returnBottle(userId, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/collect")
    @Operation(summary = "收藏漂流瓶", description = "仅捞起人可收藏（仅 PICKED 状态）；幂等")
    @SentinelResource("WISH_DRIFT_COLLECT")
    public ApiResponse<DriftBottleVO> collectBottle(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "漂流瓶 ID", required = true) @PathVariable Long id) {
        return ApiResponse.ok(driftBottleService.collectBottle(userId, id));
    }

    @PutMapping("/{id}/picker-anonymity")
    @Operation(summary = "捞瓶人匿名开关", description = "仅捞起人可设置（仅 PICKED 状态）；"
            + "默认匿名（true 隐藏捞瓶人身份 / false 实名，投瓶人可见捞瓶人身份）")
    @SentinelResource("WISH_DRIFT_PICKER_ANONYMITY")
    public ApiResponse<DriftBottleVO> updatePickerAnonymity(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "漂流瓶 ID", required = true) @PathVariable Long id,
            @Valid @RequestBody PickerAnonymityRequest request) {
        return ApiResponse.ok(driftBottleService.updatePickerAnonymity(userId, id, request.isAnonymous()));
    }

    @PostMapping("/{id}/interactions")
    @Operation(summary = "匿名回应", description = "仅捞起人可回应关联心愿的漂流瓶：BLESS 匿名祝福（免费）/ "
            + "LIGHT 点亮对方心愿（扣星光 2）；单瓶每日 1 次（429）；投瓶人收到匿名通知")
    @SentinelResource("WISH_DRIFT_INTERACT")
    public ApiResponse<DriftBottleVO> interact(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "漂流瓶 ID", required = true) @PathVariable Long id,
            @Valid @RequestBody DriftBottleInteractRequest request) {
        return ApiResponse.ok(driftBottleService.interact(userId, id, request.type()));
    }

    @GetMapping("/{id}/comments")
    @Operation(summary = "漂流瓶评论列表", description = "仅投瓶人或捞起人可见；id 倒序游标分页；"
            + "匿名评论不返回真实身份（昵称显示「匿名瓶友」）")
    @SentinelResource("WISH_DRIFT_COMMENT_LIST")
    public ApiResponse<List<DriftBottleCommentVO>> listComments(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "漂流瓶 ID", required = true) @PathVariable Long id,
            @Parameter(description = "分页游标（上一页最后一条评论 ID）") @RequestParam(required = false) String cursor,
            @Parameter(description = "每页数量（1-50，默认 20）") @RequestParam(required = false) Integer pageSize) {
        DriftBottleService.CommentPage page =
                driftBottleService.listComments(userId, id, cursor, pageSize);
        int safeSize = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 50);
        return ApiResponse.okWithCursor(page.records(), safeSize, page.nextCursor(), page.hasMore());
    }

    @PostMapping("/{id}/comments")
    @Operation(summary = "发表漂流瓶评论/回复", description = "仅投瓶人或捞起人可评论；"
            + "默认匿名（不选默认匿名），可切换实名；parentId 指向同一漂流瓶下的评论即回复")
    @SentinelResource("WISH_DRIFT_COMMENT_ADD")
    public ApiResponse<DriftBottleCommentVO> addComment(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "漂流瓶 ID", required = true) @PathVariable Long id,
            @Valid @RequestBody BottleCommentRequest request) {
        return ApiResponse.ok(driftBottleService.addComment(userId, id, request));
    }
}
