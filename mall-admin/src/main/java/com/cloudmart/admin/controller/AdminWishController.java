package com.cloudmart.admin.controller;

import com.cloudmart.admin.dto.feign.AdminCommentSearchRequest;
import com.cloudmart.admin.dto.feign.AdminInteractionSearchRequest;
import com.cloudmart.admin.dto.feign.AdminWishSearchRequest;
import com.cloudmart.admin.feign.WishFeignClient;
import com.cloudmart.common.annotation.OperLog;
import com.cloudmart.common.annotation.RequiresPermission;
import com.cloudmart.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 心愿宇宙管理代理接口。
 *
 * <p>转发至 mall-wish /admin/** 内部端点，由网关 AdminAuthGlobalFilter
 * 校验管理员身份（/api/admin/** 前缀）。</p>
 */
@RestController
@Tag(name = "心愿管理", description = "管理后台心愿宇宙模块代理接口")
@RequiredArgsConstructor
public class AdminWishController {

    private final WishFeignClient wishFeignClient;

    // ========== 心愿列表与审核 ==========

    @GetMapping("/wish/wishes")
    @RequiresPermission("business:wish:list")
    @Operation(summary = "心愿列表", description = "多维度筛选（状态/审核状态/分类/关键词）offset 分页")
    public ApiResponse<Object> listWishes(@Valid AdminWishSearchRequest request) {
        return wishFeignClient.listWishes(request);
    }

    @GetMapping("/wish/wishes/{id}")
    @RequiresPermission("business:wish:query")
    @Operation(summary = "心愿详情", description = "含审核字段与软删时间")
    public ApiResponse<Object> getWish(@PathVariable Long id) {
        return wishFeignClient.getWish(id);
    }

    @PutMapping("/wish/wishes/{id}/audit")
    @OperLog(title = "心愿审核", businessType = 2)
    @RequiresPermission("business:wish:audit")
    @Operation(summary = "审核心愿", description = "PENDING → APPROVED/REJECTED，REJECTED 需填写原因")
    public ApiResponse<Object> auditWish(@PathVariable Long id, @RequestBody Map<String, Object> data) {
        return wishFeignClient.auditWish(id, data);
    }

    // ========== 心愿分类管理 ==========

    @GetMapping("/wish/categories")
    @RequiresPermission("business:wishCategory:list")
    @Operation(summary = "心愿分类列表")
    public ApiResponse<Object> listCategories() {
        return wishFeignClient.listCategories();
    }

    @PostMapping("/wish/categories")
    @OperLog(title = "心愿分类管理", businessType = 1)
    @RequiresPermission("business:wishCategory:add")
    @Operation(summary = "创建心愿分类", description = "code 唯一")
    public ApiResponse<Object> createCategory(@RequestBody Map<String, Object> data) {
        return wishFeignClient.createCategory(data);
    }

    @PutMapping("/wish/categories/{id}")
    @OperLog(title = "心愿分类管理", businessType = 2)
    @RequiresPermission("business:wishCategory:edit")
    @Operation(summary = "更新心愿分类")
    public ApiResponse<Object> updateCategory(@PathVariable Long id, @RequestBody Map<String, Object> data) {
        return wishFeignClient.updateCategory(id, data);
    }

    @DeleteMapping("/wish/categories/{id}")
    @OperLog(title = "心愿分类管理", businessType = 3)
    @RequiresPermission("business:wishCategory:remove")
    @Operation(summary = "删除心愿分类", description = "系统预设分类不可删除")
    public ApiResponse<Void> deleteCategory(@PathVariable Long id) {
        return wishFeignClient.deleteCategory(id);
    }

    // ========== 互动记录审计（Sprint 1.2） ==========

    @GetMapping("/wish/interactions")
    @RequiresPermission("business:wishInteraction:list")
    @Operation(summary = "互动记录列表", description = "含已取消记录的完整审计轨迹，"
            + "支持心愿/用户/类型/时间范围筛选，offset 分页")
    public ApiResponse<Object> listInteractions(@Valid AdminInteractionSearchRequest request) {
        return wishFeignClient.listInteractions(request);
    }

    // ========== 评论审核（Sprint 1.2） ==========

    @GetMapping("/wish/comments")
    @RequiresPermission("business:wishComment:list")
    @Operation(summary = "评论列表", description = "含已删除评论供审计；"
            + "敏感词审核场景：sensitiveHit=true + status=VISIBLE 筛选待处理命中")
    public ApiResponse<Object> listComments(@Valid AdminCommentSearchRequest request) {
        return wishFeignClient.listComments(request);
    }

    @PutMapping("/wish/comments/{id}/status")
    @OperLog(title = "心愿评论审核", businessType = 2)
    @RequiresPermission("business:wishComment:audit")
    @Operation(summary = "评论上下架", description = "HIDDEN=下架（四端立即不展示），VISIBLE=恢复上架")
    public ApiResponse<Object> updateCommentStatus(@PathVariable Long id,
                                                   @RequestBody Map<String, Object> data) {
        return wishFeignClient.updateCommentStatus(id, data);
    }

    // ========== 徽章管理（Sprint 1.8） ==========

    @GetMapping("/wish/badges")
    @RequiresPermission("business:wishBadge:list")
    @Operation(summary = "徽章列表", description = "全量含下架状态与原始 condition JSON（编辑器回显）")
    public ApiResponse<Object> listBadges() {
        return wishFeignClient.listBadges();
    }

    @PostMapping("/wish/badges")
    @OperLog(title = "徽章管理", businessType = 1)
    @RequiresPermission("business:wishBadge:add")
    @Operation(summary = "新增徽章", description = "code 唯一；condition JSON 结构校验"
            + "（type/threshold/description 三段式）")
    public ApiResponse<Object> createBadge(@RequestBody Map<String, Object> data) {
        return wishFeignClient.createBadge(data);
    }

    @PutMapping("/wish/badges/{id}")
    @OperLog(title = "徽章管理", businessType = 2)
    @RequiresPermission("business:wishBadge:edit")
    @Operation(summary = "编辑徽章", description = "code 不可修改；condition 编辑校验同新增")
    public ApiResponse<Object> updateBadge(@PathVariable Long id,
                                           @RequestBody Map<String, Object> data) {
        return wishFeignClient.updateBadge(id, data);
    }

    @PutMapping("/wish/badges/{id}/status")
    @OperLog(title = "徽章上下架", businessType = 2)
    @RequiresPermission("business:wishBadge:edit")
    @Operation(summary = "徽章上下架", description = "下架后不参与授予判定、不出现在徽章墙与图鉴；"
            + "已获得记录保留，重新上架自动恢复")
    public ApiResponse<Object> updateBadgeStatus(@PathVariable Long id,
                                                 @RequestBody Map<String, Object> data) {
        return wishFeignClient.updateBadgeStatus(id, data);
    }

    // ========== 背景音乐曲库管理（Sprint 2.3） ==========

    @GetMapping("/wish/bgm")
    @RequiresPermission("business:wishBgm:list")
    @Operation(summary = "BGM 歌曲列表", description = "全量含未激活（管理端表格），sort 升序；"
            + "url 为 OSS 直链可供试听")
    public ApiResponse<Object> listBgmSongs() {
        return wishFeignClient.listBgmSongs();
    }

    @PostMapping("/wish/bgm")
    @OperLog(title = "BGM 曲库管理", businessType = 1)
    @RequiresPermission("business:wishBgm:add")
    @Operation(summary = "登记歌曲", description = "前端先调 mall-file /file/upload 传 mp3"
            + "（白名单已含，上限 50MB）拿到 URL 再登记；默认未加入播放列表")
    public ApiResponse<Object> createBgmSong(@RequestBody Map<String, Object> data) {
        return wishFeignClient.createBgmSong(data);
    }

    @PutMapping("/wish/bgm/{id}")
    @OperLog(title = "BGM 曲库管理", businessType = 2)
    @RequiresPermission("business:wishBgm:edit")
    @Operation(summary = "编辑歌曲", description = "title/sort 可改；url 不可改"
            + "（换歌走重新上传+登记）")
    public ApiResponse<Object> updateBgmSong(@PathVariable Long id,
                                             @RequestBody Map<String, Object> data) {
        return wishFeignClient.updateBgmSong(id, data);
    }

    @PutMapping("/wish/bgm/{id}/status")
    @OperLog(title = "BGM 播放列表勾选", businessType = 2)
    @RequiresPermission("business:wishBgm:edit")
    @Operation(summary = "启停歌曲（勾选播放列表）", description = "active=true 加入播放列表；"
            + "多首激活=顺序循环，单首激活=单曲循环；空列表四端回退默认曲")
    public ApiResponse<Object> updateBgmSongStatus(@PathVariable Long id,
                                                    @RequestBody Map<String, Object> data) {
        return wishFeignClient.updateBgmSongStatus(id, data);
    }

    @DeleteMapping("/wish/bgm/{id}")
    @OperLog(title = "BGM 曲库管理", businessType = 3)
    @RequiresPermission("business:wishBgm:delete")
    @Operation(summary = "删除歌曲", description = "物理删除元数据行（OSS 音频文件保留）；"
            + "正在播放列表中则同时移出")
    public ApiResponse<Void> deleteBgmSong(@PathVariable Long id) {
        return wishFeignClient.deleteBgmSong(id);
    }

    // ========== AI 心愿助手管理（Sprint 2.5） ==========

    @GetMapping("/wish/ai/prompts")
    @RequiresPermission("business:aiPrompt:list")
    @Operation(summary = "Prompt 模板列表", description = "含 DRAFT/ACTIVE/ARCHIVED 全状态；"
            + "scene 过滤可选（GOAL_BREAKDOWN/TREE_HOLE/ANNUAL_REPORT/EXPECTED_GUIDE）")
    public ApiResponse<Object> listAiPrompts(@RequestParam(required = false) String scene) {
        return wishFeignClient.listAiPrompts(scene);
    }

    @PostMapping("/wish/ai/prompts")
    @OperLog(title = "AI Prompt 管理", businessType = 1)
    @RequiresPermission("business:aiPrompt:add")
    @Operation(summary = "创建新版本模板", description = "初始 DRAFT 不生效；version 在 scene 内自动递增；"
            + "激活后进入 A/B 分流（trafficPercent 加权）")
    public ApiResponse<Object> createAiPrompt(@RequestBody Map<String, Object> data) {
        return wishFeignClient.createAiPrompt(data);
    }

    @PutMapping("/wish/ai/prompts/{id}/status")
    @OperLog(title = "AI Prompt 管理", businessType = 2)
    @RequiresPermission("business:aiPrompt:edit")
    @Operation(summary = "模板状态流转", description = "DRAFT→ACTIVE 生效 / ACTIVE→ARCHIVED 下线；"
            + "激活时可携带 trafficPercent 配置 A/B 权重；正文不可改（建新版本）；"
            + "运行时 60s 缓存，修改后最迟 1 分钟生效不重部署")
    public ApiResponse<Object> updateAiPromptStatus(@PathVariable Long id,
                                                    @RequestBody Map<String, Object> data) {
        return wishFeignClient.updateAiPromptStatus(id, data);
    }

    @GetMapping("/wish/ai/configs")
    @RequiresPermission("business:aiConfig:list")
    @Operation(summary = "AI 策略配置列表", description = "陪伴提醒频次/免打扰时段/预期管理限频/"
            + "年度报告缓存时长等全局策略项")
    public ApiResponse<Object> listAiConfigs() {
        return wishFeignClient.listAiConfigs();
    }

    @PutMapping("/wish/ai/configs/{key}")
    @OperLog(title = "AI 策略配置", businessType = 2)
    @RequiresPermission("business:aiConfig:edit")
    @Operation(summary = "更新策略配置", description = "更新后主动失效缓存实时生效；键不存在返回 400")
    public ApiResponse<Object> updateAiConfig(@PathVariable String key,
                                              @RequestBody Map<String, Object> data) {
        return wishFeignClient.updateAiConfig(key, data);
    }

    // ---- 同愿匹配 + 监督小队（Sprint 2.6）----

    @GetMapping("/wish/match/groups")
    @RequiresPermission("business:matchGroup:list")
    @Operation(summary = "同愿小组列表", description = "全量小组（含 CLOSED）；status/keyword 过滤可选；"
            + "含组长昵称与最近活跃时间（活跃度监控口径）")
    public ApiResponse<Object> listMatchGroups(@RequestParam(required = false) String status,
                                               @RequestParam(required = false) String keyword) {
        return wishFeignClient.listMatchGroups(status, keyword);
    }

    @PostMapping("/wish/match/groups/{id}/dissolution")
    @OperLog(title = "同愿小组解散", businessType = 2)
    @RequiresPermission("business:matchGroup:close")
    @Operation(summary = "强制解散异常小组", description = "关闭小组 + 成员关系置 LEFT + 逐成员通知")
    public ApiResponse<Object> forceDissolveMatchGroup(@PathVariable Long id) {
        return wishFeignClient.forceDissolveMatchGroup(id);
    }

    @GetMapping("/wish/match/configs")
    @RequiresPermission("business:matchConfig:list")
    @Operation(summary = "匹配算法配置列表", description = "关键词/城市/活跃度权重、相似度阈值、提醒与建组限频")
    public ApiResponse<Object> listMatchConfigs() {
        return wishFeignClient.listMatchConfigs();
    }

    @PutMapping("/wish/match/configs/{key}")
    @OperLog(title = "匹配算法配置", businessType = 2)
    @RequiresPermission("business:matchConfig:edit")
    @Operation(summary = "更新匹配算法配置", description = "权重可配置实时生效（文档 2.6 验收：调整后结果排序变化不改代码）")
    public ApiResponse<Object> updateMatchConfig(@PathVariable String key,
                                                 @RequestBody Map<String, Object> data) {
        return wishFeignClient.updateMatchConfig(key, data);
    }

    // ---- 传承 + 排行榜（Sprint 2.7）----

    @GetMapping("/wish/legacy/flows")
    @RequiresPermission("business:legacy:list")
    @Operation(summary = "内容流转日志", description = "还愿 → community 帖子流转记录（SUCCESS/FAILED/HIDDEN）")
    public ApiResponse<Object> listContentFlowLogs(@RequestParam(required = false) String status,
                                                   @RequestParam(defaultValue = "1") Integer page,
                                                   @RequestParam(defaultValue = "20") Integer size) {
        return wishFeignClient.listContentFlowLogs(status, page, size);
    }

    @PostMapping("/wish/legacy/flows/{id}/retry")
    @OperLog(title = "内容流转重试", businessType = 2)
    @RequiresPermission("business:legacy:retry")
    @Operation(summary = "重试内容流转", description = "对 FAILED 流转单次重试（community 不可用时的补偿入口）")
    public ApiResponse<Object> retryContentFlow(@PathVariable Long id) {
        return wishFeignClient.retryContentFlow(id);
    }

    @GetMapping("/wish/legacy/stats")
    @RequiresPermission("business:legacy:list")
    @Operation(summary = "传承统计", description = "传承次数/目标与推送数/推送成功率/流转状态分布")
    public ApiResponse<Object> legacyStats() {
        return wishFeignClient.legacyStats();
    }

    @GetMapping("/wish/leaderboard/configs")
    @RequiresPermission("business:leaderboard:list")
    @Operation(summary = "排行榜配置列表", description = "刷新周期/Top N/同分处理/封禁过滤")
    public ApiResponse<Object> listLeaderboardConfigs() {
        return wishFeignClient.listLeaderboardConfigs();
    }

    @PutMapping("/wish/leaderboard/configs/{key}")
    @OperLog(title = "排行榜配置", businessType = 2)
    @RequiresPermission("business:leaderboard:edit")
    @Operation(summary = "更新排行榜配置", description = "配置修改实时生效（下次刷新任务按新值执行）")
    public ApiResponse<Object> updateLeaderboardConfig(@PathVariable String key,
                                                       @RequestBody Map<String, Object> data) {
        return wishFeignClient.updateLeaderboardConfig(key, data);
    }

    // ---- 灰度控制台 + AI 抽检（Sprint 2.8）----

    @GetMapping("/wish/grayscale/configs")
    @RequiresPermission("business:grayscale:list")
    @Operation(summary = "灰度配置列表", description = "全部功能键的当前灰度比例（0=已回滚/未放量）")
    public ApiResponse<Object> listGrayscaleConfigs() {
        return wishFeignClient.listGrayscaleConfigs();
    }

    @PutMapping("/wish/grayscale/configs/{key}")
    @OperLog(title = "灰度比例调整", businessType = 2)
    @RequiresPermission("business:grayscale:edit")
    @Operation(summary = "更新灰度比例", description = "比例吸附档位 {0,5,20,50,100}；回滚=置 0；实时生效")
    public ApiResponse<Object> updateGrayscaleRatio(@PathVariable String key,
                                                    @RequestBody Map<String, Object> data) {
        return wishFeignClient.updateGrayscaleRatio(key, data);
    }

    @PostMapping("/wish/ai-review/generate")
    @OperLog(title = "AI 抽检任务生成", businessType = 1)
    @RequiresPermission("business:aiReview:score")
    @Operation(summary = "生成抽检任务", description = "随机抽取 ASSISTANT 回复生成待评样本（1-100 条）")
    public ApiResponse<Object> generateAiReviewSamples(@RequestBody Map<String, Object> data) {
        return wishFeignClient.generateAiReviewSamples(data);
    }

    @GetMapping("/wish/ai-review/samples")
    @RequiresPermission("business:aiReview:list")
    @Operation(summary = "抽检样本列表", description = "scene/result 过滤可选")
    public ApiResponse<Object> listAiReviewSamples(@RequestParam(required = false) String scene,
                                                   @RequestParam(required = false) String result,
                                                   @RequestParam(defaultValue = "1") Integer page,
                                                   @RequestParam(defaultValue = "20") Integer size) {
        return wishFeignClient.listAiReviewSamples(scene, result, page, size);
    }

    @PutMapping("/wish/ai-review/samples/{id}")
    @OperLog(title = "AI 抽检评分", businessType = 2)
    @RequiresPermission("business:aiReview:score")
    @Operation(summary = "人工评分", description = "PASS 或 FAIL+问题分类（机械感/错误信息/不相关）")
    public ApiResponse<Object> scoreAiReviewSample(@PathVariable Long id,
                                                   @RequestBody Map<String, Object> data) {
        return wishFeignClient.scoreAiReviewSample(id, data);
    }

    @GetMapping("/wish/ai-review/stats")
    @RequiresPermission("business:aiReview:list")
    @Operation(summary = "合格率与问题分类统计", description = "passRate=pass/(pass+fail) + 分类计数")
    public ApiResponse<Object> aiReviewStats() {
        return wishFeignClient.aiReviewStats();
    }

    // ---- LBS 隐私审计（Sprint 3.1）----

    @GetMapping("/wish/map/audit")
    @RequiresPermission("business:map:audit")
    @Operation(summary = "隐私审计面板", description = "PUBLIC 心愿 geohash 覆盖统计 + 模糊化策略说明")
    public ApiResponse<Object> mapAudit() {
        return wishFeignClient.mapAudit();
    }

    // ---- 围栏 + 温暖事件（Sprint 3.2）----

    @GetMapping("/wish/warm-map/fences")
    @RequiresPermission("business:fence:list")
    @Operation(summary = "围栏列表", description = "全部围栏（含未启用；含中心坐标回显——仅管理端可见）")
    public ApiResponse<Object> listFences(@RequestParam(required = false) Long wishId) {
        return wishFeignClient.listFences(wishId);
    }

    @PostMapping("/wish/warm-map/fences")
    @OperLog(title = "围栏创建", businessType = 1)
    @RequiresPermission("business:fence:add")
    @Operation(summary = "创建围栏", description = "半径最小 10m；center 服务端 geohash7 编码存储")
    public ApiResponse<Object> createFence(@RequestBody Map<String, Object> data) {
        return wishFeignClient.createFence(data);
    }

    @PutMapping("/wish/warm-map/fences/{id}")
    @OperLog(title = "围栏更新", businessType = 2)
    @RequiresPermission("business:fence:edit")
    @Operation(summary = "更新围栏", description = "字段覆盖式更新")
    public ApiResponse<Object> updateFence(@PathVariable Long id, @RequestBody Map<String, Object> data) {
        return wishFeignClient.updateFence(id, data);
    }

    @PutMapping("/wish/warm-map/fences/{id}/active")
    @OperLog(title = "围栏状态切换", businessType = 2)
    @RequiresPermission("business:fence:edit")
    @Operation(summary = "启用/停用围栏", description = "is_active=0 → 判定恒 false")
    public ApiResponse<Object> toggleFence(@PathVariable Long id, @RequestParam boolean active) {
        return wishFeignClient.toggleFence(id, active);
    }

    @DeleteMapping("/wish/warm-map/fences/{id}")
    @OperLog(title = "围栏删除", businessType = 2)
    @RequiresPermission("business:fence:edit")
    @Operation(summary = "删除围栏", description = "配置数据物理删除；到达记录保留审计")
    public ApiResponse<Object> deleteFence(@PathVariable Long id) {
        return wishFeignClient.deleteFence(id);
    }

    @GetMapping("/wish/warm-map/warm-events")
    @RequiresPermission("business:warmEvent:list")
    @Operation(summary = "温暖事件审核列表", description = "全状态分页")
    public ApiResponse<Object> listWarmEventsForAdmin(@RequestParam(required = false) String auditStatus,
                                                      @RequestParam(defaultValue = "1") Integer page,
                                                      @RequestParam(defaultValue = "20") Integer size) {
        return wishFeignClient.listWarmEventsForAdmin(auditStatus, page, size);
    }

    @PutMapping("/wish/warm-map/warm-events/{id}/audit")
    @OperLog(title = "温暖事件审核", businessType = 2)
    @RequiresPermission("business:warmEvent:audit")
    @Operation(summary = "审核温暖事件", description = "APPROVED/REJECTED/AUTO_HIDDEN 同步 is_visible")
    public ApiResponse<Object> auditWarmEvent(@PathVariable Long id, @RequestParam String auditStatus) {
        return wishFeignClient.auditWarmEvent(id, auditStatus);
    }

    // ---- 社区活动（Sprint 3.5）----

    @GetMapping("/wish/activity/list")
    @RequiresPermission("business:activity:list")
    @Operation(summary = "活动列表（全状态）", description = "status/type 过滤可选，分页")
    public ApiResponse<Object> listActivitiesForAdmin(@RequestParam(required = false) String status,
                                                      @RequestParam(required = false) String type,
                                                      @RequestParam(defaultValue = "1") Integer page,
                                                      @RequestParam(defaultValue = "20") Integer size) {
        return wishFeignClient.listActivitiesForAdmin(status, type, page, size);
    }

    @PostMapping("/wish/activity")
    @OperLog(title = "活动创建", businessType = 1)
    @RequiresPermission("business:activity:add")
    @Operation(summary = "创建活动", description = "初始 DRAFT；condition JSON 校验（非法 400）")
    public ApiResponse<Object> createActivity(@RequestBody Map<String, Object> data) {
        return wishFeignClient.createActivity(data);
    }

    @PutMapping("/wish/activity/{id}")
    @OperLog(title = "活动更新", businessType = 2)
    @RequiresPermission("business:activity:edit")
    @Operation(summary = "更新活动", description = "仅 DRAFT/ACTIVE 可改")
    public ApiResponse<Object> updateActivity(@PathVariable Long id, @RequestBody Map<String, Object> data) {
        return wishFeignClient.updateActivity(id, data);
    }

    @PostMapping("/wish/activity/{id}/transition")
    @OperLog(title = "活动状态机流转", businessType = 2)
    @RequiresPermission("business:activity:edit")
    @Operation(summary = "活动状态机流转", description = "start/end/archive（非法流转 409）")
    public ApiResponse<Object> transitionActivity(@PathVariable Long id, @RequestParam String action) {
        return wishFeignClient.transitionActivity(id, action);
    }

    @DeleteMapping("/wish/activity/{id}")
    @OperLog(title = "活动删除", businessType = 2)
    @RequiresPermission("business:activity:edit")
    @Operation(summary = "删除活动", description = "仅 DRAFT 可删")
    public ApiResponse<Object> deleteActivity(@PathVariable Long id) {
        return wishFeignClient.deleteActivity(id);
    }

    @PostMapping("/wish/activity/{id}/rewards")
    @OperLog(title = "活动奖励发放", businessType = 2)
    @RequiresPermission("business:activity:reward")
    @Operation(summary = "发放奖励", description = "条件达成后对参与/组队用户发星光/徽章；uk 幂等（重复跳过）")
    public ApiResponse<Object> issueActivityRewards(@PathVariable Long id) {
        return wishFeignClient.issueActivityRewards(id);
    }

    @GetMapping("/wish/activity/{id}/rewards/logs")
    @RequiresPermission("business:activity:list")
    @Operation(summary = "奖励发放日志", description = "审计：时间/用户/奖励类型/数量")
    public ApiResponse<Object> listActivityRewardLogs(@PathVariable Long id) {
        return wishFeignClient.listActivityRewardLogs(id);
    }
}
