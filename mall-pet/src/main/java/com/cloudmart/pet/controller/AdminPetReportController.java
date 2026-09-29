package com.cloudmart.pet.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetReport;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.repository.PetReportMapper;
import com.cloudmart.pet.service.PetAchievementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 管理端举报处理与成就补算（B14/B17/B21）：列表/处理审计/补算；仅内部链路可达。
 */
@RestController
@RequestMapping("/admin/pet")
@Tag(name = "宠物管理·举报与成就", description = "举报列表与处理、成就补算（管理员审计）")
@RequiredArgsConstructor
@PreAuthorize("hasRole('INTERNAL')")
public class AdminPetReportController {

    private final PetReportMapper reportMapper;
    private final PetMapper petMapper;
    private final PetAchievementService achievementService;
    private final com.cloudmart.pet.service.impl.PetCompanionFeatureService companionFeatureService;
    private final com.cloudmart.pet.service.impl.PetReportResolutionService resolutionService;

    @GetMapping("/reports")
    @Operation(summary = "举报列表", description = "status 过滤 + 分页")
    public ApiResponse<List<PetReport>> list(
            @Parameter(description = "状态过滤") @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        LambdaQueryWrapper<PetReport> wrapper = new LambdaQueryWrapper<PetReport>()
                .orderByDesc(PetReport::getId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(PetReport::getStatus, status.toUpperCase());
        }
        Page<PetReport> result = reportMapper.selectPage(new Page<>(Math.max(page, 1), Math.min(size, 50)), wrapper);
        return ApiResponse.ok(result.getRecords(), result.getCurrent(),
                result.getSize(), result.getTotal());
    }

    @PutMapping("/reports/{id}/handle")
    @Operation(summary = "处理举报", description = "action=HANDLED/REJECTED；处理人取 mall-admin 代理透传的可信操作者头（SEC-02，不接受客户端自填）")
    public ApiResponse<Void> handle(
            @Parameter(description = "举报 ID") @PathVariable("id") Long id,
            @RequestParam("action") String action,
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId) {
        String normalized = action != null ? action.toUpperCase() : "";
        if (!"HANDLED".equals(normalized) && !"REJECTED".equals(normalized)) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "处理动作非法");
        }
        int updated = reportMapper.update(null, new LambdaUpdateWrapper<PetReport>()
                .set(PetReport::getStatus, normalized)
                .set(PetReport::getHandledBy, adminUserId)
                .set(PetReport::getHandledAt, LocalDateTime.now(ZoneOffset.UTC))
                .eq(PetReport::getId, id)
                .eq(PetReport::getStatus, "PENDING"));
        if (updated == 0) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "举报不存在或已处理");
        }
        return ApiResponse.ok(null);
    }

    /** 举报闭环处理请求（P0-2） */
    public record ResolveReportRequest(String action, String reason) {
    }

    @PostMapping("/reports/{id}/resolve")
    @Operation(summary = "闭环处理举报", description = "action: CONTENT_REMOVED/USER_WARNED/USER_PET_BANNED/DISMISSED + reason 必填；" +
            "处理后经 outbox 通知举报人，CONTENT_REMOVED 联动隐藏留言墙内容")
    public ApiResponse<Void> resolve(
            @Parameter(description = "举报 ID") @PathVariable("id") Long id,
            @org.springframework.web.bind.annotation.RequestBody ResolveReportRequest request,
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId) {
        resolutionService.resolve(id, request.action(), request.reason(), adminUserId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/album/{assetId}/approve")
    @Operation(summary = "相册资源审核通过", description = "BE-11：仅审核链路可设 APPROVED（用户上传进入时为 PENDING）")
    public ApiResponse<com.cloudmart.pet.entity.PetAlbumAsset> approveAlbum(
            @Parameter(description = "相册资源 ID") @PathVariable("assetId") Long assetId) {
        return ApiResponse.ok(companionFeatureService.approveAlbumAsset(assetId));
    }

    /** 成就补算（B17）：从历史事实重评全部事件；已达成记录唯一键幂等，不重复发奖 */
    @PostMapping("/achievements/recalculate")
    @Operation(summary = "成就补算", description = "按宠物扫描历史事实补漏成就；重复执行结果不变")
    public ApiResponse<Integer> recalculate(@RequestParam("petId") Long petId) {
        Pet pet = petMapper.selectById(petId);
        if (pet == null) {
            throw new BusinessException(PetErrorCodes.PET_NOT_FOUND, "宠物不存在");
        }
        int triggered = 0;
        for (PetAchievementService.Event event : PetAchievementService.Event.values()) {
            try {
                achievementService.evaluate(pet, event);
                triggered++;
            } catch (Exception e) {
                // 单事件失败不阻断补算批次
            }
        }
        return ApiResponse.ok(triggered);
    }
}
