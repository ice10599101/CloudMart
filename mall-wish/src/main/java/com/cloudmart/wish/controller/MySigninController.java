package com.cloudmart.wish.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.wish.service.DailySigninService;
import com.cloudmart.wish.vo.DailySigninVO;
import com.cloudmart.wish.vo.SigninCalendarVO;
import com.cloudmart.wish.vo.SigninMilestoneClaimVO;
import com.cloudmart.wish.vo.SigninMilestoneVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户每日签到 Controller（文档 2.6）。
 *
 * <p>与心愿打卡（{@code POST /wish/wishes/{id}/checkin}，心愿维度）独立：
 * 本接口为用户维度每日签到（+5 星光），签到响应携带等级提升事件，
 * 供三端庆祝弹窗（文档 L1912）与 APP 本地推送（L1917）消费。</p>
 */
@RestController
@RequestMapping("/my/checkin")
@Tag(name = "每日签到", description = "用户维度每日签到 + 签到日历（个人数据，需登录）")
@RequiredArgsConstructor
@Validated
public class MySigninController {

    private final DailySigninService dailySigninService;

    @PostMapping
    @Operation(summary = "每日签到", description = "每日一次（uk_signin_daily 幂等，用户时区按日去重）；"
            + "发放星光 +5（SIGNIN 流水）；响应含 levelUp 等级提升事件（未提升为 null）。"
            + "errors: 409 WISH_ALREADY_SIGNED_IN")
    @SentinelResource("WISH_MY_SIGNIN")
    public ApiResponse<DailySigninVO> signin(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(dailySigninService.signin(userId));
    }

    @GetMapping("/calendar")
    @Operation(summary = "签到日历", description = "指定月份已签到日期 + 截至当前连续签到天数"
            + " + 历史累计签到天数。errors: 400 WISH_VALIDATION_ERROR")
    @SentinelResource("WISH_MY_SIGNIN_CALENDAR")
    public ApiResponse<SigninCalendarVO> getCalendar(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "月份（yyyy-MM，如 2026-09）", required = true)
            @RequestParam @Pattern(regexp = "\\d{4}-\\d{2}", message = "month 格式须为 yyyy-MM") String month) {
        return ApiResponse.ok(dailySigninService.getCalendar(userId, month));
    }

    @GetMapping("/milestones")
    @Operation(summary = "连续签到里程碑列表", description = "返回固定里程碑（7/14/30 天）各自的"
            + "星光/经验奖励 + 领取状态（是否已领取、当前是否可领取）。")
    @SentinelResource("WISH_MY_SIGNIN_MILESTONES")
    public ApiResponse<List<SigninMilestoneVO>> listMilestones(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(dailySigninService.listMilestones(userId));
    }

    @PostMapping("/milestones/{days}/claim")
    @Operation(summary = "领取连续签到里程碑奖励", description = "连续签到满对应天数后手动领取一次星光+经验。"
            + "errors: 400 WISH_MILESTONE_INVALID / 409 WISH_MILESTONE_NOT_REACHED"
            + " / 409 WISH_MILESTONE_ALREADY_CLAIMED")
    @SentinelResource("WISH_MY_SIGNIN_MILESTONE_CLAIM")
    public ApiResponse<SigninMilestoneClaimVO> claimMilestone(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "里程碑连续签到天数（7/14/30）", required = true)
            @PathVariable int days) {
        return ApiResponse.ok(dailySigninService.claimMilestone(userId, days));
    }
}
