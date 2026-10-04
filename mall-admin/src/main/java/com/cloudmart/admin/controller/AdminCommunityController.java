package com.cloudmart.admin.controller;

import com.cloudmart.admin.feign.CommunityFeignClient;
import com.cloudmart.common.annotation.OperLog;
import com.cloudmart.common.annotation.RequiresPermission;
import com.cloudmart.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@Tag(name = "社区管理", description = "管理后台社区模块代理接口")
@RequiredArgsConstructor
public class AdminCommunityController {

    /** 净化透传查询参数：前端 ProTable 会把未填搜索项序列化为字符串 "undefined"/"null"，需剔除 */
    private static java.util.Map<String, Object> sanitizeParams(java.util.Map<String, Object> params) {
        java.util.Map<String, Object> cleaned = new java.util.HashMap<>();
        if (params == null) return cleaned;
        for (java.util.Map.Entry<String, Object> e : params.entrySet()) {
            Object v = e.getValue();
            if (v == null) continue;
            String str = String.valueOf(v);
            if (str.isBlank() || "undefined".equalsIgnoreCase(str) || "null".equalsIgnoreCase(str)) continue;
            cleaned.put(e.getKey(), v);
        }
        return cleaned;
    }


    private final CommunityFeignClient communityFeignClient;

    @GetMapping("/stats/overview")
    @Operation(summary = "社区概览统计")
    public ApiResponse<Map<String, Object>> getStatsOverview() {
        return communityFeignClient.getStatsOverview();
    }

    @GetMapping("/stats/trend")
    @Operation(summary = "社区趋势统计")
    public ApiResponse<List<Map<String, Object>>> getStatsTrend(@RequestParam(defaultValue = "7") int days) {
        return communityFeignClient.getStatsTrend(days);
    }

    @GetMapping("/community/posts")
    @Operation(summary = "帖子列表")
    public ApiResponse<Object> listPosts(@RequestParam Map<String, Object> params) {
        return communityFeignClient.listPosts(sanitizeParams(params));
    }

    @PutMapping("/community/posts/{id}/status")
    @OperLog(title = "帖子管理", businessType = 2)
    @RequiresPermission("community:post:edit")
    @Operation(summary = "更新帖子状态")
    public ApiResponse<Void> updatePostStatus(@PathVariable Long id, @RequestBody Map<String, Object> data) {
        return communityFeignClient.updatePostStatus(id, data);
    }

    @PutMapping("/community/posts/{id}/top")
    @OperLog(title = "帖子管理", businessType = 2)
    @RequiresPermission("community:post:edit")
    @Operation(summary = "切换帖子置顶")
    public ApiResponse<Void> togglePostTop(@PathVariable Long id, @RequestBody Map<String, Object> data) {
        return communityFeignClient.togglePostTop(id, data);
    }

    @DeleteMapping("/community/posts/{id}")
    @OperLog(title = "帖子管理", businessType = 3)
    @RequiresPermission("community:post:remove")
    @Operation(summary = "删除帖子")
    public ApiResponse<Void> deletePost(@PathVariable Long id) {
        return communityFeignClient.deletePost(id);
    }

    @GetMapping("/community/comments")
    @Operation(summary = "评论列表")
    public ApiResponse<Object> listComments(@RequestParam Map<String, Object> params) {
        return communityFeignClient.listComments(sanitizeParams(params));
    }

    @PutMapping("/community/comments/{id}/status")
    @OperLog(title = "评论管理", businessType = 2)
    @RequiresPermission("community:comment:edit")
    @Operation(summary = "更新评论状态")
    public ApiResponse<Void> updateCommentStatus(@PathVariable Long id, @RequestBody Map<String, Object> data) {
        return communityFeignClient.updateCommentStatus(id, data);
    }

    @DeleteMapping("/community/comments/{id}")
    @OperLog(title = "评论管理", businessType = 3)
    @RequiresPermission("community:comment:remove")
    @Operation(summary = "删除评论")
    public ApiResponse<Void> deleteComment(@PathVariable Long id) {
        return communityFeignClient.deleteComment(id);
    }

    @GetMapping("/community/tags")
    @Operation(summary = "标签列表")
    public ApiResponse<Object> listTags(@RequestParam Map<String, Object> params) {
        return communityFeignClient.listTags(sanitizeParams(params));
    }

    @PostMapping("/community/tags")
    @OperLog(title = "标签管理", businessType = 1)
    @RequiresPermission("community:tag:write")
    @Operation(summary = "创建标签")
    public ApiResponse<Object> createTag(@RequestBody Map<String, Object> data) {
        return communityFeignClient.createTag(data);
    }

    @PutMapping("/community/tags/{id}")
    @OperLog(title = "标签管理", businessType = 2)
    @RequiresPermission("community:tag:write")
    @Operation(summary = "更新标签")
    public ApiResponse<Object> updateTag(@PathVariable Long id, @RequestBody Map<String, Object> data) {
        return communityFeignClient.updateTag(id, data);
    }

    @DeleteMapping("/community/tags/{id}")
    @OperLog(title = "标签管理", businessType = 3)
    @RequiresPermission("community:tag:write")
    @Operation(summary = "删除标签")
    public ApiResponse<Void> deleteTag(@PathVariable Long id) {
        return communityFeignClient.deleteTag(id);
    }

    @PutMapping("/community/tags/{id}/status")
    @OperLog(title = "标签管理", businessType = 2)
    @RequiresPermission("community:tag:edit")
    @Operation(summary = "切换标签状态")
    public ApiResponse<Void> updateTagStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        return communityFeignClient.updateTagStatus(id, body.get("status"));
    }

    @GetMapping("/community/reports")
    @Operation(summary = "举报列表")
    public ApiResponse<Object> listReports(@RequestParam Map<String, Object> params) {
        return communityFeignClient.listReports(sanitizeParams(params));
    }

    @PutMapping("/community/reports/{id}/handle")
    @OperLog(title = "举报管理", businessType = 2)
    @RequiresPermission("community:report:handle")
    @Operation(summary = "处理举报")
    public ApiResponse<Void> handleReport(@PathVariable Long id, @RequestBody Map<String, Object> data) {
        return communityFeignClient.handleReport(id, data);
    }

    @GetMapping("/community/badges")
    @Operation(summary = "徽章列表")
    public ApiResponse<Object> listBadges(@RequestParam Map<String, Object> params) {
        return communityFeignClient.listBadges(sanitizeParams(params));
    }

    @PostMapping("/community/badges")
    @OperLog(title = "徽章管理", businessType = 1)
    @RequiresPermission("community:badge:write")
    @Operation(summary = "创建徽章")
    public ApiResponse<Object> createBadge(@RequestBody Map<String, Object> data) {
        return communityFeignClient.createBadge(data);
    }

    @PutMapping("/community/badges/{id}")
    @OperLog(title = "徽章管理", businessType = 2)
    @RequiresPermission("community:badge:write")
    @Operation(summary = "更新徽章")
    public ApiResponse<Object> updateBadge(@PathVariable Long id, @RequestBody Map<String, Object> data) {
        return communityFeignClient.updateBadge(id, data);
    }

    @DeleteMapping("/community/badges/{id}")
    @OperLog(title = "徽章管理", businessType = 3)
    @RequiresPermission("community:badge:write")
    @Operation(summary = "删除徽章")
    public ApiResponse<Void> deleteBadge(@PathVariable Long id) {
        return communityFeignClient.deleteBadge(id);
    }

    @PutMapping("/community/badges/{id}/status")
    @OperLog(title = "徽章管理", businessType = 2)
    @RequiresPermission("community:badge:edit")
    @Operation(summary = "切换徽章状态")
    public ApiResponse<Void> updateBadgeStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        return communityFeignClient.updateBadgeStatus(id, body.get("status"));
    }

    @PostMapping("/community/badges/{id}/grant")
    @OperLog(title = "徽章管理", businessType = 1)
    @RequiresPermission("community:badge:grant")
    @Operation(summary = "授予徽章")
    public ApiResponse<Void> grantBadge(@PathVariable Long id, @RequestBody Map<String, Object> data) {
        return communityFeignClient.grantBadge(id, data);
    }

    @PostMapping("/community/badges/{id}/revoke")
    @OperLog(title = "徽章管理", businessType = 2)
    @RequiresPermission("community:badge:grant")
    @Operation(summary = "撤销徽章", description = "T23：撤销原因必填（审计）；授予历史保留")
    public ApiResponse<Void> revokeBadge(@PathVariable Long id, @RequestBody Map<String, Object> data) {
        if (data.get("reason") == null || String.valueOf(data.get("reason")).isBlank()) {
            throw new com.cloudmart.common.exception.BusinessException("VALIDATION_ERROR", "撤销原因必填");
        }
        return communityFeignClient.revokeBadge(id, data);
    }

    @GetMapping("/community/rankings/current")
    @RequiresPermission("community:ranking:read")
    @Operation(summary = "当期榜单", description = "T23：脱敏视图；区分社区榜与心愿榜")
    public ApiResponse<Object> currentRankings() {
        return communityFeignClient.currentRankings();
    }

    @GetMapping("/community/rankings/seasons")
    @RequiresPermission("community:ranking:read")
    @Operation(summary = "赛季列表", description = "T23：赛季时间/规则/状态/快照版本")
    public ApiResponse<Object> listSeasons(@RequestParam Map<String, Object> params) {
        return communityFeignClient.listSeasons(params);
    }

    @GetMapping("/community/rankings/seasons/{seasonId}")
    @RequiresPermission("community:ranking:read")
    @Operation(summary = "赛季详情", description = "T23：含历史结果与快照版本")
    public ApiResponse<Object> seasonDetail(@PathVariable Long seasonId) {
        return communityFeignClient.seasonDetail(seasonId);
    }

    @PutMapping("/community/rankings/seasons/{seasonId}/status")
    @OperLog(title = "赛季管理", businessType = 2)
    @RequiresPermission("community:ranking:manage")
    @Operation(summary = "赛季启停", description = "T23：0 进行中/1 已归档；已结算规则不可原地修改（社区服务约束）")
    public ApiResponse<Void> updateSeasonStatus(@PathVariable Long seasonId,
                                                @RequestBody Map<String, Integer> body) {
        return communityFeignClient.updateSeasonStatus(seasonId, body);
    }

    @GetMapping("/community/growth/level-configs")
    @Operation(summary = "等级配置列表")
    public ApiResponse<Object> listLevelConfigs(@RequestParam Map<String, Object> params) {
        return communityFeignClient.listLevelConfigs(params);
    }

    @PostMapping("/community/growth/level-configs")
    @OperLog(title = "成长等级", businessType = 1)
    @RequiresPermission("community:growth:write")
    @Operation(summary = "创建等级配置")
    public ApiResponse<Object> createLevelConfig(@RequestBody Map<String, Object> data) {
        return communityFeignClient.createLevelConfig(data);
    }

    @PutMapping("/community/growth/level-configs/{id}")
    @OperLog(title = "成长等级", businessType = 2)
    @RequiresPermission("community:growth:write")
    @Operation(summary = "更新等级配置")
    public ApiResponse<Object> updateLevelConfig(@PathVariable Long id, @RequestBody Map<String, Object> data) {
        return communityFeignClient.updateLevelConfig(id, data);
    }

    @DeleteMapping("/community/growth/level-configs/{id}")
    @OperLog(title = "成长等级", businessType = 3)
    @RequiresPermission("community:growth:write")
    @Operation(summary = "删除等级配置")
    public ApiResponse<Void> deleteLevelConfig(@PathVariable Long id) {
        return communityFeignClient.deleteLevelConfig(id);
    }

    @PutMapping("/community/growth/level-configs/{id}/status")
    @OperLog(title = "成长等级", businessType = 2)
    @RequiresPermission("community:growth:edit")
    @Operation(summary = "切换等级配置状态")
    public ApiResponse<Void> updateGrowthLevelStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        return communityFeignClient.updateGrowthLevelStatus(id, body.get("status"));
    }

    @GetMapping("/review/pending/posts")
    @Operation(summary = "待审核帖子列表")
    public ApiResponse<Object> listPendingReviewPosts(@RequestParam Map<String, Object> params) {
        return communityFeignClient.listPendingReviewPosts(params);
    }

    @PutMapping("/review/posts/{id}/approve")
    @OperLog(title = "帖子管理", businessType = 2)
    @RequiresPermission("community:post:moderate")
    @Operation(summary = "审核通过帖子")
    public ApiResponse<Void> approvePost(@PathVariable Long id) {
        return communityFeignClient.approvePost(id);
    }

    @PutMapping("/review/posts/{id}/reject")
    @OperLog(title = "帖子管理", businessType = 2)
    @RequiresPermission("community:post:moderate")
    @Operation(summary = "审核拒绝帖子")
    public ApiResponse<Void> rejectPost(@PathVariable Long id, @RequestBody Map<String, String> data) {
        return communityFeignClient.rejectPost(id, data);
    }

    @GetMapping("/review/sensitive-words")
    @Operation(summary = "敏感词列表")
    public ApiResponse<Object> listSensitiveWords(@RequestParam Map<String, Object> params) {
        return communityFeignClient.listSensitiveWords(sanitizeParams(params));
    }

    @PostMapping("/review/sensitive-words")
    @OperLog(title = "敏感词管理", businessType = 1)
    @RequiresPermission("community:review:edit")
    @Operation(summary = "添加敏感词")
    public ApiResponse<Object> addSensitiveWord(@RequestBody Map<String, Object> data) {
        return communityFeignClient.addSensitiveWord(data);
    }

    @PutMapping("/review/sensitive-words/{id}")
    @OperLog(title = "敏感词管理", businessType = 2)
    @RequiresPermission("community:review:edit")
    @Operation(summary = "更新敏感词")
    public ApiResponse<Object> updateSensitiveWord(@PathVariable Long id, @RequestBody Map<String, Object> data) {
        return communityFeignClient.updateSensitiveWord(id, data);
    }

    @DeleteMapping("/review/sensitive-words/{id}")
    @OperLog(title = "敏感词管理", businessType = 3)
    @Operation(summary = "删除敏感词")
    public ApiResponse<Void> deleteSensitiveWord(@PathVariable Long id) {
        return communityFeignClient.deleteSensitiveWord(id);
    }

    @PostMapping("/review/sensitive-words/refresh")
    @OperLog(title = "敏感词管理", businessType = 1)
    @Operation(summary = "刷新敏感词缓存")
    public ApiResponse<Void> refreshSensitiveWordCache() {
        return communityFeignClient.refreshSensitiveWordCache();
    }
}
