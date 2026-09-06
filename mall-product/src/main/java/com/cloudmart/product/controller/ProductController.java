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
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/products")
@Tag(name = "商品管理", description = "商品CRUD与搜索接口")
public class ProductController {

    private final ProductService productService;
    private final ProductConverter productConverter;

    public ProductController(ProductService productService, ProductConverter productConverter) {
        this.productService = productService;
        this.productConverter = productConverter;
    }

    @PostMapping
    @Operation(summary = "创建商品", description = "创建商品及其SKU列表")
    public ApiResponse<ProductVO> createProduct(
            @Parameter(description = "创建商品请求") @Valid @RequestBody CreateProductRequest request) {
        ProductDTO dto = productService.createProduct(request);
        return ApiResponse.ok(productConverter.productDtoToVO(dto));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询商品", description = "根据商品ID查询商品详情（含SKU）")
    public ApiResponse<ProductVO> getProductById(
            @Parameter(description = "商品ID", required = true) @PathVariable("id") Long id) {
        ProductDTO dto = productService.getProductById(id);
        return ApiResponse.ok(productConverter.productDetailToVO(dto));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新商品", description = "更新商品基本信息")
    public ApiResponse<ProductVO> updateProduct(
            @Parameter(description = "商品ID", required = true) @PathVariable("id") Long id,
            @Parameter(description = "更新商品请求") @Valid @RequestBody UpdateProductRequest request) {
        ProductDTO dto = productService.updateProduct(id, request);
        return ApiResponse.ok(productConverter.productDtoToVO(dto));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除商品", description = "软删除商品")
    public ApiResponse<Void> deleteProduct(
            @Parameter(description = "商品ID", required = true) @PathVariable("id") Long id) {
        productService.deleteProduct(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/search")
    @Operation(summary = "搜索商品", description = "支持关键词、分类、品牌、价格区间、排序搜索，返回聚合分面")
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
}
