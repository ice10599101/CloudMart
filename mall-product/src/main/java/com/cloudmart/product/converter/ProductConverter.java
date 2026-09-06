package com.cloudmart.product.converter;

import com.cloudmart.product.dto.CategoryDTO;
import com.cloudmart.product.dto.ProductDTO;
import com.cloudmart.product.dto.ReviewDTO;
import com.cloudmart.product.dto.ReviewStatsDTO;
import com.cloudmart.product.dto.SkuDTO;
import com.cloudmart.product.entity.Category;
import com.cloudmart.product.entity.Product;
import com.cloudmart.product.entity.ProductSku;
import com.cloudmart.product.vo.BrandVO;
import com.cloudmart.product.vo.CategoryVO;
import com.cloudmart.product.vo.ProductVO;
import com.cloudmart.product.vo.ReviewStatsVO;
import com.cloudmart.product.vo.ReviewVO;
import com.cloudmart.product.vo.SkuVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface ProductConverter {

    ProductDTO toDTO(Product product, List<ProductSku> skus, String categoryName);

    SkuDTO toSkuDTO(ProductSku sku);

    List<SkuDTO> toSkuDTOList(List<ProductSku> skus);

    CategoryDTO toCategoryDTO(Category category);

    List<CategoryDTO> toCategoryDTOList(List<Category> categories);

    ProductVO toProductVO(Product product);

    List<ProductVO> toProductVOList(List<Product> products);

    SkuVO toSkuVO(ProductSku sku);

    List<SkuVO> toSkuVOList(List<ProductSku> skus);

    CategoryVO toCategoryVO(Category category);

    List<CategoryVO> toCategoryVOList(List<Category> categories);

    BrandVO toBrandVO(com.cloudmart.product.entity.Brand brand);

    List<BrandVO> toBrandVOList(List<com.cloudmart.product.entity.Brand> brands);

    @Mapping(source = "brand", target = "brandName")
    @Mapping(target = "price", ignore = true)
    @Mapping(target = "originalPrice", ignore = true)
    @Mapping(target = "sales", ignore = true)
    ProductVO productDtoToVO(ProductDTO dto);

    /**
     * 商品详情 VO：携带 SKU 列表，并从 SKU 汇总价格/原价/库存，
     * 保证无 SKU 选中时详情接口也返回可用的兜底价格。
     */
    default ProductVO productDetailToVO(ProductDTO dto) {
        ProductVO base = productDtoToVO(dto);
        List<SkuDTO> skus = dto.skus();
        if (skus == null || skus.isEmpty()) {
            return new ProductVO(base.id(), base.name(), base.mainImage(),
                    base.price(), base.originalPrice(), base.stock(), base.sales(),
                    base.categoryName(), base.brandName(), base.status(), base.createdAt(), null);
        }
        BigDecimal price = skus.stream()
                .map(SkuDTO::price)
                .filter(Objects::nonNull)
                .min(BigDecimal::compareTo)
                .orElse(null);
        BigDecimal originalPrice = skus.stream()
                .map(SkuDTO::originalPrice)
                .filter(Objects::nonNull)
                .min(BigDecimal::compareTo)
                .orElse(null);
        Integer stock = skus.stream()
                .map(SkuDTO::stock)
                .filter(Objects::nonNull)
                .reduce(0, Integer::sum);
        return new ProductVO(base.id(), base.name(), base.mainImage(),
                price, originalPrice, stock, base.sales(),
                base.categoryName(), base.brandName(), base.status(), base.createdAt(), skus);
    }

    default List<ProductVO> productDtoListToVOList(List<ProductDTO> dtos) {
        return dtos.stream().map(dto -> {
            BigDecimal price = dto.skus() != null ? dto.skus().stream()
                    .map(SkuDTO::price)
                    .filter(Objects::nonNull)
                    .min(BigDecimal::compareTo)
                    .orElse(null) : null;
            BigDecimal originalPrice = dto.skus() != null ? dto.skus().stream()
                    .map(SkuDTO::originalPrice)
                    .filter(Objects::nonNull)
                    .min(BigDecimal::compareTo)
                    .orElse(null) : null;
            Integer stock = dto.skus() != null ? dto.skus().stream()
                    .map(SkuDTO::stock)
                    .filter(Objects::nonNull)
                    .reduce(0, Integer::sum) : 0;
            ProductVO base = productDtoToVO(dto);
            return new ProductVO(base.id(), base.name(), base.mainImage(),
                    price, originalPrice, stock, null, base.categoryName(),
                    base.brandName(), base.status(), base.createdAt(), null);
        }).toList();
    }

    CategoryVO categoryDtoToVO(CategoryDTO dto);

    default List<CategoryVO> categoryDtoListToVOList(List<CategoryDTO> dtos) {
        return dtos.stream().map(this::categoryDtoToVO).toList();
    }

    ReviewVO reviewDtoToVO(ReviewDTO dto);

    default List<ReviewVO> reviewDtoListToVOList(List<ReviewDTO> dtos) {
        return dtos.stream().map(this::reviewDtoToVO).toList();
    }

    default ReviewStatsVO reviewStatsDtoToVO(ReviewStatsDTO dto) {
        Map<Integer, Integer> distribution = Map.of(
                5, dto.fiveStarCount(),
                4, dto.fourStarCount(),
                3, dto.threeStarCount(),
                2, dto.twoStarCount(),
                1, dto.oneStarCount()
        );
        return new ReviewStatsVO(dto.averageRating(), dto.totalReviews(), distribution);
    }

    BrandVO brandDtoToVO(com.cloudmart.product.dto.BrandDTO dto);
}
