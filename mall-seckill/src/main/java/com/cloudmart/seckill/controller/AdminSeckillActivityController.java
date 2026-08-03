package com.cloudmart.seckill.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.seckill.converter.SeckillConverter;
import com.cloudmart.seckill.dto.CreateActivityRequest;
import com.cloudmart.seckill.dto.SeckillActivityDTO;
import com.cloudmart.seckill.entity.SeckillActivity;
import com.cloudmart.seckill.repository.SeckillActivityMapper;
import com.cloudmart.seckill.service.SeckillActivityService;
import com.cloudmart.seckill.vo.SeckillActivityVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/seckill/activities")
@Tag(name = "秒杀活动管理(后台)", description = "管理后台秒杀活动管理接口，仅供内部服务调用")
@RequiredArgsConstructor
public class AdminSeckillActivityController {

    private final SeckillActivityService activityService;
    private final SeckillConverter seckillConverter;
    private final SeckillActivityMapper activityMapper;

    @GetMapping
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "查询秒杀活动列表", description = "管理后台查询秒杀活动列表")
    public ApiResponse<List<SeckillActivityVO>> listActivities(
            @Parameter(description = "活动状态") @RequestParam(required = false) String status) {
        List<SeckillActivityDTO> dtos = activityService.listActivities(status);
        return ApiResponse.ok(seckillConverter.activityDtoListToVOList(dtos));
    }

    @GetMapping("/{activityId}")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "查询秒杀活动详情", description = "管理后台查询秒杀活动详情")
    public ApiResponse<SeckillActivityVO> getActivity(
            @Parameter(description = "活动ID") @PathVariable("activityId") Long activityId) {
        SeckillActivityDTO dto = activityService.getActivity(activityId);
        return ApiResponse.ok(seckillConverter.activityDtoToVO(dto));
    }

    @PostMapping
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "创建秒杀活动", description = "管理后台创建秒杀活动")
    public ApiResponse<SeckillActivityVO> createActivity(@Valid @RequestBody CreateActivityRequest request) {
        SeckillActivityDTO dto = activityService.createActivity(request);
        return ApiResponse.ok(seckillConverter.activityDtoToVO(dto));
    }

    @PutMapping("/{activityId}/status")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "更新活动状态", description = "管理后台更新秒杀活动状态")
    public ApiResponse<SeckillActivityVO> updateActivityStatus(
            @Parameter(description = "活动ID") @PathVariable("activityId") Long activityId,
            @Parameter(description = "目标状态") @RequestParam String status) {
        SeckillActivityDTO dto = activityService.updateActivityStatus(activityId, status);
        return ApiResponse.ok(seckillConverter.activityDtoToVO(dto));
    }

    @PutMapping("/{activityId}")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "更新秒杀活动", description = "管理后台更新秒杀活动信息")
    public ApiResponse<SeckillActivityVO> updateActivity(
            @Parameter(description = "活动ID") @PathVariable("activityId") Long activityId,
            @RequestBody Map<String, Object> body) {
        SeckillActivity entity = activityMapper.selectById(activityId);
        if (entity == null) {
            throw new BusinessException("ACTIVITY_NOT_FOUND", "活动不存在");
        }
        if (body.containsKey("name")) {
            entity.setName((String) body.get("name"));
        }
        if (body.containsKey("description")) {
            entity.setDescription((String) body.get("description"));
        }
        if (body.containsKey("startTime")) {
            entity.setStartTime(LocalDateTime.parse(body.get("startTime").toString()));
        }
        if (body.containsKey("endTime")) {
            entity.setEndTime(LocalDateTime.parse(body.get("endTime").toString()));
        }
        if (body.containsKey("status")) {
            entity.setStatus((String) body.get("status"));
        }
        activityMapper.updateById(entity);
        return ApiResponse.ok(seckillConverter.toActivityVO(entity));
    }

    @DeleteMapping("/{activityId}")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "删除秒杀活动", description = "管理后台删除秒杀活动")
    public ApiResponse<Void> deleteActivity(
            @Parameter(description = "活动ID") @PathVariable("activityId") Long activityId) {
        SeckillActivity entity = activityMapper.selectById(activityId);
        if (entity == null) {
            throw new BusinessException("ACTIVITY_NOT_FOUND", "活动不存在");
        }
        activityMapper.deleteById(activityId);
        return ApiResponse.ok(null);
    }
}
