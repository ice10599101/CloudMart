package com.cloudmart.community.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.api.ApiResponse.Meta;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.community.service.GrowthService;
import com.cloudmart.community.vo.CheckInResultVO;
import com.cloudmart.community.vo.ExpLogVO;
import com.cloudmart.community.vo.LevelConfigVO;
import com.cloudmart.community.vo.UserLevelVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/growth")
@Tag(name = "成长体系", description = "用户签到、等级、经验值接口")
@RequiredArgsConstructor
public class GrowthController {

    private final GrowthService growthService;

    @PostMapping("/check-in")
    @Operation(summary = "每日签到", description = "用户每日签到获取经验值，连续签到有额外奖励")
    public ApiResponse<CheckInResultVO> checkIn(
            @Parameter(description = "当前用户ID") @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        CheckInResultVO vo = growthService.checkIn(userId);
        return ApiResponse.ok(vo);
    }

    @GetMapping("/check-in/status")
    @Operation(summary = "签到状态", description = "查询今日是否已签到")
    public ApiResponse<Boolean> isCheckedInToday(
            @Parameter(description = "当前用户ID") @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        boolean checkedIn = growthService.isCheckedInToday(userId);
        return ApiResponse.ok(checkedIn);
    }

    @GetMapping("/check-in/calendar")
    @Operation(summary = "签到日历", description = "获取指定月份的签到日历记录")
    public ApiResponse<List<java.time.LocalDate>> getCheckInCalendar(
            @Parameter(description = "当前用户ID") @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "年份") @RequestParam int year,
            @Parameter(description = "月份") @RequestParam int month) {
        List<java.time.LocalDate> calendar = growthService.getCheckInCalendar(userId, year, month);
        return ApiResponse.ok(calendar);
    }

    @GetMapping("/check-in/continuous")
    @Operation(summary = "连续签到天数", description = "获取当前连续签到天数")
    public ApiResponse<Integer> getContinuousDays(
            @Parameter(description = "当前用户ID") @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        int days = growthService.getContinuousDays(userId);
        return ApiResponse.ok(days);
    }

    @GetMapping("/level")
    @Operation(summary = "用户等级", description = "获取当前用户等级信息及升级进度")
    public ApiResponse<UserLevelVO> getUserLevel(
            @Parameter(description = "当前用户ID") @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        UserLevelVO vo = growthService.getUserLevel(userId);
        return ApiResponse.ok(vo);
    }

    @GetMapping("/exp-logs")
    @Operation(summary = "经验值记录", description = "分页查询用户经验值变动记录")
    public ApiResponse<List<ExpLogVO>> getExpLogs(
            @Parameter(description = "当前用户ID") @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size) {
        Page<ExpLogVO> result = growthService.getExpLogs(userId, page, size);
        return ApiResponse.ok(result.getRecords(), new Meta(page, size, result.getTotal()));
    }

    @GetMapping("/level-configs")
    @Operation(summary = "等级配置列表", description = "获取所有启用的等级配置（公开接口）")
    public ApiResponse<List<LevelConfigVO>> getLevelConfigs() {
        List<LevelConfigVO> configs = growthService.getLevelConfigs();
        return ApiResponse.ok(configs);
    }
}
