package com.cloudmart.product.dto;

import com.cloudmart.product.dto.ProductDTO;

import java.util.List;
import java.util.Map;

/**
 * 商品搜索响应：包含搜索结果列表、聚合 facets 与分页元数据。
 *
 * <p>聚合 facets 用于前端侧边栏筛选展示（品牌、分类）。</p>
 *
 * @param products    商品列表
 * @param brands      品牌聚合结果（品牌名 → 商品数）
 * @param categories  分类聚合结果（分类ID → 商品数）
 * @param total       命中总数
 * @param page        当前页码
 * @param size        每页大小
 */
public record ProductSearchResponse(
    List<ProductDTO> products,
    List<BrandBucket> brands,
    List<CategoryBucket> categories,
    long total,
    int page,
    int size
) {
    /** 品牌聚合桶 */
    public record BrandBucket(String brand, long count) {}

    /** 分类聚合桶 */
    public record CategoryBucket(Long categoryId, long count) {}
}
