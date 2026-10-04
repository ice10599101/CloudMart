package com.cloudmart.pet.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.pet.entity.PetAlbumAsset;
import com.cloudmart.pet.entity.PetMemory;
import com.cloudmart.pet.entity.PetNotifyPref;
import com.cloudmart.pet.service.impl.PetCompanionFeatureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

    @GetMapping("/onboarding")
    @Operation(summary = "新手引导进度（N01）", description = "查询当前步骤/全部步骤/是否可跳过；完成由领域事件驱动，不能客户端提交")
    public ApiResponse<Map<String, Object>> onboarding(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(featureService.onboarding(userId));
    }

    @PostMapping("/onboarding/skip")
    @Operation(summary = "跳过引导（N01）", description = "幂等；跳过不伪造步骤与奖励，不影响正常养成")
    public ApiResponse<Void> skipOnboarding(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        featureService.skipOnboarding(userId);
        return ApiResponse.ok(null);
    }

    // ---------------- N02 ----------------

    @GetMapping("/pets/{petId}/diary")
    @Operation(summary = "成长日记时间线（N02）", description = "游标分页 nextCursor/hasMore；他人仅见 PUBLIC 条目")
    public ApiResponse<Map<String, Object>> diary(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "宠物 ID") @PathVariable("petId") Long petId,
            @Parameter(description = "游标（上一页末条 ID）") @RequestParam(value = "cursor", required = false) String cursor,
            @Parameter(description = "页大小") @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(featureService.diary(userId, petId, cursor, size));
    }

    public record DiaryVisibilityRequest(String visibility, Integer expectedVersion) {
    }

    @PatchMapping("/pets/{petId}/diary/{entryId}")
    @Operation(summary = "编辑日记可见性（§7.2）", description = "统一可见性入口：visibility 取 PUBLIC/OWNER_ONLY"
            + "（旧 PRIVATE 值兼容映射）；expectedVersion 必填 CAS，冲突返回 PET_STATE_CONFLICT")
    public ApiResponse<Map<String, Object>> updateDiaryVisibility(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("petId") Long petId,
            @PathVariable("entryId") Long entryId,
            @RequestBody DiaryVisibilityRequest request) {
        return ApiResponse.ok(featureService.updateDiaryVisibility(userId, petId, entryId,
                request.visibility(), request.expectedVersion()));
    }

    public record AlbumUploadRequest(String fileId, Long diaryEntryId, String caption) {
    }

    @PostMapping("/pets/{petId}/album")
    @Operation(summary = "上传相册资源（N02/R04）", description = "fileId 为 mall-file PRIVATE 资产 ID（数字串，不是 URL）；"
            + "远程校验归属/类型/大小（JPEG/PNG/WebP ≤5MiB）后登记幂等引用；每用户 100 张；失败留 BINDING 可重试；"
            + "默认私有，caption 可选（≤200 字）")
    public ApiResponse<PetAlbumAsset> uploadAlbum(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("petId") Long petId,
            @RequestBody AlbumUploadRequest request) {
        return ApiResponse.ok(featureService.uploadAlbumAsset(userId, petId,
                request.fileId(), request.diaryEntryId(), request.caption()));
    }

    @GetMapping("/pets/{petId}/album")
    @Operation(summary = "相册列表（R04）", description = "本人返回全部条目（含审核状态与驳回理由），"
            + "访客仅见 APPROVED+BOUND+PUBLIC；预览为 60 秒短期授权地址，禁止持久化")
    public ApiResponse<List<com.cloudmart.pet.service.impl.PetCompanionFeatureService.AlbumAssetVO>> albumList(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("petId") Long petId) {
        return ApiResponse.ok(featureService.albumList(userId, petId));
    }

    public record AlbumUpdateRequest(String caption, String visibility, Integer expectedVersion) {
    }

    @PatchMapping("/pets/{petId}/album/{assetId}")
    @Operation(summary = "编辑相册资源（§7.2）", description = "caption/visibility 部分更新（缺省不改）；"
            + "expectedVersion 必填 CAS 防多端覆盖；审核通过前 PUBLIC 被拒绝")
    public ApiResponse<PetAlbumAsset> updateAlbum(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("petId") Long petId,
            @PathVariable("assetId") Long assetId,
            @RequestBody AlbumUpdateRequest request) {
        return ApiResponse.ok(featureService.updateAlbumAsset(userId, petId, assetId,
                request.caption(), request.visibility(), request.expectedVersion()));
    }

    @PostMapping("/pets/{petId}/album/{assetId}/retry-binding")
    @Operation(summary = "重试文件绑定（R04）", description = "BINDING 条目的恢复入口（上传响应丢失/远程失败）；非 BINDING 返回原状态")
    public ApiResponse<PetAlbumAsset> retryAlbumBinding(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("petId") Long petId,
            @PathVariable("assetId") Long assetId) {
        return ApiResponse.ok(featureService.retryAlbumBinding(userId, assetId));
    }

    @DeleteMapping("/pets/{petId}/album/{assetId}")
    @Operation(summary = "删除相册资源（N02/R04）", description = "归属校验；本地行立即删除并尽力解绑远程文件引用")
    public ApiResponse<Void> deleteAlbum(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("petId") Long petId,
            @PathVariable("assetId") Long assetId) {
        featureService.deleteAlbumAsset(userId, assetId);
        return ApiResponse.ok(null);
    }

    // ---------------- N03 ----------------

    @GetMapping("/pets/{petId}/memories")
    @Operation(summary = "宠物记忆列表（N03）", description = "按宠物隔离；仅主人可访问；不进公开接口/排行/分享")
    public ApiResponse<List<PetMemory>> memories(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("petId") Long petId) {
        return ApiResponse.ok(featureService.memories(userId, petId));
    }

    public record MemoryEditRequest(String value) {
    }

    @PutMapping("/pets/{petId}/memories/{memoryId}")
    @Operation(summary = "编辑记忆（N03）", description = "用户编辑（USER 来源）优先于自动抽取，同长度/否定语句均可覆盖")
    public ApiResponse<PetMemory> editMemory(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("petId") Long petId, @PathVariable("memoryId") Long memoryId,
            @RequestBody MemoryEditRequest request) {
        return ApiResponse.ok(featureService.editMemory(userId, petId, memoryId, request.value()));
    }

    @DeleteMapping("/pets/{petId}/memories/{memoryId}")
    @Operation(summary = "删除记忆（N03）", description = "删除标记防复活；下次上下文不再注入")
    public ApiResponse<Void> deleteMemory(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("petId") Long petId, @PathVariable("memoryId") Long memoryId) {
        featureService.deleteMemory(userId, petId, memoryId);
        return ApiResponse.ok(null);
    }

    public record MemoryToggleRequest(boolean extract, boolean use) {
    }

    @GetMapping("/pets/{petId}/memory-settings")
    @Operation(summary = "记忆设置读取（R03/T11）", description = "三端先 GET 再编辑；不得以默认值覆盖服务端已关闭设置")
    public ApiResponse<Map<String, Object>> memorySettings(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("petId") Long petId) {
        return ApiResponse.ok(featureService.memorySettings(userId, petId));
    }

    @PutMapping("/pets/{petId}/memory-settings")
    @Operation(summary = "记忆开关（N03）", description = "自动提取与注入使用独立控制；返回持久化后的值")
    public ApiResponse<Map<String, Object>> toggleMemory(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("petId") Long petId, @RequestBody MemoryToggleRequest request) {
        return ApiResponse.ok(featureService.toggleMemory(userId, petId, request.extract(), request.use()));
    }

    @GetMapping("/notify-settings")
    @Operation(summary = "通知偏好查询（B19）", description = "免打扰/日常问候开关")
    public ApiResponse<PetNotifyPref> notifyPrefs(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(featureService.notifyPrefs(userId));
    }

    public record NotifyPrefUpdate(boolean muteDailyGreeting, boolean dailyGreetingEnabled) {
    }

    @PutMapping("/notify-settings")
    @Operation(summary = "更新通知偏好（B19）", description = "仅作用于日常问候类 proactive；重要业务通知不受影响")
    public ApiResponse<PetNotifyPref> updateNotifyPrefs(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @RequestBody NotifyPrefUpdate request) {
        return ApiResponse.ok(featureService.updateNotifyPrefs(userId, request.muteDailyGreeting(), request.dailyGreetingEnabled()));
    }

    @DeleteMapping("/pets/{petId}/memories")
    @Operation(summary = "批量清空记忆（N03）", description = "全部软删（可恢复标记防复活）")
    public ApiResponse<Void> clearMemories(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("petId") Long petId) {
        featureService.clearMemories(userId, petId);
        return ApiResponse.ok(null);
    }
}
