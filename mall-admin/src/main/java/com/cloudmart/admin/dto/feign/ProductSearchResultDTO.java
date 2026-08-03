package com.cloudmart.admin.dto.feign;

import java.util.List;

/**
 * 商品搜索结果 DTO，与 mall-product 的 ProductSearchResultVO 结构对齐。
 *
 * <p>包含商品列表、品牌与分类聚合分面、分页信息。
 */
public record ProductSearchResultDTO(
    List<ProductDTO> products,
    List<BrandBucket> brands,
    List<CategoryBucket> categories,
    long total,
    int page,
    int size
) {

    /** 品牌聚合桶 */
    public record BrandBucket(
        String brand,
        long count
    ) {}

    /** 分类聚合桶 */
    public record CategoryBucket(
        Long categoryId,
        long count
    ) {}
}
