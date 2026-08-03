package com.cloudmart.seckill.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.seckill.converter.SeckillConverter;
import com.cloudmart.seckill.dto.AddSeckillProductRequest;
import com.cloudmart.seckill.dto.SeckillProductDTO;
import com.cloudmart.seckill.entity.SeckillProduct;
import com.cloudmart.seckill.repository.SeckillProductMapper;
import com.cloudmart.seckill.service.SeckillProductService;
import com.cloudmart.seckill.vo.SeckillProductVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/seckill/products")
@Tag(name = "秒杀商品管理(后台)", description = "管理后台秒杀商品管理接口，仅供内部服务调用")
@RequiredArgsConstructor
public class AdminSeckillProductController {

    private final SeckillProductService productService;
    private final SeckillConverter seckillConverter;
    private final SeckillProductMapper productMapper;

    @GetMapping("/activity/{activityId}")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "查询活动下的秒杀商品", description = "管理后台根据活动ID查询秒杀商品列表")
    public ApiResponse<List<SeckillProductVO>> listProductsByActivity(
            @Parameter(description = "活动ID") @PathVariable("activityId") Long activityId) {
        List<SeckillProductDTO> dtos = productService.listProductsByActivity(activityId);
        return ApiResponse.ok(seckillConverter.productDtoListToVOList(dtos));
    }

    @GetMapping("/{productId}")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "查询秒杀商品详情", description = "管理后台查询秒杀商品详情")
    public ApiResponse<SeckillProductVO> getProduct(
            @Parameter(description = "秒杀商品ID") @PathVariable("productId") Long productId) {
        SeckillProductDTO dto = productService.getProduct(productId);
        return ApiResponse.ok(seckillConverter.productDtoToVO(dto));
    }

    @PostMapping("/{activityId}")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "添加秒杀商品", description = "管理后台为指定活动添加秒杀商品")
    public ApiResponse<SeckillProductVO> addProduct(
            @Parameter(description = "活动ID") @PathVariable("activityId") Long activityId,
            @Valid @RequestBody AddSeckillProductRequest request) {
        SeckillProductDTO dto = productService.addProduct(activityId, request);
        return ApiResponse.ok(seckillConverter.productDtoToVO(dto));
    }

    @DeleteMapping("/{productId}")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "删除秒杀商品", description = "管理后台删除秒杀商品")
    public ApiResponse<Void> deleteProduct(
            @Parameter(description = "秒杀商品ID") @PathVariable("productId") Long productId) {
        SeckillProduct entity = productMapper.selectById(productId);
        if (entity == null) {
            throw new BusinessException("PRODUCT_NOT_FOUND", "秒杀商品不存在");
        }
        productMapper.deleteById(productId);
        return ApiResponse.ok(null);
    }
}
