package com.cloudmart.seckill.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.seckill.converter.SeckillConverter;
import com.cloudmart.seckill.dto.SeckillExecuteRequest;
import com.cloudmart.seckill.dto.SeckillResultDTO;
import com.cloudmart.seckill.service.SeckillExecuteService;
import com.cloudmart.seckill.vo.SeckillResultVO;
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
    @Operation(summary = "执行秒杀", description = "用户执行秒杀抢购")
    public ApiResponse<SeckillResultVO> executeSeckill(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Valid @RequestBody SeckillExecuteRequest request) {
        SeckillResultDTO dto = seckillExecuteService.executeSeckill(userId, request);
        return ApiResponse.ok(seckillConverter.resultDtoToVO(dto));
    }

    @GetMapping("/result")
    @Operation(summary = "查询秒杀结果", description = "查询用户秒杀抢购结果")
    public ApiResponse<SeckillResultVO> getSeckillResult(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "活动ID") @RequestParam Long activityId,
            @Parameter(description = "秒杀商品ID") @RequestParam Long seckillProductId) {
        SeckillResultDTO dto = seckillExecuteService.getSeckillResult(userId, activityId, seckillProductId);
        return ApiResponse.ok(seckillConverter.resultDtoToVO(dto));
    }
}
