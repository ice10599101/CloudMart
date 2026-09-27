package com.cloudmart.product.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.api.ApiResponse.Meta;
import com.cloudmart.product.converter.ProductConverter;
import com.cloudmart.product.dto.ProductDTO;
import com.cloudmart.product.dto.ProductSearchRequest;
import com.cloudmart.product.dto.ProductSearchResponse;
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

/**
 * 商品公开查询接口（SEC-01）：仅保留用户端浏览/搜索能力。
 * 商品创建/更新/删除等管理动作统一走 {@link AdminProductController}
 * （mall-admin 经服务令牌调用），本控制器不再暴露任何写入口。
 */
@RestController
@RequestMapping("/products")
@Tag(name = "商品查询", description = "商品详情与搜索接口")
public class ProductController {

    private final ProductService productService;
    private final ProductConverter productConverter;

    public ProductController(ProductService productService, ProductConverter productConverter) {
        this.productService = productService;
        this.productConverter = productConverter;
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询商品", description = "根据商品ID查询商品详情（含SKU）")
    public ApiResponse<ProductVO> getProductById(
            @Parameter(description = "商品ID", required = true) @PathVariable("id") Long id) {
        ProductDTO dto = productService.getProductById(id);
        return ApiResponse.ok(productConverter.productDetailToVO(dto));
    }

    @GetMapping("/skus/batch")
    @Operation(summary = "批量查询SKU商品信息", description = "按 SKU ID 批量返回商品名称/图片，供秒杀等跨服务 enrich 使用；须置于 /{id} 前声明以避免路径歧义")
    public ApiResponse<List<com.cloudmart.product.vo.SkuBatchItemVO>> getSkusBatch(
            @Parameter(description = "SKU ID 列表", required = true) @RequestParam("ids") List<Long> ids) {
        return ApiResponse.ok(productService.getSkuBatchInfo(ids));
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
