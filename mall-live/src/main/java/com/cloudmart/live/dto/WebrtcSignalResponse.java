package com.cloudmart.live.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * WebRTC 信令响应
 */
@Schema(description = "WebRTC 信令响应")
public record WebrtcSignalResponse(
    @Schema(description = "信令类型: OFFER/ANSWER/ICE_CANDIDATE")
    String type,
    @Schema(description = "SDP 描述或 ICE 候选者 JSON")
    String payload,
    @Schema(description = "发送者角色")
    String role
) {}
