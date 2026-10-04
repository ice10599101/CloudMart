package com.cloudmart.live.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.live.dto.WebrtcSignalRequest;
import com.cloudmart.live.dto.WebrtcSignalResponse;
import com.cloudmart.live.service.WebrtcService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WebRTC 信令（T08）：房间级授权。所有信令操作以 {@code ticket} 为唯一身份源，
 * 角色由服务端按房间 anchorUserId 派生——客户端自报 role 不再被信任。
 */
@Tag(name = "WebRTC信令", description = "直播 WebRTC 推流/拉流信令交换（T08 票据鉴权）")
@RestController
@RequestMapping("/webrtc")
@RequiredArgsConstructor
@Validated
public class WebrtcController {

    private final WebrtcService webrtcService;

    public record IssueTicketRequest(@NotNull Long roomId) {
    }

    @Operation(summary = "签发信令票据", description = "已登录用户为指定直播间换取 60s 滑动会话票据；"
            + "角色服务端派生（房主=HOST，其余=VIEWER），票据绑定 peerSessionId 实现观众会话隔离")
    @PostMapping("/tickets")
    public ApiResponse<Map<String, Object>> issueTicket(
            @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @RequestBody IssueTicketRequest request) {
        var ticket = webrtcService.issueSignalTicket(userId, request.roomId());
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("ticket", ticket.ticket());
        view.put("role", ticket.role());
        view.put("peerSessionId", ticket.peerSessionId());
        view.put("expiresAt", ticket.expiresAt().toString());
        return ApiResponse.ok(view);
    }

    @Operation(summary = "发布信令", description = "主播发布 SDP OFFER（新会话整键替换旧 OFFER）；"
            + "观众提交 SDP ANSWER（写入自身会话键，其他观众不可见）")
    @PostMapping("/signal")
    public ApiResponse<Void> publishSignal(@Valid @RequestBody WebrtcSignalRequest request) {
        webrtcService.publishSignal(request);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "获取信令", description = "targetRole=HOST 读主播共享 OFFER；"
            + "targetRole=VIEWER 主播聚合全部观众会话，观众仅能读自身会话")
    @GetMapping("/signal/{roomId}/{targetRole}")
    public ApiResponse<List<WebrtcSignalResponse>> getSignals(
            @Parameter(description = "直播间ID") @PathVariable Long roomId,
            @Parameter(description = "目标角色: HOST/VIEWER") @PathVariable String targetRole,
            @Parameter(description = "信令票据") @RequestParam String ticket) {
        return ApiResponse.ok(webrtcService.getSignals(roomId, targetRole, ticket));
    }

    @Operation(summary = "发布 ICE 候选者", description = "主播候选写共享键；观众候选写自身会话键")
    @PostMapping("/ice")
    public ApiResponse<Void> publishIceCandidate(@Valid @RequestBody WebrtcSignalRequest request) {
        webrtcService.publishIceCandidate(request);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "获取 ICE 候选者", description = "授权规则同信令获取：观众仅能读自身会话候选")
    @GetMapping("/ice/{roomId}/{targetRole}")
    public ApiResponse<List<String>> getIceCandidates(
            @Parameter(description = "直播间ID") @PathVariable Long roomId,
            @Parameter(description = "目标角色: HOST/VIEWER") @PathVariable String targetRole,
            @Parameter(description = "信令票据") @RequestParam String ticket) {
        return ApiResponse.ok(webrtcService.getIceCandidates(roomId, targetRole, ticket));
    }

    @Operation(summary = "清除信令", description = "仅房主票据可清除直播间全部信令（直播结束/切换）")
    @DeleteMapping("/signal/{roomId}")
    public ApiResponse<Void> clearSignals(
            @Parameter(description = "直播间ID") @PathVariable Long roomId,
            @Parameter(description = "信令票据") @RequestParam String ticket) {
        webrtcService.clearSignals(roomId, ticket);
        return ApiResponse.ok(null);
    }
}
