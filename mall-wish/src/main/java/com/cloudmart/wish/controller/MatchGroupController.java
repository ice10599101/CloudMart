package com.cloudmart.wish.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.wish.dto.CreateMatchGroupRequest;
import com.cloudmart.wish.dto.JoinGroupRequest;
import com.cloudmart.wish.dto.MatchRecommendQuery;
import com.cloudmart.wish.service.MatchGroupService;
import com.cloudmart.wish.vo.MatchGroupCreatedVO;
import com.cloudmart.wish.vo.MatchGroupDetailVO;
import com.cloudmart.wish.vo.MatchGroupVO;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 同愿匹配 + 监督小队 Controller（Sprint 2.6，文档 2.8/十章）。
 *
 * <p>路由前缀 /match（网关 /wish/match/**）：建组/推荐/加入/退出/踢人/
 * 解散/互相提醒/我的小队/小组详情。推荐为公开浏览（permitAll，匿名
 * 降级为纯参数匹配）；其余接口需登录（网关注入用户头）。</p>
 */
@RestController
@RequestMapping("/match")
@Tag(name = "同愿匹配 + 监督小队", description = "匹配推荐 + 2-4 人打卡小队 + 互相提醒（Sprint 2.6）")
@RequiredArgsConstructor
public class MatchGroupController {

    private final MatchGroupService matchGroupService;

    @PostMapping("/groups")
    @Operation(summary = "建组", description = "创建 2-4 人同愿小队，创建者为 LEADER；"
            + "一人同主题（keyword）仅一个进行中的小队；每用户每日建组数受限频约束；"
            + "被移出同主题小队 24h 冷却期内不可建组/加入。"
            + "errors: 400 WISH_VALIDATION_ERROR / 403 WISH_KICKED_COOLDOWN / "
            + "409 WISH_GROUP_KEYWORD_DUPLICATED / 429 WISH_RATE_LIMITED")
    @SentinelResource("WISH_MATCH_GROUP_CREATE")
    public ApiResponse<MatchGroupCreatedVO> createGroup(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @RequestBody CreateMatchGroupRequest request) {
        return ApiResponse.ok(matchGroupService.createGroup(userId, request));
    }

    @GetMapping("/groups/recommend")
    @Operation(summary = "匹配推荐", description = "OPEN 小队按相似度降序（关键词/城市/活跃度加权，权重管理端可配）；"
            + "排除本人已加入的小队；keyword/city 皆空时基于用户心愿标签推荐（冷启动按活跃度兜底）。"
            + "matchReason 为三端一致的相似度说明（如'你们都想看极光'）。公开浏览：未登录可按参数匹配")
    @SentinelResource("WISH_MATCH_RECOMMEND")
    public ApiResponse<List<MatchGroupVO>> recommendGroups(
            @Parameter(description = "当前用户 ID（网关注入，匿名浏览可空）")
            @RequestHeader(value = SecurityConstants.USER_ID_HEADER, required = false) Long userId,
            @Parameter(description = "关键词（可选）") @RequestParam(required = false) String keyword,
            @Parameter(description = "同城代理码（可选，geohash 前缀）") @RequestParam(required = false) String city,
            @Parameter(description = "游标") @RequestParam(required = false) String cursor,
            @Parameter(description = "页大小") @RequestParam(required = false) Integer pageSize) {
        MatchRecommendQuery query = new MatchRecommendQuery(keyword, city, cursor, pageSize);
        MatchGroupVO.MatchPage page = matchGroupService.recommendGroups(userId, query);
        return ApiResponse.okWithCursor(page.records(), query.safePageSize(), page.nextCursor(), page.hasMore());
    }

    @GetMapping("/groups/my")
    @Operation(summary = "我的小队", description = "当前用户 ACTIVE 成员身份的小队（含成员列表与活跃度），"
            + "CLOSED 小队不展示")
    @SentinelResource("WISH_MATCH_GROUP_MY")
    public ApiResponse<List<MatchGroupDetailVO>> listMyGroups(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(matchGroupService.listMyGroups(userId));
    }

    @GetMapping("/groups/{id}")
    @Operation(summary = "小组详情", description = "成员仅暴露昵称/头像/活跃度（不泄露手机号/邮箱）；"
            + "viewerRole 为当前查看者角色（非成员为 null）")
    @SentinelResource("WISH_MATCH_GROUP_DETAIL")
    public ApiResponse<MatchGroupDetailVO> getGroupDetail(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "小组 ID", required = true) @PathVariable Long id) {
        return ApiResponse.ok(matchGroupService.getGroupDetail(userId, id));
    }

    @PostMapping("/groups/{id}/members")
    @Operation(summary = "加入小队", description = "CAS 占位防并发超卖：满员前最后 1 个名额并发加入仅 1 人成功。"
            + "errors: 403 WISH_KICKED_COOLDOWN / 404 WISH_GROUP_NOT_FOUND / "
            + "409 WISH_GROUP_FULL / WISH_ALREADY_MEMBER / WISH_GROUP_KEYWORD_DUPLICATED")
    @SentinelResource("WISH_MATCH_GROUP_JOIN")
    public ApiResponse<Void> joinGroup(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "小组 ID", required = true) @PathVariable Long id,
            @Valid @RequestBody(required = false) JoinGroupRequest request) {
        matchGroupService.joinGroup(userId, id, request);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/groups/{id}/members/{targetUserId}")
    @Operation(summary = "退出/踢出", description = "targetUserId=自己为退出；他人须 LEADER 权限（踢出）。"
            + "组长退出自动转让给最早加入的 MEMBER，无成员则组关闭；被踢者 24h 同主题冷却并收到通知。"
            + "errors: 403 WISH_FORBIDDEN / WISH_GROUP_LEADER_REQUIRED / 404 WISH_GROUP_NOT_FOUND")
    @SentinelResource("WISH_MATCH_GROUP_LEAVE")
    public ApiResponse<Void> leaveOrKickMember(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "小组 ID", required = true) @PathVariable Long id,
            @Parameter(description = "目标成员用户 ID", required = true) @PathVariable Long targetUserId) {
        matchGroupService.leaveOrKickMember(userId, id, targetUserId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/groups/{id}/dissolution")
    @Operation(summary = "解散小队", description = "仅组长可解散；全部成员收到通知，成员关系置 LEFT 保留历史")
    @SentinelResource("WISH_MATCH_GROUP_DISSOLVE")
    public ApiResponse<Void> dissolveGroup(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "小组 ID", required = true) @PathVariable Long id) {
        matchGroupService.dissolveGroup(userId, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/groups/{id}/reminds")
    @Operation(summary = "互相提醒", description = "提醒未打卡组员：指定 targetUserId 点名提醒，"
            + "否则提醒全部 idle 超过 remind_idle_days（默认 3 天）的组员；"
            + "发送者每日提醒条数受限频（429 WISH_RATE_LIMITED）")
    @SentinelResource("WISH_MATCH_GROUP_REMIND")
    public ApiResponse<Void> remindMembers(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "小组 ID", required = true) @PathVariable Long id,
            @Parameter(description = "目标组员（可选，空=全部 idle 组员）") @RequestParam(required = false) Long targetUserId) {
        matchGroupService.remindMembers(userId, id, targetUserId);
        return ApiResponse.ok(null);
    }
}
