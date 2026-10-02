package com.cloudmart.seckill.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.seckill.entity.SeckillRequest;
import com.cloudmart.seckill.repository.SeckillRequestMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 秒杀请求运营查询（T09）：排队中/终态失败的请求对运营可见——
 * 恢复任务的对账与释放动作全部留痕于请求行（fail_reason/send_attempts），
 * 死信场景（结果事件重试耗尽）下请求保持 PENDING，运营据此介入处置。
 */
@RestController
@RequestMapping("/admin/seckill/requests")
@Tag(name = "秒杀请求运营(后台)", description = "管理后台秒杀请求事实查询，仅供内部服务调用")
@RequiredArgsConstructor
public class AdminSeckillRequestController {

    private final SeckillRequestMapper requestMapper;

    @GetMapping
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "分页查询秒杀请求", description = "按状态/活动/用户筛选；PENDING 停留过久或 FAILED 集中"
            + "出现时需要运营关注（对应消息投递或结果回写异常）")
    public ApiResponse<Page<SeckillRequest>> page(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") long pageSize,
            @Parameter(description = "状态：PENDING/SUCCESS/FAILED") @RequestParam(required = false) String status,
            @Parameter(description = "活动ID") @RequestParam(required = false) Long activityId,
            @Parameter(description = "用户ID") @RequestParam(required = false) Long userId) {
        LambdaQueryWrapper<SeckillRequest> wrapper = new LambdaQueryWrapper<SeckillRequest>()
                .eq(status != null && !status.isBlank(), SeckillRequest::getStatus, status)
                .eq(activityId != null, SeckillRequest::getActivityId, activityId)
                .eq(userId != null, SeckillRequest::getUserId, userId)
                .orderByDesc(SeckillRequest::getId);
        Page<SeckillRequest> result = requestMapper.selectPage(new Page<>(page, pageSize), wrapper);
        return ApiResponse.ok(result, new ApiResponse.Meta((int) page, (int) pageSize, result.getTotal()));
    }
}
