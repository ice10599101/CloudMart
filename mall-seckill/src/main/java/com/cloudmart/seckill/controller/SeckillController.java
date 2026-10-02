package com.cloudmart.seckill.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.seckill.converter.SeckillConverter;
import com.cloudmart.seckill.dto.SeckillExecuteRequest;
import com.cloudmart.seckill.dto.SeckillResultDTO;
import com.cloudmart.seckill.service.SeckillExecuteService;
import com.cloudmart.seckill.vo.SeckillResultVO;
import org.springframework.security.access.prepost.PreAuthorize;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "秒杀执行", description = "秒杀抢购和结果查询")
public class SeckillController {

    private final SeckillExecuteService seckillExecuteService;
    private final SeckillConverter seckillConverter;

    public SeckillController(SeckillExecuteService seckillExecuteService, SeckillConverter seckillConverter) {
        this.seckillExecuteService = seckillExecuteService;
        this.seckillConverter = seckillConverter;
    }

    @PostMapping("/execute")
    @PreAuthorize("hasAnyRole('USER','INTERNAL')")
    @Operation(summary = "执行秒杀", description = "用户执行秒杀抢购（用户 JWT 或 mall-live 服务令牌）")
    public ApiResponse<SeckillResultVO> executeSeckill(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @RequestBody SeckillExecuteRequest request) {
        SeckillResultDTO dto = seckillExecuteService.executeSeckill(userId, request);
        return ApiResponse.ok(seckillConverter.resultDtoToVO(dto));
    }

    @GetMapping("/result")
    @Operation(summary = "查询秒杀结果", description = "按用户+活动+商品查询秒杀结果（DB 事实，Redis 投影可重建）")
    public ApiResponse<SeckillResultVO> getSeckillResult(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "活动ID") @RequestParam Long activityId,
            @Parameter(description = "秒杀商品ID") @RequestParam Long seckillProductId) {
        SeckillResultDTO dto = seckillExecuteService.getSeckillResult(userId, activityId, seckillProductId);
        return ApiResponse.ok(seckillConverter.resultDtoToVO(dto));
    }

    @GetMapping("/requests/{requestId}")
    @Operation(summary = "按请求ID查询秒杀结果", description = "T09：执行秒杀返回的 requestId 与 statusUrl 指向本端点；"
            + "刷新/轮询继续查原请求，归属校验只能查本人请求")
    public ApiResponse<SeckillResultVO> getSeckillResultByRequest(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "请求ID", required = true) @PathVariable("requestId") String requestId) {
        SeckillResultDTO dto = seckillExecuteService.getSeckillResultByRequest(userId, requestId);
        return ApiResponse.ok(seckillConverter.resultDtoToVO(dto));
    }
}
