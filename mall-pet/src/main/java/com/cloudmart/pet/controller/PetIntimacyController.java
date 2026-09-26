package com.cloudmart.pet.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.pet.dto.CompanionHeartbeatRequest;
import com.cloudmart.pet.service.PetIntimacyService;
import com.cloudmart.pet.vo.PetCompanionSessionVO;
import com.cloudmart.pet.vo.PetIntimacyVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 亲密度与陪伴接口（三期 + B05）。
 *
 * <p>陪伴计时以服务端会话为权威（B05）：首次心跳建立基准（本次不计时），之后按服务端
 * 时钟差累计有效时长，超过失效间隔不补计；客户端上报秒数仅作参考，不参与收益判定；
 * 多端共享一份有效时间；停止/失效只结算有效窗口。积分公式
 * {@code entitled = min(floor(todayAccepted/secondsPerPoint), cap)}、grant = entitled - granted。</p>
 */
@RestController
@RequestMapping
@Tag(name = "宠物亲密度", description = "亲密度概览/陪伴会话心跳与停止")
@RequiredArgsConstructor
public class PetIntimacyController {

    private final PetIntimacyService intimacyService;

    @GetMapping("/intimacy")
    @Operation(summary = "亲密度与陪伴", description = "等级/进度/经验加成/陪伴时长与连续天数/等级阶梯；今日值按业务日")
    @SentinelResource("PET_QUERY")
    public ApiResponse<PetIntimacyVO> intimacy(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(intimacyService.overview(userId));
    }

    @PostMapping("/companion/heartbeat")
    @Operation(summary = "陪伴心跳（B05）", description = "服务端会话计时：无有效会话时建立基准（本次不计时）；"
            + "携带会话内单调递增 seq 时重复请求幂等；返回会话与今日累计视图")
    @SentinelResource("PET_COMPANION")
    public ApiResponse<PetCompanionSessionVO> heartbeat(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @RequestBody CompanionHeartbeatRequest request) {
        return ApiResponse.ok(intimacyService.heartbeat(userId, request.seconds(), request.seq()));
    }

    @PostMapping("/companion/stop")
    @Operation(summary = "停止陪伴会话（B05）", description = "结算有效窗口内尚未计入的时间并结束会话（幂等）")
    @SentinelResource("PET_COMPANION")
    public ApiResponse<PetCompanionSessionVO> stop(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(intimacyService.stopSession(userId));
    }
}
