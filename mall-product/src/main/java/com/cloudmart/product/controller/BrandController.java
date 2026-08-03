package com.cloudmart.product.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.product.converter.ProductConverter;
import com.cloudmart.product.dto.BrandDTO;
import com.cloudmart.product.dto.CreateBrandRequest;
import com.cloudmart.product.dto.UpdateBrandRequest;
import com.cloudmart.product.entity.Brand;
import com.cloudmart.product.repository.BrandMapper;
import com.cloudmart.product.service.BrandService;
import com.cloudmart.product.vo.BrandVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@Tag(name = "品牌管理", description = "品牌查询接口")
@RestController
@RequestMapping("/brands")
public class BrandController {

    private final BrandService brandService;
    private final ProductConverter productConverter;

    public BrandController(BrandService brandService, ProductConverter productConverter) {
        this.brandService = brandService;
        this.productConverter = productConverter;
    }

    @Operation(summary = "获取品牌详情")
    @GetMapping("/{id}")
    public ApiResponse<BrandVO> getBrand(@Parameter(description = "品牌ID") @PathVariable Long id) {
        BrandDTO dto = brandService.getBrand(id);
        return ApiResponse.ok(productConverter.brandDtoToVO(dto));
    }

    @Operation(summary = "查询品牌列表")
    @GetMapping
    public ApiResponse<IPage<BrandVO>> listBrands(
            @Parameter(description = "品牌名称模糊搜索") @RequestParam(required = false) String name,
            @Parameter(description = "状态") @RequestParam(required = false) Integer status,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int size) {
        IPage<BrandDTO> dtoPage = brandService.listBrands(name, status, page, size);
        IPage<BrandVO> voPage = dtoPage.convert(productConverter::brandDtoToVO);
        return ApiResponse.ok(voPage);
    }
}

@Tag(name = "品牌管理(Admin)", description = "品牌管理接口")
@RestController
@RequestMapping("/admin/brands")
class AdminBrandController {

    private final BrandService brandService;
    private final ProductConverter productConverter;
    private final BrandMapper brandMapper;

    public AdminBrandController(BrandService brandService, ProductConverter productConverter, BrandMapper brandMapper) {
        this.brandService = brandService;
        this.productConverter = productConverter;
        this.brandMapper = brandMapper;
    }

    @Operation(summary = "创建品牌")
    @PostMapping
    public ApiResponse<BrandVO> createBrand(@Valid @RequestBody CreateBrandRequest request) {
        BrandDTO dto = brandService.createBrand(request);
        return ApiResponse.ok(productConverter.brandDtoToVO(dto));
    }

    @Operation(summary = "更新品牌")
    @PutMapping("/{id}")
    public ApiResponse<BrandVO> updateBrand(
            @Parameter(description = "品牌ID") @PathVariable Long id,
            @Valid @RequestBody UpdateBrandRequest request) {
        BrandDTO dto = brandService.updateBrand(id, request);
        return ApiResponse.ok(productConverter.brandDtoToVO(dto));
    }

    @Operation(summary = "获取品牌详情")
    @GetMapping("/{id}")
    public ApiResponse<BrandVO> getBrand(@Parameter(description = "品牌ID") @PathVariable Long id) {
        BrandDTO dto = brandService.getBrand(id);
        return ApiResponse.ok(productConverter.brandDtoToVO(dto));
    }

    @Operation(summary = "查询品牌列表")
    @GetMapping
    public ApiResponse<IPage<BrandVO>> listBrands(
            @Parameter(description = "品牌名称模糊搜索") @RequestParam(required = false) String name,
            @Parameter(description = "状态") @RequestParam(required = false) Integer status,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int size) {
        IPage<BrandDTO> dtoPage = brandService.listBrands(name, status, page, size);
        IPage<BrandVO> voPage = dtoPage.convert(productConverter::brandDtoToVO);
        return ApiResponse.ok(voPage);
    }

    @Operation(summary = "删除品牌")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteBrand(@Parameter(description = "品牌ID") @PathVariable Long id) {
        Brand brand = brandMapper.selectById(id);
        if (brand == null) {
            throw new BusinessException("BRAND_NOT_FOUND", "品牌不存在");
        }
        brandMapper.deleteById(id);
        return ApiResponse.ok(null);
    }
}
