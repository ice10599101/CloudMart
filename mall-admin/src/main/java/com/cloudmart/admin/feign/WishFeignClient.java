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

    @GetMapping("/wishes/{id}")
    ApiResponse<Object> getWish(@PathVariable("id") Long id);

    @PutMapping("/wishes/{id}/audit")
    ApiResponse<Object> auditWish(@PathVariable("id") Long id, @RequestBody Map<String, Object> data);

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
}
