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
    private final com.cloudmart.pet.repository.PetReportMapper reportMapper;

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

    /** 举报请求体 */
    public record ReportRequest(String targetType, Long targetId, String reason) {
    }

    @PostMapping("/reports")
    @Operation(summary = "提交举报", description = "targetType: WALL_MESSAGE/BOTTLE_CONTENT/NICKNAME；进入管理员处理队列")
    @SentinelResource("PET_SOCIAL_UPDATE")
    public ApiResponse<Void> report(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @RequestBody ReportRequest request) {
        java.util.Set<String> allowed = java.util.Set.of("WALL_MESSAGE", "BOTTLE_CONTENT", "NICKNAME");
        if (request.targetType() == null || !allowed.contains(request.targetType().toUpperCase())
                || request.targetId() == null
                || request.reason() == null || request.reason().isBlank()
                || request.reason().length() > 200) {
            throw new com.cloudmart.common.exception.BusinessException(
                    com.cloudmart.pet.constant.PetErrorCodes.PET_VALIDATION_ERROR, "举报参数非法");
        }
        com.cloudmart.pet.entity.PetReport report = new com.cloudmart.pet.entity.PetReport();
        report.setReporterUserId(userId);
        report.setTargetType(request.targetType().toUpperCase());
        report.setTargetId(request.targetId());
        report.setReason(request.reason().strip());
        report.setStatus("PENDING");
        reportMapper.insert(report);
        return ApiResponse.ok(null);
    }
}
