package com.cloudmart.pet.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.pet.service.PetEventService;
import com.cloudmart.pet.vo.PetEventVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 社区宠物活动接口（原文档 §89 社区宠物活动：常驻/限时活动 + 惰性进度统计 + 幂等领奖）。
 */
@RestController
@RequestMapping
@Tag(name = "社区宠物活动", description = "活动列表、领取活动奖励")
@RequiredArgsConstructor
public class PetEventController {

    private final PetEventService eventService;

    @GetMapping("/events")
    @Operation(summary = "活动列表", description = "进度由既有业务表惰性统计（捞瓶流水/胜场/行为留痕），不设计数器；"
            + "status 可选 AVAILABLE/CLAIMABLE/HISTORY；期次驱动活动返回 occurrenceId/claimDeadlineAt")
    @SentinelResource("PET_QUERY")
    public ApiResponse<List<PetEventVO>> events(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "状态过滤 AVAILABLE/CLAIMABLE/HISTORY（缺省全部）")
            @RequestParam(value = "status", required = false) String status) {
        return ApiResponse.ok(eventService.events(userId, status));
    }

    @PostMapping("/event-occurrences/{occurrenceId}/claim")
    @Operation(summary = "按期次领取活动奖励（R33）", description = "唯一领奖事实 uk(petId, occurrenceId)；"
            + "进度按期次窗口统计；超过 claimDeadline 拒绝；本期未完成 409")
    @SentinelResource("PET_EVENT")
    public ApiResponse<PetEventVO> claimByOccurrence(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("occurrenceId") Long occurrenceId) {
        return ApiResponse.ok(eventService.claimByOccurrence(userId, occurrenceId));
    }

    @PostMapping("/events/{eventCode}/claim")
    @Operation(summary = "领取活动奖励", description = "未完成 409 PET_EVENT_NOT_FINISHED；已领 409 PET_EVENT_ALREADY_CLAIMED；"
            + "活动已结束 409 PET_EVENT_ENDED；奖励含经验/星光/物品")
    @SentinelResource("PET_EVENT")
    public ApiResponse<PetEventVO> claim(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("eventCode") String eventCode) {
        return ApiResponse.ok(eventService.claim(userId, eventCode));
    }
}
