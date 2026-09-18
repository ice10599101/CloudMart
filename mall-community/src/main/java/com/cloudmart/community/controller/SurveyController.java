package com.cloudmart.community.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.community.dto.CreateSurveyRequest;
import com.cloudmart.community.dto.SurveyResponseRequest;
import com.cloudmart.community.service.SurveyService;
import com.cloudmart.community.vo.SurveyVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 问卷 Controller（编辑器附件，V10）。
 *
 * <p>创建/答卷需登录；详情对匿名开放。答卷按 (问卷, 题目, 用户) 唯一，
 * 重复提交覆盖更新。</p>
 */
@RestController
@RequestMapping("/surveys")
@Tag(name = "问卷", description = "编辑器问卷附件：创建/详情/答卷")
@RequiredArgsConstructor
public class SurveyController {

    private final SurveyService surveyService;

    @PostMapping
    @Operation(summary = "创建问卷", description = "发布含问卷的正文时由前端调用；主键为客户端 UUID，幂等落库")
    public ApiResponse<SurveyVO> createSurvey(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @RequestBody CreateSurveyRequest request) {
        return ApiResponse.ok(surveyService.createSurvey(userId, request));
    }

    @GetMapping("/{surveyId}")
    @Operation(summary = "问卷详情", description = "含题目与各题聚合结果；已登录时返回我的答案")
    public ApiResponse<SurveyVO> getSurvey(
            @Parameter(description = "问卷 ID", required = true) @PathVariable("surveyId") String surveyId,
            @Parameter(description = "当前用户 ID（网关注入，匿名查看时缺失）")
            @RequestHeader(value = SecurityConstants.USER_ID_HEADER, required = false) Long userId) {
        return ApiResponse.ok(surveyService.getSurvey(surveyId, userId));
    }

    @PostMapping("/{surveyId}/responses")
    @Operation(summary = "提交答卷", description = "必答题缺失返回 400；重复提交覆盖更新")
    public ApiResponse<Void> submitResponse(
            @Parameter(description = "问卷 ID", required = true) @PathVariable("surveyId") String surveyId,
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @RequestBody SurveyResponseRequest request) {
        surveyService.submitResponse(surveyId, userId, request);
        return ApiResponse.ok(null);
    }
}
