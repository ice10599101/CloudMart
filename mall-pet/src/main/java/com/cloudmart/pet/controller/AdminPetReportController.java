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

    // R05：旧 PUT /reports/{id}/handle 旁路已停用——该端点绕过处罚事实与通知闭环，
    // 直接终结举报状态。统一走 POST /reports/{id}/resolve（含处罚矩阵与审计）。

    /** 举报闭环处理请求（P0-2/R05：durationSeconds 限时处罚时长，可空=需人工解除） */
    public record ResolveReportRequest(String action, String reason, Integer durationSeconds) {
    }

    @PostMapping("/reports/{id}/resolve")
    @Operation(summary = "闭环处理举报", description = "action: CONTENT_REMOVED/USER_WARNED/USER_PET_BANNED/DISMISSED + reason 必填；" +
            "处理后经 outbox 通知举报人，CONTENT_REMOVED 联动隐藏留言墙内容")
    public ApiResponse<Void> resolve(
            @Parameter(description = "举报 ID") @PathVariable("id") Long id,
            @org.springframework.web.bind.annotation.RequestBody ResolveReportRequest request,
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId) {
        resolutionService.resolve(id, request.action(), request.reason(), adminUserId,
                request.durationSeconds());
        return ApiResponse.ok(null);
    }

    // ---------------- R05 处罚事实 ----------------

    private final com.cloudmart.pet.service.impl.PetAccessPolicy accessPolicy;

    @GetMapping("/sanctions")
    @Operation(summary = "处罚列表（R05）", description = "userId/status/scope 筛选；封禁范围、期限、理由与来源举报可追溯")
    public ApiResponse<List<com.cloudmart.pet.entity.PetUserSanction>> sanctions(
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "scope", required = false) String scope,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(accessPolicy.list(userId, status, scope, page, size));
    }

    public record RevokeSanctionRequest(@jakarta.validation.constraints.NotBlank String reason) {
    }

    @PostMapping("/sanctions/{id}/revoke")
    @Operation(summary = "撤销处罚（R05）", description = "理由必填留痕；撤销保留历史不物理删除；立即恢复对应写入能力")
    public ApiResponse<Void> revokeSanction(
            @PathVariable("id") Long id,
            @org.springframework.web.bind.annotation.RequestBody RevokeSanctionRequest request,
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId) {
        accessPolicy.revoke(id, adminUserId, request.reason());
        return ApiResponse.ok(null);
    }

    /** R04 审核请求：驳回理由必填（留痕） */
    public record AlbumReviewRequest(String reason) {
    }

    @PostMapping("/album/{assetId}/approve")
    @Operation(summary = "相册资源审核通过", description = "R04：仅 BOUND+PENDING 可通过；处理人取认证上下文；"
            + "已处理/已删除对象不能被旧请求复活")
    public ApiResponse<com.cloudmart.pet.entity.PetAlbumAsset> approveAlbum(
            @Parameter(description = "相册资源 ID") @PathVariable("assetId") Long assetId,
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId) {
        return ApiResponse.ok(companionFeatureService.approveAlbumAsset(assetId, adminUserId));
    }

    @PostMapping("/album/{assetId}/reject")
    @Operation(summary = "相册资源审核驳回（R04）", description = "理由必填留痕；被驳回条目保留在相册且不可公开")
    public ApiResponse<com.cloudmart.pet.entity.PetAlbumAsset> rejectAlbum(
            @Parameter(description = "相册资源 ID") @PathVariable("assetId") Long assetId,
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId,
            @org.springframework.web.bind.annotation.RequestBody AlbumReviewRequest request) {
        return ApiResponse.ok(companionFeatureService.rejectAlbumAsset(assetId, adminUserId, request.reason()));
    }

    @GetMapping("/album/reviews")
    @Operation(summary = "相册审核队列（R04）", description = "PENDING 且 BOUND 的条目，按时间正序；auditStatus 可选过滤")
    public ApiResponse<List<com.cloudmart.pet.entity.PetAlbumAsset>> albumReviewQueue(
            @Parameter(description = "审核状态过滤（默认 PENDING）") @RequestParam(value = "auditStatus", required = false) String auditStatus) {
        String status = auditStatus == null || auditStatus.isBlank() ? "PENDING" : auditStatus;
        return ApiResponse.ok(companionFeatureService.albumReviewQueue(status));
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
