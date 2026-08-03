package com.cloudmart.live.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * WebRTC 信令请求
 * 用于直播推流/拉流的 SDP 交换和 ICE 候选者传递
 */
@Schema(description = "WebRTC 信令请求")
public record WebrtcSignalRequest(
    @Schema(description = "直播间ID") @NotNull Long roomId,
    @Schema(description = "信令类型: OFFER/ANSWER/ICE_CANDIDATE") @NotBlank String type,
    @Schema(description = "SDP 描述或 ICE 候选者 JSON") @NotBlank String payload,
    @Schema(description = "用户角色: HOST/VIEWER") @NotBlank String role
) {}
