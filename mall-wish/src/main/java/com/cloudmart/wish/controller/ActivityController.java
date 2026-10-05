package com.cloudmart.wish.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.wish.dto.ApplyPartnerRequest;
import com.cloudmart.wish.entity.ActivityParticipant;
import com.cloudmart.wish.entity.CommunityActivity;
import com.cloudmart.wish.service.ActivityService;
import com.cloudmart.wish.vo.ActivityBoardVO;
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
 * 社区活动 Controller（Sprint 3.5，文档 2.21/3.5：GET /wish/activities/**）。
 *
 * <p>列表/详情/进度公开浏览；参与/申请/看板需登录。</p>
 */
@RestController
@RequestMapping("/activities")
@Tag(name = "社区活动", description = "活动列表/详情/进度 + 心愿合伙人协作（Sprint 3.5）")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    @GetMapping
    @Operation(summary = "活动列表（入口）", description = "仅 ACTIVE 且展示期内；type 四类过滤；cityCode 城市过滤；"
            + "归档活动不出现在列表（验收）")
    @SentinelResource("WISH_ACTIVITY_LIST")
    public ApiResponse<List<CommunityActivity>> listActivities(
            @Parameter(description = "类型过滤：WORLD_EVENT/FESTIVAL/CITY/WISH_PARTNER")
            @RequestParam(required = false) String type,
            @Parameter(description = "城市代理过滤（geohash4）") @RequestParam(required = false) String cityCode) {
        return ApiResponse.ok(activityService.listActivities(type, cityCode));
    }

    @GetMapping("/{id}")
    @Operation(summary = "活动详情", description = "归档后详情页仍可访问（验收）")
    @SentinelResource("WISH_ACTIVITY_DETAIL")
    public ApiResponse<CommunityActivity> getActivity(
            @Parameter(description = "活动 ID", required = true) @PathVariable Long id) {
        return ApiResponse.ok(activityService.getActivity(id));
    }

    @GetMapping("/{id}/progress")
    @Operation(summary = "活动进度", description = "Redis 原子计数（1000 并发参与准确）")
    @SentinelResource("WISH_ACTIVITY_PROGRESS")
    public ApiResponse<Long> getProgress(
            @Parameter(description = "活动 ID", required = true) @PathVariable Long id) {
        return ApiResponse.ok(activityService.getProgress(id));
    }

    /**
     * T22：参与者列表（受控查询）——JOINED/APPROVED 成员的最小信息，昵称脱敏为
     * ID 尾号；分页限制服务端上限。对应 Web 已有封装 /wish/activities/{id}/participants。
     */
    @GetMapping("/{id}/participants")
    @Operation(summary = "活动参与者列表", description = "T22：最小成员信息（昵称脱敏）；分页")
    @SentinelResource("WISH_ACTIVITY_PARTICIPANTS")
    public ApiResponse<java.util.List<java.util.Map<String, Object>>> participants(
            @Parameter(description = "活动 ID", required = true) @PathVariable Long id,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(activityService.listParticipants(id, page, Math.min(size, 100)));
    }

    @PostMapping("/{id}/join")
    @Operation(summary = "参与活动", description = "普通活动参与（进度 Redis INCR；重复参与幂等不重复计数）")
    @SentinelResource("WISH_ACTIVITY_JOIN")
    public ApiResponse<Void> join(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "活动 ID", required = true) @PathVariable Long id) {
        activityService.join(userId, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/apply")
    @Operation(summary = "合伙人申请", description = "提交协作心愿 + 技能标签；服务端计算与招募需求的技能匹配度；"
            + "申请后等待招募作者审批")
    @SentinelResource("WISH_ACTIVITY_APPLY")
    public ApiResponse<Void> applyPartner(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "活动 ID", required = true) @PathVariable Long id,
            @Valid @RequestBody ApplyPartnerRequest request) {
        activityService.applyPartner(userId, id, request.wishId(), request.skills());
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}/board")
    @Operation(summary = "组队看板", description = "合伙人协作进度共享：成员各自心愿进度/打卡天数/"
            + "最新成长记录互相可见；仅组内成员可查看")
    @SentinelResource("WISH_ACTIVITY_BOARD")
    public ApiResponse<ActivityBoardVO> getBoard(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "活动 ID", required = true) @PathVariable Long id) {
        List<ActivityBoardVO.MemberBoard> members = activityService.getPartnerBoard(id, userId);
        return ApiResponse.ok(new ActivityBoardVO(id, activityService.getActivity(id).getCreatedBy(), members));
    }
}
