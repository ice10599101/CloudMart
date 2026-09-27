package com.cloudmart.seckill.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.seckill.converter.SeckillConverter;
import com.cloudmart.seckill.dto.SeckillProductDTO;
import com.cloudmart.seckill.service.SeckillProductService;
import com.cloudmart.seckill.vo.SeckillProductVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 秒杀商品公开查询接口（SEC-01）：仅保留匿名可浏览的活动商品列表/详情。
 * 秒杀商品配置统一走 {@link AdminSeckillProductController}
 * （mall-admin 经服务令牌调用）；原公开写入口已随 SEC-01 移除。
 */
@RestController
@RequestMapping("/products")
@Tag(name = "秒杀商品查询", description = "秒杀商品浏览接口")
public class SeckillProductController {

    private final SeckillProductService productService;
    private final SeckillConverter seckillConverter;

    public SeckillProductController(SeckillProductService productService, SeckillConverter seckillConverter) {
        this.productService = productService;
        this.seckillConverter = seckillConverter;
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
