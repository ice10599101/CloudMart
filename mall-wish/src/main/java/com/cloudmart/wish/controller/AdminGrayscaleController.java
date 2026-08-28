package com.cloudmart.wish.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.wish.entity.AiReview;
import com.cloudmart.wish.entity.GrayscaleConfig;
import com.cloudmart.wish.service.AiReviewService;
import com.cloudmart.wish.service.GrayscaleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理后台灰度控制台 + AI 质量抽检 Controller（Sprint 2.8）。
 *
 * <p>路由前缀 /admin/grayscale、/admin/ai-review，仅内部服务调用；
 * 权限点 {@code business:grayscale:list/edit}、{@code business:aiReview:list/score}。
 * 灰度配置需管理员权限（安全验收：非管理员 403——INTERNAL 角色由
 * X-Internal-Call 内部头授予，外部请求无法伪造）。</p>
 */
@RestController
@PreAuthorize("hasRole('INTERNAL')")
@RequiredArgsConstructor
@Tag(name = "管理后台-灰度控制台/AI 抽检", description = "灰度比例/回滚 + AI 回复人工抽检（Sprint 2.8）")
public class AdminGrayscaleController {

    private final GrayscaleService grayscaleService;
    private final AiReviewService aiReviewService;

    // ---------------- 灰度控制台 ----------------

    @GetMapping("/admin/grayscale/configs")
    @Operation(summary = "灰度配置列表", description = "全部功能键的当前灰度比例（0=已回滚/未放量）")
    public ApiResponse<List<GrayscaleConfig>> listConfigs() {
        return ApiResponse.ok(grayscaleService.listConfigs());
    }

    @PutMapping("/admin/grayscale/configs/{key}")
    @Operation(summary = "更新灰度比例", description = "比例自动吸附到文档档位 {0,5,20,50,100}；"
            + "回滚=置 0；更新回填缓存实时生效。并发切换安全（单行更新，无状态路由）")
    public ApiResponse<GrayscaleConfig> updateRatio(
            @Parameter(description = "功能键", required = true) @PathVariable("key") String featureKey,
            @RequestBody RatioUpdateRequest request,
            @Parameter(description = "操作管理员 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId) {
        return ApiResponse.ok(grayscaleService.updateRatio(featureKey, request.grayRatio(), adminUserId));
    }

    // ---------------- AI 质量抽检 ----------------

    @PostMapping("/admin/ai-review/generate")
    @Operation(summary = "生成抽检任务", description = "随机抽取指定场景 ASSISTANT 回复生成待评样本"
            + "（1-100 条，默认 20；已抽样回复不重复）")
    public ApiResponse<Integer> generateSamples(
            @RequestBody GenerateRequest request,
            @Parameter(description = "操作管理员 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId) {
        return ApiResponse.ok(aiReviewService.generateSamples(
                request.scenes(), request.sampleSize() == null ? 20 : request.sampleSize(), adminUserId));
    }

    @GetMapping("/admin/ai-review/samples")
    @Operation(summary = "样本列表", description = "scene/result 过滤可选，id 倒序，默认 20 条")
    public ApiResponse<List<AiReview>> listSamples(
            @Parameter(description = "场景过滤") @RequestParam(required = false) String scene,
            @Parameter(description = "评分过滤：PASS/FAIL") @RequestParam(required = false) String result,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") Integer size) {
        AiReview.ReviewResult resultFilter = null;
        if (result != null && !result.isBlank()) {
            resultFilter = AiReview.ReviewResult.valueOf(result.trim());
        }
        return ApiResponse.ok(aiReviewService.listSamples(scene, resultFilter, page, size));
    }

    @PutMapping("/admin/ai-review/samples/{id}")
    @Operation(summary = "人工评分", description = "PASS 或 FAIL+问题分类（MECHANICAL/ERROR/IRRELEVANT）；"
            + "PASS 时问题分类自动清空")
    public ApiResponse<AiReview> scoreSample(
            @Parameter(description = "样本 ID", required = true) @PathVariable Long id,
            @RequestBody ScoreRequest request,
            @Parameter(description = "操作管理员 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long adminUserId) {
        return ApiResponse.ok(aiReviewService.scoreSample(id,
                AiReview.ReviewResult.valueOf(request.result()),
                request.issueType() == null ? null : AiReview.IssueType.valueOf(request.issueType()),
                request.note(), adminUserId));
    }

    @GetMapping("/admin/ai-review/stats")
    @Operation(summary = "合格率与问题分类统计", description = "passRate=pass/(pass+fail)，"
            + "问题分类计数（机械感/错误信息/不相关）")
    public ApiResponse<AiReviewService.AiReviewStats> stats() {
        return ApiResponse.ok(aiReviewService.stats());
    }

    /** 灰度比例更新请求。 */
    public record RatioUpdateRequest(@NotNull(message = "灰度比例不能为空") Integer grayRatio) {
    }

    /** 抽样生成请求。 */
    public record GenerateRequest(List<String> scenes, Integer sampleSize) {
    }

    /** 评分请求。 */
    public record ScoreRequest(@NotNull String result, String issueType, String note) {
    }
}
