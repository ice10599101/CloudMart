package com.cloudmart.live.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.live.service.LiveWsTicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 直播 WS 票据签发（LIVE-01）：已登录用户为指定房间换取 30 秒一次性 WS 票据。
 * 弹幕连接改为「票据握手认证」——服务端签发的身份（userId）绑定到会话，
 * 客户端载荷中的 userId/nickname 不再被信任。
 */
@RestController
@RequestMapping("/ws-tickets")
@RequiredArgsConstructor
@Validated
@Tag(name = "直播WS票据", description = "弹幕 WebSocket 一次性握手票据")
public class LiveWsTicketController {

    private final LiveWsTicketService liveWsTicketService;

    public record IssueRequest(@NotNull Long roomId, String nickname) {
    }

    @PostMapping
    @Operation(summary = "签发 WS 握手票据", description = "30 秒有效期、一次性、绑定房间；"
            + "响应含 ticket/expiresAt/wsPath（网关 WS 路由前缀 /ws/live/danmaku）")
    public ApiResponse<Map<String, Object>> issue(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @jakarta.validation.Valid @RequestBody IssueRequest request) {
        String nickname = sanitizeNickname(request.nickname(), userId);
        LiveWsTicketService.IssuedTicket ticket =
                liveWsTicketService.issue(userId, nickname, request.roomId());
        return ApiResponse.ok(Map.of(
                "ticket", ticket.ticket(),
                "expiresAt", ticket.expiresAt().toString(),
                "wsPath", ticket.wsPath()));
    }

    /** 昵称为展示数据：去标签、限长；缺省用「用户{id}」占位（身份以 userId 为准） */
    private String sanitizeNickname(String nickname, Long userId) {
        if (nickname == null || nickname.isBlank()) {
            return "用户" + userId;
        }
        String cleaned = nickname.replaceAll("<[^>]*>", "").replaceAll("[\\r\\n]", " ").trim();
        return cleaned.isEmpty() ? "用户" + userId
                : cleaned.substring(0, Math.min(cleaned.length(), 50));
    }
}
