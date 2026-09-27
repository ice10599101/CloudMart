package com.cloudmart.wish.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.wish.service.WishService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 心愿延期 / 归档 / 取消归档（N03，/api/wish/v2/wishes/**）。
 * 全部作者权限 + version CAS；归档保存前状态，取消归档按前状态恢复且不重复计发布数。
 */
@RestController
@RequestMapping("/v2/wishes")
@RequiredArgsConstructor
@Tag(name = "心愿宇宙·生命周期", description = "延期/归档（N03）")
public class WishLifecycleController {

    private final WishService wishService;


    public record RescheduleRequest(Long version, LocalDateTime expectedAt,
                                    String expectedTimezone, String reason) {
    }

    public record ArchiveRequest(Long version, String reason) {
    }

    @PostMapping("/{id}/reschedule")
    @Operation(summary = "延期（N03）", description = "仅 ACTIVE/OVERDUE；新日期必须未来；version CAS")
    public ApiResponse<Void> reschedule(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("id") Long wishId,
            @RequestBody RescheduleRequest request) {
        wishService.reschedule(userId, wishId, request.expectedAt(),
                request.expectedTimezone(), request.version());
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/archive")
    @Operation(summary = "归档（N03）", description = "保存归档前状态；停止提醒与增长写入")
    public ApiResponse<Void> archive(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("id") Long wishId,
            @RequestBody ArchiveRequest request) {
        wishService.archive(userId, wishId, request.reason(), request.version());
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/unarchive")
    @Operation(summary = "取消归档（N03）", description = "按归档前状态恢复 ACTIVE/OVERDUE；不重复计发布数")
    public ApiResponse<Void> unarchive(
            @Parameter(description = "当前用户 ID（网关注入）", required = true)
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @PathVariable("id") Long wishId,
            @RequestBody Map<String, Long> body) {
        wishService.unarchive(userId, wishId, body == null ? null : body.get("version"));
        return ApiResponse.ok(null);
    }

}
