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
}
