package com.cloudmart.admin.feign;

import com.cloudmart.admin.dto.feign.AdminCommentSearchRequest;
import com.cloudmart.admin.dto.feign.AdminInteractionSearchRequest;
import com.cloudmart.admin.dto.feign.AdminWishSearchRequest;
import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * mall-wish 管理端 Feign 客户端。
 *
 * <p>下游端点由 @PreAuthorize("hasRole('INTERNAL')") 保护，仅接受
 * AdminFeignInterceptor 注入 X-Internal-Call 头的内部调用。</p>
 */
@FeignClient(contextId = "wishFeignClient", name = "mall-wish", path = "/admin", fallbackFactory = WishFeignClientFallbackFactory.class)
public interface WishFeignClient {

    @GetMapping("/wishes")
    ApiResponse<Object> listWishes(@SpringQueryMap AdminWishSearchRequest request);

    @GetMapping("/wishes/stats")
    ApiResponse<Object> getWishStats();

    @GetMapping("/wishes/{id}")
    ApiResponse<Object> getWish(@PathVariable("id") Long id);

    @PutMapping("/wishes/{id}/audit")
    ApiResponse<Object> auditWish(@PathVariable("id") Long id, @RequestBody Map<String, Object> data);

    @PutMapping("/wishes/{id}/visibility")
    ApiResponse<Object> updateWishVisibility(@PathVariable("id") Long id, @RequestBody Map<String, Object> data);

    @PutMapping("/wishes/{id}/top")
    ApiResponse<Object> updateWishTop(@PathVariable("id") Long id, @RequestBody Map<String, Object> data);

    @DeleteMapping("/wishes/{id}")
    ApiResponse<Void> deleteWish(@PathVariable("id") Long id);

    @GetMapping("/categories")
    ApiResponse<Object> listCategories();

    @PostMapping("/categories")
    ApiResponse<Object> createCategory(@RequestBody Map<String, Object> data);

    @PutMapping("/categories/{id}")
    ApiResponse<Object> updateCategory(@PathVariable("id") Long id, @RequestBody Map<String, Object> data);

    @DeleteMapping("/categories/{id}")
    ApiResponse<Void> deleteCategory(@PathVariable("id") Long id);

    // ========== 互动记录审计（Sprint 1.2） ==========

    @GetMapping("/interactions")
    ApiResponse<Object> listInteractions(@SpringQueryMap AdminInteractionSearchRequest request);

    // ========== 评论审核（Sprint 1.2） ==========

    @GetMapping("/comments")
    ApiResponse<Object> listComments(@SpringQueryMap AdminCommentSearchRequest request);

    @PutMapping("/comments/{id}/status")
    ApiResponse<Object> updateCommentStatus(@PathVariable("id") Long id,
                                            @RequestBody Map<String, Object> data);

    // ========== 徽章管理（Sprint 1.8） ==========

    @GetMapping("/badges")
    ApiResponse<Object> listBadges();

    @PostMapping("/badges")
    ApiResponse<Object> createBadge(@RequestBody Map<String, Object> data);

    @PutMapping("/badges/{id}")
    ApiResponse<Object> updateBadge(@PathVariable("id") Long id, @RequestBody Map<String, Object> data);

    @PutMapping("/badges/{id}/status")
    ApiResponse<Object> updateBadgeStatus(@PathVariable("id") Long id,
                                          @RequestBody Map<String, Object> data);

    // ========== 背景音乐曲库管理（Sprint 2.3） ==========

    @GetMapping("/bgm")
    ApiResponse<Object> listBgmSongs();

    @PostMapping("/bgm")
    ApiResponse<Object> createBgmSong(@RequestBody Map<String, Object> data);

    @PutMapping("/bgm/{id}")
    ApiResponse<Object> updateBgmSong(@PathVariable("id") Long id,
                                      @RequestBody Map<String, Object> data);

    @PutMapping("/bgm/{id}/status")
    ApiResponse<Object> updateBgmSongStatus(@PathVariable("id") Long id,
                                            @RequestBody Map<String, Object> data);

    @DeleteMapping("/bgm/{id}")
    ApiResponse<Void> deleteBgmSong(@PathVariable("id") Long id);

    // ========== AI 心愿助手管理（Sprint 2.5） ==========

    @GetMapping("/ai/prompts")
    ApiResponse<Object> listAiPrompts(@RequestParam(value = "scene", required = false) String scene);

    @PostMapping("/ai/prompts")
    ApiResponse<Object> createAiPrompt(@RequestBody Map<String, Object> data);

    @PutMapping("/ai/prompts/{id}/status")
    ApiResponse<Object> updateAiPromptStatus(@PathVariable("id") Long id,
                                             @RequestBody Map<String, Object> data);

    @GetMapping("/ai/configs")
    ApiResponse<Object> listAiConfigs();

    @PutMapping("/ai/configs/{key}")
    ApiResponse<Object> updateAiConfig(@PathVariable("key") String configKey,
                                       @RequestBody Map<String, Object> data);

    // ---- 同愿匹配（Sprint 2.6）----

    @GetMapping("/match/groups")
    ApiResponse<Object> listMatchGroups(@RequestParam("status") String status,
                                        @RequestParam("keyword") String keyword);

    @PostMapping("/match/groups/{id}/dissolution")
    ApiResponse<Object> forceDissolveMatchGroup(@PathVariable("id") Long groupId);

    @GetMapping("/match/configs")
    ApiResponse<Object> listMatchConfigs();

    @PutMapping("/match/configs/{key}")
    ApiResponse<Object> updateMatchConfig(@PathVariable("key") String configKey,
                                          @RequestBody Map<String, Object> data);

    // ---- 传承 + 排行榜（Sprint 2.7）----

    @GetMapping("/legacy/flows")
    ApiResponse<Object> listContentFlowLogs(@RequestParam("status") String status,
                                            @RequestParam("page") Integer page,
                                            @RequestParam("size") Integer size);

    @PostMapping("/legacy/flows/{id}/retry")
    ApiResponse<Object> retryContentFlow(@PathVariable("id") Long logId);

    @GetMapping("/legacy/stats")
    ApiResponse<Object> legacyStats();

    @GetMapping("/leaderboard/configs")
    ApiResponse<Object> listLeaderboardConfigs();

    @PutMapping("/leaderboard/configs/{key}")
    ApiResponse<Object> updateLeaderboardConfig(@PathVariable("key") String configKey,
                                                @RequestBody Map<String, Object> data);

    // ---- 灰度控制台 + AI 抽检（Sprint 2.8）----

    @GetMapping("/grayscale/configs")
    ApiResponse<Object> listGrayscaleConfigs();

    @PutMapping("/grayscale/configs/{key}")
    ApiResponse<Object> updateGrayscaleRatio(@PathVariable("key") String featureKey,
                                             @RequestBody Map<String, Object> data);

    @PostMapping("/ai-review/generate")
    ApiResponse<Object> generateAiReviewSamples(@RequestBody Map<String, Object> data);

    @GetMapping("/ai-review/samples")
    ApiResponse<Object> listAiReviewSamples(@RequestParam("scene") String scene,
                                            @RequestParam("result") String result,
                                            @RequestParam("page") Integer page,
                                            @RequestParam("size") Integer size);

    @PutMapping("/ai-review/samples/{id}")
    ApiResponse<Object> scoreAiReviewSample(@PathVariable("id") Long id,
                                            @RequestBody Map<String, Object> data);

    @GetMapping("/ai-review/stats")
    ApiResponse<Object> aiReviewStats();

    // ---- LBS 隐私审计（Sprint 3.1）----

    @GetMapping("/map/audit")
    ApiResponse<Object> mapAudit();

    // ---- 围栏 + 温暖事件（Sprint 3.2）----

    @GetMapping("/warm-map/fences")
    ApiResponse<Object> listFences(@RequestParam("wishId") Long wishId);

    @PostMapping("/warm-map/fences")
    ApiResponse<Object> createFence(@RequestBody Map<String, Object> data);

    @PutMapping("/warm-map/fences/{id}")
    ApiResponse<Object> updateFence(@PathVariable("id") Long fenceId,
                                    @RequestBody Map<String, Object> data);

    @PutMapping("/warm-map/fences/{id}/active")
    ApiResponse<Object> toggleFence(@PathVariable("id") Long fenceId,
                                    @RequestParam("active") boolean active);

    @DeleteMapping("/warm-map/fences/{id}")
    ApiResponse<Object> deleteFence(@PathVariable("id") Long fenceId);

    @GetMapping("/warm-map/warm-events")
    ApiResponse<Object> listWarmEventsForAdmin(@RequestParam("auditStatus") String auditStatus,
                                               @RequestParam("page") Integer page,
                                               @RequestParam("size") Integer size);

    @PutMapping("/warm-map/warm-events/{id}/audit")
    ApiResponse<Object> auditWarmEvent(@PathVariable("id") Long eventId,
                                       @RequestParam("auditStatus") String auditStatus);

    // ---- 社区活动（Sprint 3.5）----

    @GetMapping("/activity/list")
    ApiResponse<Object> listActivitiesForAdmin(@RequestParam("status") String status,
                                               @RequestParam("type") String type,
                                               @RequestParam("page") Integer page,
                                               @RequestParam("size") Integer size);

    @PostMapping("/activity")
    ApiResponse<Object> createActivity(@RequestBody Map<String, Object> data);

    @PutMapping("/activity/{id}")
    ApiResponse<Object> updateActivity(@PathVariable("id") Long id,
                                       @RequestBody Map<String, Object> data);

    @PostMapping("/activity/{id}/transition")
    ApiResponse<Object> transitionActivity(@PathVariable("id") Long id,
                                           @RequestParam("action") String action);

    @DeleteMapping("/activity/{id}")
    ApiResponse<Object> deleteActivity(@PathVariable("id") Long id);

    @PostMapping("/activity/{id}/rewards")
    ApiResponse<Object> issueActivityRewards(@PathVariable("id") Long id);

    @GetMapping("/activity/{id}/rewards/logs")
    ApiResponse<Object> listActivityRewardLogs(@PathVariable("id") Long id);

    // ---- 虚拟资产 + 品牌审核（Sprint 3.6 管理后台，代理 /admin/collection、/admin/brand）----

    @GetMapping("/collection/assets")
    ApiResponse<Object> listWishAssets();

    @PostMapping("/collection/assets")
    ApiResponse<Object> saveWishAsset(@RequestBody Map<String, Object> data);

    @PutMapping("/collection/assets/{id}/active")
    ApiResponse<Object> toggleWishAssetActive(@PathVariable("id") Long id,
                                              @RequestParam("active") boolean active);

    @DeleteMapping("/collection/assets/{id}")
    ApiResponse<Object> deleteWishAsset(@PathVariable("id") Long id);

    @GetMapping("/brand/list")
    ApiResponse<Object> listAllBrandsForAdmin();

    @PostMapping("/brand/{id}/audit")
    ApiResponse<Object> auditWishBrand(@PathVariable("id") Long id,
                                       @RequestParam("status") String status);

    // ---- 擦肩而过风控（Sprint 3.3 管理后台，代理 /admin/encounter）----

    @GetMapping("/encounter/suspicious")
    ApiResponse<Object> listSuspicious(@RequestParam("userId") Long userId);

    @GetMapping("/encounter/freezes")
    ApiResponse<Object> listFreezes();

    @PostMapping("/encounter/freezes/{userId}/unfreeze")
    ApiResponse<Object> unfreezeUser(@PathVariable("userId") Long userId);

    // ---- 直播挂件（Sprint 3.4 管理后台，代理 /admin/live/widget）----

    @GetMapping("/live/widget/configs")
    ApiResponse<Object> listLiveWidgetConfigs();

    @PutMapping("/live/widget/{streamerId}")
    ApiResponse<Object> saveLiveWidgetConfig(@PathVariable("streamerId") Long streamerId,
                                             @RequestBody Map<String, Object> data);

    @PutMapping("/live/widget/{streamerId}/visible")
    ApiResponse<Object> toggleLiveWidgetVisible(@PathVariable("streamerId") Long streamerId,
                                                @RequestParam("visible") boolean visible);
}
