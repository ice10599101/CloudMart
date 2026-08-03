package com.cloudmart.live.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.live.dto.WebrtcSignalRequest;
import com.cloudmart.live.dto.WebrtcSignalResponse;
import com.cloudmart.live.service.WebrtcService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "WebRTC信令", description = "直播 WebRTC 推流/拉流信令交换")
@RestController
@RequestMapping("/webrtc")
public class WebrtcController {

    private final WebrtcService webrtcService;

    public WebrtcController(WebrtcService webrtcService) {
        this.webrtcService = webrtcService;
    }

    @Operation(summary = "发布信令", description = "主播发布 SDP Offer 或观众提交 SDP Answer")
    @PostMapping("/signal")
    public ApiResponse<Void> publishSignal(@RequestBody WebrtcSignalRequest request) {
        webrtcService.publishSignal(request);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "获取信令", description = "拉取指定直播间的 SDP Offer/Answer")
    @GetMapping("/signal/{roomId}/{role}")
    public ApiResponse<List<WebrtcSignalResponse>> getSignals(
            @Parameter(description = "直播间ID") @PathVariable Long roomId,
            @Parameter(description = "角色: HOST/VIEWER") @PathVariable String role) {
        return ApiResponse.ok(webrtcService.getSignals(roomId, role));
    }

    @Operation(summary = "发布 ICE 候选者", description = "传递 ICE 候选者信息")
    @PostMapping("/ice")
    public ApiResponse<Void> publishIceCandidate(@RequestBody WebrtcSignalRequest request) {
        webrtcService.publishIceCandidate(request);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "获取 ICE 候选者", description = "拉取指定角色的 ICE 候选者列表")
    @GetMapping("/ice/{roomId}/{role}")
    public ApiResponse<List<String>> getIceCandidates(
            @Parameter(description = "直播间ID") @PathVariable Long roomId,
            @Parameter(description = "角色: HOST/VIEWER") @PathVariable String role) {
        return ApiResponse.ok(webrtcService.getIceCandidates(roomId, role));
    }

    @Operation(summary = "清除信令", description = "直播结束或切换时清除信令缓存")
    @DeleteMapping("/signal/{roomId}")
    public ApiResponse<Void> clearSignals(
            @Parameter(description = "直播间ID") @PathVariable Long roomId) {
        webrtcService.clearSignals(roomId);
        return ApiResponse.ok(null);
    }
}
