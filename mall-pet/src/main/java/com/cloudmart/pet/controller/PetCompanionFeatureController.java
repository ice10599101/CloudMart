package com.cloudmart.pet.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.pet.entity.PetAlbumAsset;
import com.cloudmart.pet.entity.PetMemory;
import com.cloudmart.pet.service.impl.PetCompanionFeatureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 陪伴功能接口（N01 新手引导 / N02 成长日记与相册 / N03 记忆管理）。
 */
@RestController
@Tag(name = "宠物陪伴功能", description = "新手引导、成长日记与相册、记忆管理")
@RequiredArgsConstructor
public class PetCompanionFeatureController {

    private final PetCompanionFeatureService featureService;

    // ---------------- N01 ----------------

    @GetMapping("/pet/onboarding")
    @Operation(summary = "新手引导进度（N01）", description = "查询当前步骤/全部步骤/是否可跳过；完成由领域事件驱动，不能客户端提交")
    public ApiResponse<Map<String, Object>> onboarding(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(featureService.onboarding(userId));
    }

    @PostMapping("/pet/onboarding/skip")
    @Operation(summary = "跳过引导（N01）", description = "幂等；跳过不伪造步骤与奖励，不影响正常养成")
    public ApiResponse<Void> skipOnboarding(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        featureService.skipOnboarding(userId);
        return ApiResponse.ok(null);
    }

    // ---------------- N02 ----------------

    @GetMapping("/pet/pets/{petId}/diary")
    @Operation(summary = "成长日记时间线（N02）", description = "游标分页 nextCursor/hasMore；他人仅见 PUBLIC 条目")
    public ApiResponse<Map<String, Object>> diary(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "宠物 ID") @PathVariable("petId") Long petId,
            @Parameter(description = "游标（上一页末条 ID）") @RequestParam(value = "cursor", required = false) String cursor,
            @Parameter(description = "页大小") @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(featureService.diary(userId, petId, cursor, size));
    }

    public record AlbumUploadRequest(String fileId, Long diaryEntryId) {
    }

    @PostMapping("/pet/pets/{petId}/album")
    @Operation(summary = "上传相册资源（N02）", description = "fileId 为 mall-file 授权引用（JPEG/PNG/WebP ≤5MB，由文件服务校验）；每用户 100 张")
    public ApiResponse<PetAlbumAsset> uploadAlbum(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("petId") Long petId,
            @RequestBody AlbumUploadRequest request) {
        return ApiResponse.ok(featureService.uploadAlbumAsset(userId, petId, request.fileId(), request.diaryEntryId()));
    }

    @DeleteMapping("/pet/pets/{petId}/album/{assetId}")
    @Operation(summary = "删除相册资源（N02）", description = "归属校验；撤销关联公开访问")
    public ApiResponse<Void> deleteAlbum(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("petId") Long petId,
            @PathVariable("assetId") Long assetId) {
        featureService.deleteAlbumAsset(userId, assetId);
        return ApiResponse.ok(null);
    }

    // ---------------- N03 ----------------

    @GetMapping("/pet/pets/{petId}/memories")
    @Operation(summary = "宠物记忆列表（N03）", description = "按宠物隔离；仅主人可访问；不进公开接口/排行/分享")
    public ApiResponse<List<PetMemory>> memories(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("petId") Long petId) {
        return ApiResponse.ok(featureService.memories(userId, petId));
    }

    public record MemoryEditRequest(String value) {
    }

    @PutMapping("/pet/pets/{petId}/memories/{memoryId}")
    @Operation(summary = "编辑记忆（N03）", description = "用户编辑（USER 来源）优先于自动抽取，同长度/否定语句均可覆盖")
    public ApiResponse<PetMemory> editMemory(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("petId") Long petId, @PathVariable("memoryId") Long memoryId,
            @RequestBody MemoryEditRequest request) {
        return ApiResponse.ok(featureService.editMemory(userId, petId, memoryId, request.value()));
    }

    @DeleteMapping("/pet/pets/{petId}/memories/{memoryId}")
    @Operation(summary = "删除记忆（N03）", description = "删除标记防复活；下次上下文不再注入")
    public ApiResponse<Void> deleteMemory(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("petId") Long petId, @PathVariable("memoryId") Long memoryId) {
        featureService.deleteMemory(userId, petId, memoryId);
        return ApiResponse.ok(null);
    }

    public record MemoryToggleRequest(boolean extract, boolean use) {
    }

    @PutMapping("/pet/pets/{petId}/memory-settings")
    @Operation(summary = "记忆开关（N03）", description = "自动提取与注入使用独立控制")
    public ApiResponse<Void> toggleMemory(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("petId") Long petId, @RequestBody MemoryToggleRequest request) {
        featureService.toggleMemory(userId, petId, request.extract(), request.use());
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/pet/pets/{petId}/memories")
    @Operation(summary = "批量清空记忆（N03）", description = "全部软删（可恢复标记防复活）")
    public ApiResponse<Void> clearMemories(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("petId") Long petId) {
        featureService.clearMemories(userId, petId);
        return ApiResponse.ok(null);
    }
}
