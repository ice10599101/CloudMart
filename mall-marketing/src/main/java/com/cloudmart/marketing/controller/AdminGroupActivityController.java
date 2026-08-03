package com.cloudmart.marketing.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.marketing.converter.MarketingConverter;
import com.cloudmart.marketing.dto.*;
import com.cloudmart.marketing.entity.GroupActivity;
import com.cloudmart.marketing.repository.GroupActivityMapper;
import com.cloudmart.marketing.service.GroupActivityService;
import com.cloudmart.marketing.vo.GroupActivityVO;
import com.cloudmart.marketing.vo.GroupOrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Tag(name = "拼团活动管理", description = "拼团活动管理端接口")
@RestController
@RequestMapping("/admin/marketing/group")
public class AdminGroupActivityController {

    private final GroupActivityService groupActivityService;
    private final MarketingConverter marketingConverter;
    private final GroupActivityMapper groupActivityMapper;

    public AdminGroupActivityController(GroupActivityService groupActivityService, MarketingConverter marketingConverter, GroupActivityMapper groupActivityMapper) {
        this.groupActivityService = groupActivityService;
        this.marketingConverter = marketingConverter;
        this.groupActivityMapper = groupActivityMapper;
    }

    @Operation(summary = "创建拼团活动")
    @PostMapping("/activities")
    public ApiResponse<GroupActivityVO> createActivity(@Valid @RequestBody CreateGroupActivityRequest request) {
        GroupActivityDTO dto = groupActivityService.createActivity(request);
        return ApiResponse.ok(marketingConverter.groupActivityDtoToVO(dto));
    }

    @Operation(summary = "启用拼团活动")
    @PutMapping("/activities/{id}/enable")
    public ApiResponse<GroupActivityVO> enableActivity(
            @Parameter(description = "活动ID") @PathVariable Long id) {
        GroupActivityDTO dto = groupActivityService.enableActivity(id);
        return ApiResponse.ok(marketingConverter.groupActivityDtoToVO(dto));
    }

    @Operation(summary = "停用拼团活动")
    @PutMapping("/activities/{id}/disable")
    public ApiResponse<GroupActivityVO> disableActivity(
            @Parameter(description = "活动ID") @PathVariable Long id) {
        GroupActivityDTO dto = groupActivityService.disableActivity(id);
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

    @Operation(summary = "更新拼团活动")
    @PutMapping("/activities/{id}")
    public ApiResponse<GroupActivityVO> updateActivity(
            @Parameter(description = "活动ID") @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        GroupActivity entity = groupActivityMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("ACTIVITY_NOT_FOUND", "拼团活动不存在");
        }
        if (body.containsKey("name")) {
            entity.setName((String) body.get("name"));
        }
        if (body.containsKey("description")) {
            entity.setDescription((String) body.get("description"));
        }
        if (body.containsKey("productId")) {
            entity.setProductId(((Number) body.get("productId")).longValue());
        }
        if (body.containsKey("skuId")) {
            entity.setSkuId(((Number) body.get("skuId")).longValue());
        }
        if (body.containsKey("originalPrice")) {
            entity.setOriginalPrice(new BigDecimal(body.get("originalPrice").toString()));
        }
        if (body.containsKey("groupPrice")) {
            entity.setGroupPrice(new BigDecimal(body.get("groupPrice").toString()));
        }
        if (body.containsKey("targetNumber")) {
            entity.setTargetNumber(((Number) body.get("targetNumber")).intValue());
        }
        if (body.containsKey("maxGroups")) {
            entity.setMaxGroups(((Number) body.get("maxGroups")).intValue());
        }
        if (body.containsKey("perUserLimit")) {
            entity.setPerUserLimit(((Number) body.get("perUserLimit")).intValue());
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
        groupActivityMapper.updateById(entity);
        return ApiResponse.ok(marketingConverter.toGroupActivityVO(entity));
    }

    @Operation(summary = "删除拼团活动")
    @DeleteMapping("/activities/{id}")
    public ApiResponse<Void> deleteActivity(
            @Parameter(description = "活动ID") @PathVariable Long id) {
        GroupActivity entity = groupActivityMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("ACTIVITY_NOT_FOUND", "拼团活动不存在");
        }
        groupActivityMapper.deleteById(id);
        return ApiResponse.ok(null);
    }
}
