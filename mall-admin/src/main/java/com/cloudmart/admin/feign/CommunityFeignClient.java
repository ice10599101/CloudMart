package com.cloudmart.admin.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(contextId = "communityFeignClient", name = "mall-community", path = "/admin", fallbackFactory = CommunityFeignClientFallbackFactory.class)
public interface CommunityFeignClient {

    @GetMapping("/stats/overview")
    ApiResponse<Map<String, Object>> getStatsOverview();

    @GetMapping("/stats/trend")
    ApiResponse<List<Map<String, Object>>> getStatsTrend(@RequestParam("days") int days);

    @GetMapping("/posts")
    ApiResponse<Object> listPosts(@SpringQueryMap Map<String, Object> params);

    @PutMapping("/posts/{id}/status")
    ApiResponse<Void> updatePostStatus(@PathVariable("id") Long id, @RequestBody Map<String, Object> data);

    @PutMapping("/posts/{id}/top")
    ApiResponse<Void> togglePostTop(@PathVariable("id") Long id, @RequestBody Map<String, Object> data);

    @DeleteMapping("/posts/{id}")
    ApiResponse<Void> deletePost(@PathVariable("id") Long id);

    @GetMapping("/comments")
    ApiResponse<Object> listComments(@SpringQueryMap Map<String, Object> params);

    @PutMapping("/comments/{id}/status")
    ApiResponse<Void> updateCommentStatus(@PathVariable("id") Long id, @RequestBody Map<String, Object> data);

    @DeleteMapping("/comments/{id}")
    ApiResponse<Void> deleteComment(@PathVariable("id") Long id);

    @GetMapping("/tags")
    ApiResponse<Object> listTags(@SpringQueryMap Map<String, Object> params);

    @PostMapping("/tags")
    ApiResponse<Object> createTag(@RequestBody Map<String, Object> data);

    @PutMapping("/tags/{id}")
    ApiResponse<Object> updateTag(@PathVariable("id") Long id, @RequestBody Map<String, Object> data);

    @DeleteMapping("/tags/{id}")
    ApiResponse<Void> deleteTag(@PathVariable("id") Long id);

    @PutMapping("/tags/{id}/status")
    ApiResponse<Void> updateTagStatus(@PathVariable("id") Long id, @RequestParam("status") Integer status);

    @GetMapping("/reports")
    ApiResponse<Object> listReports(@SpringQueryMap Map<String, Object> params);

    @PutMapping("/reports/{id}/handle")
    ApiResponse<Void> handleReport(@PathVariable("id") Long id, @RequestBody Map<String, Object> data);

    @GetMapping("/badges")
    ApiResponse<Object> listBadges(@SpringQueryMap Map<String, Object> params);

    @PostMapping("/badges")
    ApiResponse<Object> createBadge(@RequestBody Map<String, Object> data);

    @PutMapping("/badges/{id}")
    ApiResponse<Object> updateBadge(@PathVariable("id") Long id, @RequestBody Map<String, Object> data);

    @DeleteMapping("/badges/{id}")
    ApiResponse<Void> deleteBadge(@PathVariable("id") Long id);

    @PutMapping("/badges/{id}/status")
    ApiResponse<Void> updateBadgeStatus(@PathVariable("id") Long id, @RequestParam("status") Integer status);

    @PostMapping("/badges/{id}/grant")
    ApiResponse<Void> grantBadge(@PathVariable("id") Long id, @RequestBody Map<String, Object> data);

    @GetMapping("/growth/level-configs")
    ApiResponse<Object> listLevelConfigs(@SpringQueryMap Map<String, Object> params);

    @PostMapping("/growth/level-configs")
    ApiResponse<Object> createLevelConfig(@RequestBody Map<String, Object> data);

    @PutMapping("/growth/level-configs/{id}")
    ApiResponse<Object> updateLevelConfig(@PathVariable("id") Long id, @RequestBody Map<String, Object> data);

    @DeleteMapping("/growth/level-configs/{id}")
    ApiResponse<Void> deleteLevelConfig(@PathVariable("id") Long id);

    @PutMapping("/growth/level-configs/{id}/status")
    ApiResponse<Void> updateGrowthLevelStatus(@PathVariable("id") Long id, @RequestParam("status") Integer status);

    @GetMapping("/review/pending/posts")
    ApiResponse<Object> listPendingReviewPosts(@SpringQueryMap Map<String, Object> params);

    @PutMapping("/review/posts/{id}/approve")
    ApiResponse<Void> approvePost(@PathVariable("id") Long id);

    @PutMapping("/review/posts/{id}/reject")
    ApiResponse<Void> rejectPost(@PathVariable("id") Long id, @RequestBody Map<String, String> data);

    @GetMapping("/review/sensitive-words")
    ApiResponse<Object> listSensitiveWords(@SpringQueryMap Map<String, Object> params);

    @PostMapping("/review/sensitive-words")
    ApiResponse<Object> addSensitiveWord(@RequestBody Map<String, Object> data);

    @PutMapping("/review/sensitive-words/{id}")
    ApiResponse<Object> updateSensitiveWord(@PathVariable("id") Long id, @RequestBody Map<String, Object> data);

    @DeleteMapping("/review/sensitive-words/{id}")
    ApiResponse<Void> deleteSensitiveWord(@PathVariable("id") Long id);

    @PostMapping("/review/sensitive-words/refresh")
    ApiResponse<Void> refreshSensitiveWordCache();
}
