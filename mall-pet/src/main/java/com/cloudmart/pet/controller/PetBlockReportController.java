package com.cloudmart.pet.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.pet.service.PetUserBlockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 * 屏蔽与举报（B14）：用户级屏蔽名单 + 内容举报（管理员处理走 /admin/pet/reports）。
 */
@RestController
@RequestMapping
@Tag(name = "宠物屏蔽与举报", description = "屏蔽名单管理、内容举报提交")
@RequiredArgsConstructor
public class PetBlockReportController {

    private final PetUserBlockService blockService;
    private final com.cloudmart.pet.service.impl.PetReportSubmissionService reportSubmissionService;

    @PostMapping("/blocks/{blockedUserId}")
    @Operation(summary = "屏蔽用户", description = "幂等；屏蔽后双方不能新增拜访收益/挑战/留言/申请")
    @SentinelResource("PET_SOCIAL_UPDATE")
    public ApiResponse<Void> block(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "被屏蔽用户 ID") @PathVariable("blockedUserId") Long blockedUserId) {
        blockService.block(userId, blockedUserId);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/blocks/{blockedUserId}")
    @Operation(summary = "取消屏蔽", description = "幂等")
    @SentinelResource("PET_SOCIAL_UPDATE")
    public ApiResponse<Void> unblock(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "被屏蔽用户 ID") @PathVariable("blockedUserId") Long blockedUserId) {
        blockService.unblock(userId, blockedUserId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/blocks")
    @Operation(summary = "我的屏蔽名单")
    @SentinelResource("PET_QUERY")
    public ApiResponse<List<Long>> blocks(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(blockService.blockedUserIds(userId));
    }

    /** 举报请求体：description 为可选补充说明（1~1000 字符，§7.2） */
    public record ReportRequest(String targetType, Long targetId, String reason, String description) {
    }

    @PostMapping("/reports")
    @Operation(summary = "提交举报", description = "targetType: WALL_MESSAGE/BOTTLE_CONTENT/NICKNAME；"
            + "同用户同对象未结案幂等返回既有举报（deduped=true），每日提交有配额")
    @SentinelResource("PET_SOCIAL_UPDATE")
    public ApiResponse<com.cloudmart.pet.service.impl.PetReportSubmissionService.ReportSummary> report(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @RequestBody ReportRequest request) {
        return ApiResponse.ok(reportSubmissionService.create(
                userId, request.targetType(), request.targetId(), request.reason(), request.description()));
    }

    @GetMapping("/reports/mine")
    @Operation(summary = "我的举报（R05）", description = "本人举报状态与公开处置摘要、提交时间；"
            + "不含被举报者敏感资料与内部审核备注")
    @SentinelResource("PET_QUERY")
    public ApiResponse<List<com.cloudmart.pet.service.impl.PetReportSubmissionService.ReportMineVO>> myReports(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(reportSubmissionService.listMine(userId));
    }
}
