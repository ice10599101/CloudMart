package com.cloudmart.community.controller;

import com.cloudmart.community.service.UserBlockService;
import com.cloudmart.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 内部拉黑状态查询（P2-24 私信链路）。
 *
 * <p>mall-notification 发送私信前经 Feign 校验双方拉黑关系；
 * hasRole('INTERNAL') 由网关 X-Internal-Call 头授予（对齐 InternalGrowthController）。</p>
 */
@RestController
@RequestMapping("/internal/blocks")
@PreAuthorize("hasRole('INTERNAL')")
@RequiredArgsConstructor
@Tag(name = "内部-拉黑状态", description = "私信发送前的双向拉黑校验")
public class InternalBlockController {

    private final UserBlockService userBlockService;

    @GetMapping("/status")
    @Operation(summary = "双向拉黑状态", description = "sender 与 peer 任一方向拉黑即 blocked=true")
    public ApiResponse<Map<String, Object>> status(
            @Parameter(description = "发送者用户 ID", required = true) @RequestParam Long userId,
            @Parameter(description = "会话对方用户 ID", required = true) @RequestParam Long peerUserId) {
        boolean blocked = userBlockService.getBlockedOrBlockerIds(userId).contains(peerUserId);
        return ApiResponse.ok(Map.of("blocked", blocked));
    }
}
