package com.cloudmart.seckill.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.seckill.converter.SeckillConverter;
import com.cloudmart.seckill.dto.SeckillActivityDTO;
import com.cloudmart.seckill.service.SeckillActivityService;
import com.cloudmart.seckill.vo.SeckillActivityVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 秒杀活动公开查询接口（SEC-01）：仅保留匿名可浏览的活动列表/详情。
 * 活动的创建与状态变更统一走 {@link AdminSeckillActivityController}
 * （mall-admin 经服务令牌调用）；原公开写入口已随 SEC-01 移除。
 */
@RestController
@RequestMapping("/activities")
@Tag(name = "秒杀活动查询", description = "秒杀活动浏览接口")
public class SeckillActivityController {

    private final SeckillActivityService activityService;
    private final SeckillConverter seckillConverter;

    public SeckillActivityController(SeckillActivityService activityService, SeckillConverter seckillConverter) {
        this.activityService = activityService;
        this.seckillConverter = seckillConverter;
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
}
