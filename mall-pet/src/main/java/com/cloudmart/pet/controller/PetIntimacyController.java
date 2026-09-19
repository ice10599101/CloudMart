package com.cloudmart.pet.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.pet.dto.CompanionHeartbeatRequest;
import com.cloudmart.pet.service.PetIntimacyService;
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
 * 亲密度与陪伴接口（三期）。
 *
 * <p>陪伴时长由前端按间隔上报（{@code /companion/heartbeat}），服务端按日封顶并换算亲密度；
 * 亲密度等级提供经验加成（{@code PetStateService.grantExp} 统一生效）。</p>
 */
@RestController
@RequestMapping
@Tag(name = "宠物亲密度", description = "亲密度概览/陪伴心跳")
@RequiredArgsConstructor
public class PetIntimacyController {

    private final PetIntimacyService intimacyService;

    @GetMapping("/intimacy")
    @Operation(summary = "亲密度与陪伴", description = "等级/进度/经验加成/陪伴时长与连续天数/等级阶梯")
    @SentinelResource("PET_QUERY")
    public ApiResponse<PetIntimacyVO> intimacy(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(intimacyService.overview(userId));
    }

    @PostMapping("/companion/heartbeat")
    @Operation(summary = "陪伴心跳", description = "上报陪伴秒数（1-600）；日上限内换算亲密度，超出不计（防挂机）")
    @SentinelResource("PET_COMPANION")
    public ApiResponse<PetIntimacyVO> heartbeat(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @RequestBody CompanionHeartbeatRequest request) {
        intimacyService.heartbeat(userId, request.seconds());
        return ApiResponse.ok(intimacyService.overview(userId));
    }
}
