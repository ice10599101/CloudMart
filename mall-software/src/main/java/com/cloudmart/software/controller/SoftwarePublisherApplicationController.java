package com.cloudmart.software.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.software.service.SoftwarePublisherApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 发布者申请（SF03 用户侧）：申请/我的申请/撤回。
 * 身份一律取网关注入的用户头；写接口走 Idempotency-Key。
 */
@RestController
@RequestMapping("/software/publisher-applications")
@RequiredArgsConstructor
@Tag(name = "发布者申请", description = "发布资格申请闭环（SF03）")
public class SoftwarePublisherApplicationController {

    private final SoftwarePublisherApplicationService applicationService;

    public record ApplicationSubmitRequest(String intro, String intendedCategories,
                                           String evidenceAssetIds) {
    }

    @PostMapping
    @Operation(summary = "提交发布资格申请", description = "每用户最多一条待审申请；重复提交返回既有记录")
    public ApiResponse<Map<String, Object>> submit(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody ApplicationSubmitRequest request) {
        return ApiResponse.ok(applicationService.submit(userId, idempotencyKey,
                request.intro(), request.intendedCategories(), request.evidenceAssetIds()));
    }

    @GetMapping("/mine")
    @Operation(summary = "我的申请列表")
    public ApiResponse<Page<Map<String, Object>>> mine(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        var result = applicationService.mine(userId, page, size);
        return ApiResponse.ok(result, page, size, result.getTotal());
    }

    @PostMapping("/{applicationId}/cancel")
    @Operation(summary = "撤回待审申请")
    public ApiResponse<Boolean> cancel(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable("applicationId") Long applicationId) {
        return ApiResponse.ok(applicationService.cancel(userId, applicationId, idempotencyKey));
    }
}
