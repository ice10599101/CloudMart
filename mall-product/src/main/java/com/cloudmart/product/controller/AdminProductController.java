package com.cloudmart.product.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.api.ApiResponse.Meta;
import com.cloudmart.product.converter.ProductConverter;
import com.cloudmart.product.dto.CreateProductRequest;
import com.cloudmart.product.dto.ProductDTO;
import com.cloudmart.product.dto.ProductSearchRequest;
import com.cloudmart.product.dto.ProductSearchResponse;
import com.cloudmart.product.dto.UpdateProductRequest;
import com.cloudmart.product.service.ProductService;
import com.cloudmart.product.vo.ProductSearchResultVO;
import com.cloudmart.product.vo.ProductSearchResultVO.BrandBucket;
import com.cloudmart.product.vo.ProductSearchResultVO.CategoryBucket;
import com.cloudmart.product.vo.ProductVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/products")
@Tag(name = "商品管理(后台)", description = "管理后台商品管理接口，仅供内部服务调用")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductService productService;
    private final ProductConverter productConverter;

    @GetMapping("/count")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "商品总数", description = "返回商品总数")
    public ApiResponse<Map<String, Object>> getProductCount() {
        long count = productService.getProductCount();
        return ApiResponse.ok(Map.of("count", count));
    }

    @GetMapping("/search")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "搜索商品", description = "管理后台搜索商品，支持关键词、分类、品牌、价格区间，返回聚合分面")
    public ApiResponse<ProductSearchResultVO> searchProducts(
            @Parameter(description = "商品搜索请求") @Valid ProductSearchRequest request) {
        ProductSearchResponse response = productService.searchProducts(request);
        List<ProductVO> productVOs = productConverter.productDtoListToVOList(response.products());
        List<BrandBucket> brandBuckets = response.brands().stream()
                .map(b -> new BrandBucket(b.brand(), b.count()))
                .toList();
        List<CategoryBucket> categoryBuckets = response.categories().stream()
                .map(c -> new CategoryBucket(c.categoryId(), c.count()))
                .toList();
        ProductSearchResultVO result = new ProductSearchResultVO(
                productVOs, brandBuckets, categoryBuckets,
                response.total(), response.page(), response.size()
        );
        Meta meta = new Meta(response.page(), response.size(), response.total());
        return ApiResponse.ok(result, meta);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "查询商品详情", description = "管理后台查询商品详情")
    public ApiResponse<ProductVO> getProductById(
            @Parameter(description = "商品ID", required = true) @PathVariable("id") Long id) {
        ProductDTO dto = productService.getProductById(id);
        return ApiResponse.ok(productConverter.productDtoToVO(dto));
    }

    @PostMapping
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "创建商品", description = "管理后台创建商品")
    public ApiResponse<ProductVO> createProduct(
            @Parameter(description = "创建商品请求") @Valid @RequestBody CreateProductRequest request) {
        ProductDTO dto = productService.createProduct(request);
        return ApiResponse.ok(productConverter.productDtoToVO(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "更新商品", description = "管理后台更新商品信息")
    public ApiResponse<ProductVO> updateProduct(
            @Parameter(description = "商品ID", required = true) @PathVariable("id") Long id,
            @Parameter(description = "更新商品请求") @Valid @RequestBody UpdateProductRequest request) {
        ProductDTO dto = productService.updateProduct(id, request);
        return ApiResponse.ok(productConverter.productDtoToVO(dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "删除商品", description = "管理后台删除商品")
    public ApiResponse<Void> deleteProduct(
            @Parameter(description = "商品ID", required = true) @PathVariable("id") Long id) {
        productService.deleteProduct(id);
        return ApiResponse.ok(null);
    }
}
