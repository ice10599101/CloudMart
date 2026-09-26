package com.cloudmart.wish.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.wish.entity.ModerationCase;
import com.cloudmart.wish.entity.WishAppeal;
import com.cloudmart.wish.entity.WishReport;
import com.cloudmart.wish.service.impl.ModerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 心愿治理用户端点（N01，/api/wish/v2/**）：举报、我的举报、申诉、我的申诉。
 * 用户路径不接受指定操作者——操作者一律取认证上下文。
 */
@RestController
@RequestMapping("/v2")
@Tag(name = "心愿宇宙·治理（用户）", description = "举报与申诉（N01）")
@RequiredArgsConstructor
public class WishModerationController {

    private final ModerationService moderationService;

    public record SubmitReportRequest(
            @NotBlank(message = "目标类型不能为空") String targetType,
            @NotNull(message = "目标ID不能为空") Long targetId,
            @NotBlank(message = "举报原因不能为空") String reasonCode,
            @Size(max = 500, message = "说明不能超过500字") String description,
            List<String> evidenceMediaIds) {
    }

    public record SubmitAppealRequest(
            @NotBlank(message = "申诉陈述不能为空") @Size(max = 1000, message = "陈述不能超过1000字")
            String statement,
            List<String> evidenceMediaIds) {
    }

    @PostMapping("/reports")
    @Operation(summary = "提交举报", description = "仅可举报本人有权看到的内容；OTHER 原因必填说明；"
            + "每日最多 10 次有效举报；同内容同理由未结举报合并返回原记录。errors: 404/422/429")
    public ApiResponse<Long> submitReport(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @RequestBody SubmitReportRequest request) {
        return ApiResponse.ok(moderationService.submitReport(userId, request.targetType(),
                request.targetId(), request.reasonCode(), request.description(),
                request.evidenceMediaIds()));
    }

    @GetMapping("/my/reports")
    @Operation(summary = "我的举报记录", description = "本人提交记录与处理进度；不显示运营内部备注")
    public ApiResponse<List<WishReport>> myReports(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        return ApiResponse.ok(moderationService.listMyReports(userId, cursor, pageSize));
    }

    @PostMapping("/moderation-decisions/{id}/appeals")
    @Operation(summary = "对治理决定申诉", description = "仅被处理作者；决定 7 日内；同决定同作者一条。"
            + "errors: 403/404/422")
    public ApiResponse<Long> appeal(
            @Parameter(description = "治理决定 ID", required = true) @PathVariable("id") Long decisionId,
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @RequestBody SubmitAppealRequest request) {
        return ApiResponse.ok(moderationService.submitAppeal(decisionId, userId,
                request.statement(), request.evidenceMediaIds()));
    }

    @GetMapping("/my/appeals")
    @Operation(summary = "我的申诉进度", description = "本人申诉记录与复核结果")
    public ApiResponse<List<WishAppeal>> myAppeals(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        return ApiResponse.ok(moderationService.listMyAppeals(userId, cursor, pageSize));
    }
}
