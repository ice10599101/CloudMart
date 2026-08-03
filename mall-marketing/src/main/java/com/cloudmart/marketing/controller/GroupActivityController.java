package com.cloudmart.marketing.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.marketing.converter.MarketingConverter;
import com.cloudmart.marketing.dto.*;
import com.cloudmart.marketing.service.GroupActivityService;
import com.cloudmart.marketing.vo.GroupActivityVO;
import com.cloudmart.marketing.vo.GroupOrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@Tag(name = "拼团活动", description = "拼团活动用户端接口")
@RestController
@RequestMapping("/group")
public class GroupActivityController {

    private final GroupActivityService groupActivityService;
    private final MarketingConverter marketingConverter;

    public GroupActivityController(GroupActivityService groupActivityService, MarketingConverter marketingConverter) {
        this.groupActivityService = groupActivityService;
        this.marketingConverter = marketingConverter;
    }

    @Operation(summary = "获取拼团活动详情")
    @GetMapping("/activities/{id}")
    public ApiResponse<GroupActivityVO> getActivity(
            @Parameter(description = "活动ID") @PathVariable Long id) {
        GroupActivityDTO dto = groupActivityService.getActivity(id);
        return ApiResponse.ok(marketingConverter.groupActivityDtoToVO(dto));
    }

    @Operation(summary = "查询拼团活动列表")
    @GetMapping("/activities")
    public ApiResponse<IPage<GroupActivityVO>> listActivities(
            @Parameter(description = "状态筛选") @RequestParam(required = false) String status,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int size) {
        IPage<GroupActivityDTO> dtoPage = groupActivityService.listActivities(status, page, size);
        IPage<GroupActivityVO> voPage = dtoPage.convert(marketingConverter::groupActivityDtoToVO);
        return ApiResponse.ok(voPage);
    }

    @Operation(summary = "加入拼团")
    @PostMapping("/join")
    public ApiResponse<GroupOrderVO> joinGroup(
            @Parameter(description = "用户ID") @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody JoinGroupRequest request) {
        GroupOrderDTO dto = groupActivityService.joinGroup(userId, request);
        return ApiResponse.ok(marketingConverter.groupOrderDtoToVO(dto));
    }

    @Operation(summary = "查询拼团组详情")
    @GetMapping("/orders/{groupOrderId}")
    public ApiResponse<GroupOrderVO> getGroupOrder(
            @Parameter(description = "拼团组ID") @PathVariable Long groupOrderId) {
        GroupOrderDTO dto = groupActivityService.getGroupOrder(groupOrderId);
        return ApiResponse.ok(marketingConverter.groupOrderDtoToVO(dto));
    }

    @Operation(summary = "查询拼团组列表")
    @GetMapping("/orders")
    public ApiResponse<IPage<GroupOrderVO>> listGroupOrders(
            @Parameter(description = "活动ID") @RequestParam(required = false) Long activityId,
            @Parameter(description = "状态筛选") @RequestParam(required = false) String status,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int size) {
        IPage<GroupOrderDTO> dtoPage = groupActivityService.listGroupOrders(activityId, status, page, size);
        IPage<GroupOrderVO> voPage = dtoPage.convert(marketingConverter::groupOrderDtoToVO);
        return ApiResponse.ok(voPage);
    }
}
