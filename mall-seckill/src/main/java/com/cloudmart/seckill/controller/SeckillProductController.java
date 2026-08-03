package com.cloudmart.seckill.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.seckill.converter.SeckillConverter;
import com.cloudmart.seckill.dto.AddSeckillProductRequest;
import com.cloudmart.seckill.dto.SeckillProductDTO;
import com.cloudmart.seckill.service.SeckillProductService;
import com.cloudmart.seckill.vo.SeckillProductVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/products")
@Tag(name = "秒杀商品管理", description = "秒杀商品的配置和管理")
public class SeckillProductController {

    private final SeckillProductService productService;
    private final SeckillConverter seckillConverter;

    public SeckillProductController(SeckillProductService productService, SeckillConverter seckillConverter) {
        this.productService = productService;
        this.seckillConverter = seckillConverter;
    }

    @PostMapping("/{activityId}")
    @Operation(summary = "添加秒杀商品", description = "为指定活动添加秒杀商品")
    public ApiResponse<SeckillProductVO> addProduct(
            @Parameter(description = "活动ID") @PathVariable("activityId") Long activityId,
            @Valid @RequestBody AddSeckillProductRequest request) {
        SeckillProductDTO dto = productService.addProduct(activityId, request);
        return ApiResponse.ok(seckillConverter.productDtoToVO(dto));
    }

    @GetMapping("/activity/{activityId}")
    @Operation(summary = "查询活动下的秒杀商品", description = "根据活动ID查询秒杀商品列表")
    public ApiResponse<List<SeckillProductVO>> listProductsByActivity(
            @Parameter(description = "活动ID") @PathVariable("activityId") Long activityId) {
        List<SeckillProductDTO> dtos = productService.listProductsByActivity(activityId);
        return ApiResponse.ok(seckillConverter.productDtoListToVOList(dtos));
    }

    @GetMapping("/{productId}")
    @Operation(summary = "查询秒杀商品详情", description = "根据商品ID查询秒杀商品详情")
    public ApiResponse<SeckillProductVO> getProduct(
            @Parameter(description = "秒杀商品ID") @PathVariable("productId") Long productId) {
        SeckillProductDTO dto = productService.getProduct(productId);
        return ApiResponse.ok(seckillConverter.productDtoToVO(dto));
    }
}
