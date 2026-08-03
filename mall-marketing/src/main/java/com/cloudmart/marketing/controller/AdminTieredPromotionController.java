package com.cloudmart.marketing.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.marketing.converter.MarketingConverter;
import com.cloudmart.marketing.dto.CreateTieredPromotionRequest;
import com.cloudmart.marketing.dto.TieredPromotionDTO;
import com.cloudmart.marketing.entity.TieredPromotion;
import com.cloudmart.marketing.repository.TieredPromotionMapper;
import com.cloudmart.marketing.service.TieredPromotionService;
import com.cloudmart.marketing.vo.TieredPromotionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@Tag(name = "阶梯满减管理", description = "阶梯满减管理端接口")
@RestController
@RequestMapping("/admin/marketing/tiered")
public class AdminTieredPromotionController {

    private final TieredPromotionService tieredPromotionService;
    private final MarketingConverter marketingConverter;
    private final TieredPromotionMapper tieredPromotionMapper;

    public AdminTieredPromotionController(TieredPromotionService tieredPromotionService, MarketingConverter marketingConverter, TieredPromotionMapper tieredPromotionMapper) {
        this.tieredPromotionService = tieredPromotionService;
        this.marketingConverter = marketingConverter;
        this.tieredPromotionMapper = tieredPromotionMapper;
    }

    @Operation(summary = "创建阶梯满减活动")
    @PostMapping("/promotions")
    public ApiResponse<TieredPromotionVO> createPromotion(@Valid @RequestBody CreateTieredPromotionRequest request) {
        TieredPromotionDTO dto = tieredPromotionService.createPromotion(request);
        return ApiResponse.ok(marketingConverter.tieredPromotionDtoToVOWithRules(dto));
    }

    @Operation(summary = "启用满减活动")
    @PutMapping("/promotions/{id}/enable")
    public ApiResponse<TieredPromotionVO> enablePromotion(
            @Parameter(description = "活动ID") @PathVariable Long id) {
        TieredPromotionDTO dto = tieredPromotionService.enablePromotion(id);
        return ApiResponse.ok(marketingConverter.tieredPromotionDtoToVOWithRules(dto));
    }

    @Operation(summary = "停用满减活动")
    @PutMapping("/promotions/{id}/disable")
    public ApiResponse<TieredPromotionVO> disablePromotion(
            @Parameter(description = "活动ID") @PathVariable Long id) {
        TieredPromotionDTO dto = tieredPromotionService.disablePromotion(id);
        return ApiResponse.ok(marketingConverter.tieredPromotionDtoToVOWithRules(dto));
    }

    @Operation(summary = "查询满减活动列表")
    @GetMapping("/promotions")
    public ApiResponse<IPage<TieredPromotionVO>> listPromotions(
            @Parameter(description = "状态筛选") @RequestParam(required = false) String status,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int size) {
        IPage<TieredPromotionDTO> dtoPage = tieredPromotionService.listPromotions(status, page, size);
        IPage<TieredPromotionVO> voPage = dtoPage.convert(marketingConverter::tieredPromotionDtoToVOWithRules);
        return ApiResponse.ok(voPage);
    }

    @Operation(summary = "获取满减活动详情")
    @GetMapping("/promotions/{id}")
    public ApiResponse<TieredPromotionVO> getPromotion(
            @Parameter(description = "活动ID") @PathVariable Long id) {
        TieredPromotionDTO dto = tieredPromotionService.getPromotion(id);
        return ApiResponse.ok(marketingConverter.tieredPromotionDtoToVOWithRules(dto));
    }

    @Operation(summary = "更新阶梯满减活动")
    @PutMapping("/promotions/{id}")
    public ApiResponse<TieredPromotionVO> updatePromotion(
            @Parameter(description = "活动ID") @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        TieredPromotion entity = tieredPromotionMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("PROMOTION_NOT_FOUND", "满减活动不存在");
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
        tieredPromotionMapper.updateById(entity);
        return ApiResponse.ok(marketingConverter.toTieredPromotionVO(entity));
    }

    @Operation(summary = "删除阶梯满减活动")
    @DeleteMapping("/promotions/{id}")
    public ApiResponse<Void> deletePromotion(
            @Parameter(description = "活动ID") @PathVariable Long id) {
        TieredPromotion entity = tieredPromotionMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("PROMOTION_NOT_FOUND", "满减活动不存在");
        }
        tieredPromotionMapper.deleteById(id);
        return ApiResponse.ok(null);
    }
}
