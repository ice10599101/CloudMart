package com.cloudmart.seckill.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.seckill.converter.SeckillConverter;
import com.cloudmart.seckill.dto.CreateActivityRequest;
import com.cloudmart.seckill.dto.SeckillActivityDTO;
import com.cloudmart.seckill.service.SeckillActivityService;
import com.cloudmart.seckill.vo.SeckillActivityVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/activities")
@Tag(name = "秒杀活动管理", description = "秒杀活动的创建和管理")
public class SeckillActivityController {

    private final SeckillActivityService activityService;
    private final SeckillConverter seckillConverter;

    public SeckillActivityController(SeckillActivityService activityService, SeckillConverter seckillConverter) {
        this.activityService = activityService;
        this.seckillConverter = seckillConverter;
    }

    @PostMapping
    @Operation(summary = "创建秒杀活动", description = "管理员创建秒杀活动")
    public ApiResponse<SeckillActivityVO> createActivity(@Valid @RequestBody CreateActivityRequest request) {
        SeckillActivityDTO dto = activityService.createActivity(request);
        return ApiResponse.ok(seckillConverter.activityDtoToVO(dto));
    }

    @GetMapping
    @Operation(summary = "查询秒杀活动列表", description = "根据状态查询秒杀活动列表")
    public ApiResponse<List<SeckillActivityVO>> listActivities(
            @Parameter(description = "活动状态") @RequestParam(required = false) String status) {
        List<SeckillActivityDTO> dtos = activityService.listActivities(status);
        return ApiResponse.ok(seckillConverter.activityDtoListToVOList(dtos));
    }

    @GetMapping("/{activityId}")
    @Operation(summary = "查询秒杀活动详情", description = "根据活动ID查询秒杀活动详情")
    public ApiResponse<SeckillActivityVO> getActivity(
            @Parameter(description = "活动ID") @PathVariable("activityId") Long activityId) {
        SeckillActivityDTO dto = activityService.getActivity(activityId);
        return ApiResponse.ok(seckillConverter.activityDtoToVO(dto));
    }

    @PutMapping("/{activityId}/status")
    @Operation(summary = "更新活动状态", description = "手动更新秒杀活动状态")
    public ApiResponse<SeckillActivityVO> updateActivityStatus(
            @Parameter(description = "活动ID") @PathVariable("activityId") Long activityId,
            @Parameter(description = "目标状态") @RequestParam String status) {
        SeckillActivityDTO dto = activityService.updateActivityStatus(activityId, status);
        return ApiResponse.ok(seckillConverter.activityDtoToVO(dto));
    }
}
