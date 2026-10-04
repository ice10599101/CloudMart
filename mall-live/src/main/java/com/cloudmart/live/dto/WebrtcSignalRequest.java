package com.cloudmart.live.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * WebRTC 信令请求（T08）。
 *
 * <p>角色不再由客户端声明：请求携带信令票据 {@code ticket}，服务端从票据解析
 * 身份（userId/roomId/role/peerSessionId）——冒充 HOST、跨房间写信令、伪造他人
 * 会话均被拒绝。SDP/ICE 正文有大小与频率上限（服务端校验）。</p>
 */
@Schema(description = "WebRTC 信令请求（T08：票据鉴权，角色服务端派生）")
public record WebrtcSignalRequest(
    @Schema(description = "直播间ID") @NotNull Long roomId,
    @Schema(description = "信令票据（POST /webrtc/tickets 签发）") @NotBlank String ticket,
    @Schema(description = "信令类型: OFFER/ANSWER/ICE_CANDIDATE") @NotBlank String type,
    @Schema(description = "SDP 描述或 ICE 候选者 JSON") @NotBlank String payload
) {}
