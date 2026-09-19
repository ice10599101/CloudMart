package com.cloudmart.pet.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.pet.service.PetAchievementService;
import com.cloudmart.pet.service.PetReminderService;
import com.cloudmart.pet.vo.PetAchievementVO;
import com.cloudmart.pet.vo.PetReminderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 宠物提醒与成就接口。提醒复用现有通知系统（mall-notification notifications 表，
 * type=PET，WebSocket 同步推送）；成就为事件驱动幂等发奖。
 */
@RestController
@RequestMapping
@Tag(name = "宠物提醒与成就", description = "宠物口吻提醒列表、成就墙")
@RequiredArgsConstructor
public class PetReminderController {

    private final PetReminderService reminderService;
    private final PetAchievementService achievementService;

    @GetMapping("/reminders")
    @Operation(summary = "宠物提醒列表", description = "复用通知系统 type=PET 最近 20 条（子类型：PET_BOTTLE_CAUGHT 等）；"
            + "通知已读走现有 /notification 接口")
    @SentinelResource("PET_QUERY")
    public ApiResponse<List<PetReminderVO>> reminders(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(reminderService.listReminders(userId));
    }

    @GetMapping("/reminders/unread-count")
    @Operation(summary = "宠物提醒未读数", description = "type=PET 未读提醒数量，用于宠物入口角标")
    @SentinelResource("PET_QUERY")
    public ApiResponse<Long> unreadCount(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(reminderService.unreadCount(userId));
    }

    @GetMapping("/achievements")
    @Operation(summary = "成就墙", description = "全部启用成就 + 达成状态（未达成灰显）")
    @SentinelResource("PET_QUERY")
    public ApiResponse<List<PetAchievementVO>> achievements(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(achievementService.listMy(userId));
    }
}
