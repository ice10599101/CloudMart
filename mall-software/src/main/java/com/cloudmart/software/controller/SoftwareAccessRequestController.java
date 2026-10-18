package com.cloudmart.software.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.software.service.SoftwareAccessRequestService;
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
 * 受限来源访问申请（SF04 用户侧）：申请/我的申请/撤回。
 * 身份一律取网关注入的用户头；证据与凭据不在用户视图返回。
 */
@RestController
@RequestMapping("/software/access-requests")
@RequiredArgsConstructor
@Tag(name = "访问申请", description = "受限来源访问申请闭环（SF04）")
public class SoftwareAccessRequestController {

    private final SoftwareAccessRequestService accessRequestService;

    public record AccessRequestSubmitRequest(String message, String evidenceAssetIds) {
    }

    @PostMapping("/{softwareId}")
    @Operation(summary = "提交访问申请", description = "软件须为 ADMIN_PAID 访问模式；每用户每软件一条待审")
    public ApiResponse<Map<String, Object>> submit(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable("softwareId") Long softwareId,
            @RequestBody AccessRequestSubmitRequest request) {
        return ApiResponse.ok(accessRequestService.submit(userId, softwareId, idempotencyKey,
                request.message(), request.evidenceAssetIds()));
    }

    @GetMapping("/mine")
    @Operation(summary = "我的申请列表")
    public ApiResponse<Page<Map<String, Object>>> mine(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        var result = accessRequestService.mine(userId, page, size);
        return ApiResponse.ok(result, page, size, result.getTotal());
    }

    @PostMapping("/{requestId}/cancel")
    @Operation(summary = "撤回待审申请")
    public ApiResponse<Boolean> cancel(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable("requestId") Long requestId) {
        return ApiResponse.ok(accessRequestService.cancel(userId, requestId, idempotencyKey));
    }
}
