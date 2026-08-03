package com.cloudmart.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.product.dto.WishlistDTO;
import com.cloudmart.product.entity.Product;
import com.cloudmart.product.entity.ProductSku;
import com.cloudmart.product.entity.Wishlist;
import com.cloudmart.product.repository.ProductMapper;
import com.cloudmart.product.repository.ProductSkuMapper;
import com.cloudmart.product.repository.WishlistMapper;
import com.cloudmart.product.service.WishlistService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class WishlistServiceImpl implements WishlistService {

    private final WishlistMapper wishlistMapper;
    private final ProductMapper productMapper;
    private final ProductSkuMapper productSkuMapper;

    public WishlistServiceImpl(WishlistMapper wishlistMapper,
                               ProductMapper productMapper,
                               ProductSkuMapper productSkuMapper) {
        this.wishlistMapper = wishlistMapper;
        this.productMapper = productMapper;
        this.productSkuMapper = productSkuMapper;
    }

    @Override
    @Transactional
    public void addToList(Long userId, Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException("PRODUCT_NOT_FOUND", "商品不存在");
        }

        LambdaQueryWrapper<Wishlist> wrapper = new LambdaQueryWrapper<Wishlist>()
                .eq(Wishlist::getUserId, userId)
                .eq(Wishlist::getProductId, productId);
        Long count = wishlistMapper.selectCount(wrapper);
        if (count > 0) {
            throw new BusinessException("WISHLIST_ALREADY_EXISTS", "商品已在收藏列表中");
        }

        Wishlist wishlist = new Wishlist();
        wishlist.setUserId(userId);
        wishlist.setProductId(productId);
        wishlistMapper.insert(wishlist);
    }

    @Override
    @Transactional
    public void removeFromList(Long userId, Long productId) {
        LambdaQueryWrapper<Wishlist> wrapper = new LambdaQueryWrapper<Wishlist>()
                .eq(Wishlist::getUserId, userId)
                .eq(Wishlist::getProductId, productId);
        int deleted = wishlistMapper.delete(wrapper);
        if (deleted == 0) {
            throw new BusinessException("WISHLIST_NOT_FOUND", "收藏记录不存在");
        }
    }

    @Override
    public Page<WishlistDTO> getUserWishlist(Long userId, int page, int size) {
        Page<Wishlist> wishlistPage = wishlistMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<Wishlist>()
                        .eq(Wishlist::getUserId, userId)
                        .orderByDesc(Wishlist::getCreatedAt)
        );

        List<Long> productIds = wishlistPage.getRecords().stream()
                .map(Wishlist::getProductId)
                .distinct()
                .toList();

        Map<Long, Product> productMap = productIds.isEmpty() ? Map.of() :
                productMapper.selectBatchIds(productIds).stream()
                        .collect(Collectors.toMap(Product::getId, p -> p));

        List<Long> existingProductIds = productMap.values().stream()
                .map(Product::getId).toList();
        Map<Long, List<ProductSku>> skuMap = existingProductIds.isEmpty() ? Map.of() :
                productSkuMapper.selectList(
                        new LambdaQueryWrapper<ProductSku>()
                                .in(ProductSku::getProductId, existingProductIds)
                ).stream().collect(Collectors.groupingBy(ProductSku::getProductId));

        List<WishlistDTO> dtos = wishlistPage.getRecords().stream()
                .map(w -> {
                    Product p = productMap.get(w.getProductId());
                    if (p == null) {
                        return null;
                    }
                    List<ProductSku> skus = skuMap.getOrDefault(p.getId(), List.of());
                    BigDecimal minPrice = skus.stream()
                            .map(ProductSku::getPrice)
                            .min(BigDecimal::compareTo)
                            .orElse(BigDecimal.ZERO);
                    return new WishlistDTO(
                            w.getId(),
                            p.getId(),
                            p.getName(),
                            p.getMainImage(),
                            minPrice,
                            p.getBrand(),
                            w.getCreatedAt()
                    );
                })
                .filter(dto -> dto != null)
                .toList();

        Page<WishlistDTO> resultPage = new Page<>(
                wishlistPage.getCurrent(), wishlistPage.getSize(), wishlistPage.getTotal());
        resultPage.setRecords(dtos);
        return resultPage;
    }

    @Override
    public boolean isInWishlist(Long userId, Long productId) {
        LambdaQueryWrapper<Wishlist> wrapper = new LambdaQueryWrapper<Wishlist>()
                .eq(Wishlist::getUserId, userId)
                .eq(Wishlist::getProductId, productId);
        return wishlistMapper.selectCount(wrapper) > 0;
    }
}
