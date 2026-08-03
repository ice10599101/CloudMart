package com.cloudmart.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 商品搜索结果 VO：包含商品列表、品牌与分类聚合分面、分页信息。
 *
 * <p>前端可根据 brands / categories 渲染侧边栏筛选条件，
 * 点击后回传对应参数到搜索接口。
 */
@Schema(description = "商品搜索结果")
public record ProductSearchResultVO(
    @Schema(description = "商品列表") List<ProductVO> products,
    @Schema(description = "品牌聚合分面") List<BrandBucket> brands,
    @Schema(description = "分类聚合分面") List<CategoryBucket> categories,
    @Schema(description = "命中总数") long total,
    @Schema(description = "当前页码") int page,
    @Schema(description = "每页大小") int size
) {

    @Schema(description = "品牌聚合桶")
    public record BrandBucket(
        @Schema(description = "品牌名称") String brand,
        @Schema(description = "命中商品数") long count
    ) {}

    @Schema(description = "分类聚合桶")
    public record CategoryBucket(
        @Schema(description = "分类ID") Long categoryId,
        @Schema(description = "命中商品数") long count
    ) {}
}
