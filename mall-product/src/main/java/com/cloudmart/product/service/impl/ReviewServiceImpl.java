package com.cloudmart.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.product.dto.CreateReviewRequest;
import com.cloudmart.product.dto.ReviewDTO;
import com.cloudmart.product.dto.ReviewStatsDTO;
import com.cloudmart.product.entity.Product;
import com.cloudmart.product.entity.ProductReview;
import com.cloudmart.product.entity.ProductSku;
import com.cloudmart.product.repository.ProductMapper;
import com.cloudmart.product.repository.ProductReviewMapper;
import com.cloudmart.product.repository.ProductSkuMapper;
import com.cloudmart.product.service.ReviewService;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReviewServiceImpl implements ReviewService {

    private final ProductReviewMapper reviewMapper;
    private final ProductMapper productMapper;
    private final ProductSkuMapper skuMapper;
    private final ObjectMapper objectMapper;

    public ReviewServiceImpl(ProductReviewMapper reviewMapper,
                             ProductMapper productMapper,
                             ProductSkuMapper skuMapper,
                             ObjectMapper objectMapper) {
        this.reviewMapper = reviewMapper;
        this.productMapper = productMapper;
        this.skuMapper = skuMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public ReviewDTO createReview(Long userId, CreateReviewRequest request) {
        Product product = productMapper.selectById(request.productId());
        if (product == null) {
            throw new BusinessException("PRODUCT_NOT_FOUND", "商品不存在");
        }

        LambdaQueryWrapper<ProductReview> existsWrapper = new LambdaQueryWrapper<ProductReview>()
                .eq(ProductReview::getUserId, userId)
                .eq(ProductReview::getOrderId, request.orderId())
                .eq(ProductReview::getProductId, request.productId());
        if (reviewMapper.selectCount(existsWrapper) > 0) {
            throw new BusinessException("REVIEW_ALREADY_EXISTS", "您已评价过该商品");
        }

        ProductReview review = new ProductReview();
        review.setProductId(request.productId());
        review.setUserId(userId);
        review.setOrderId(request.orderId());
        review.setSkuId(request.skuId());
        review.setRating(request.rating());
        review.setContent(request.content());
        review.setStatus(1);

        if (request.images() != null && !request.images().isEmpty()) {
            try {
                review.setImages(objectMapper.writeValueAsString(request.images()));
            } catch (JacksonException e) {
                review.setImages("[]");
            }
        }

        reviewMapper.insert(review);

        ProductSku sku = skuMapper.selectById(request.skuId());
        String skuAttributes = sku != null ? sku.getAttributes() : null;

        return new ReviewDTO(
                review.getId(),
                review.getProductId(),
                review.getUserId(),
                null,
                null,
                review.getOrderId(),
                review.getSkuId(),
                skuAttributes,
                review.getRating(),
                review.getContent(),
                request.images(),
                review.getStatus(),
                review.getCreatedAt()
        );
    }

    @Override
    public Page<ReviewDTO> getProductReviews(Long productId, int page, int size) {
        Page<ProductReview> reviewPage = reviewMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<ProductReview>()
                        .eq(ProductReview::getProductId, productId)
                        .eq(ProductReview::getStatus, 1)
                        .orderByDesc(ProductReview::getCreatedAt));

        List<Long> skuIds = reviewPage.getRecords().stream()
                .map(ProductReview::getSkuId)
                .distinct()
                .filter(id -> id != null)
                .toList();

        Map<Long, String> skuAttributesMap = skuIds.isEmpty() ? Map.of() :
                skuMapper.selectBatchIds(skuIds).stream()
                        .collect(Collectors.toMap(ProductSku::getId, ProductSku::getAttributes));

        List<ReviewDTO> dtos = reviewPage.getRecords().stream()
                .map(review -> new ReviewDTO(
                        review.getId(),
                        review.getProductId(),
                        review.getUserId(),
                        buildMaskedUsername(review.getUserId()),
                        null,
                        review.getOrderId(),
                        review.getSkuId(),
                        skuAttributesMap.getOrDefault(review.getSkuId(), null),
                        review.getRating(),
                        review.getContent(),
                        parseImages(review.getImages()),
                        review.getStatus(),
                        review.getCreatedAt()
                ))
                .toList();

        Page<ReviewDTO> resultPage = new Page<>(
                reviewPage.getCurrent(), reviewPage.getSize(), reviewPage.getTotal());
        resultPage.setRecords(dtos);
        return resultPage;
    }

    @Override
    public ReviewStatsDTO getReviewStats(Long productId) {
        List<Map<String, Object>> ratingStats = reviewMapper.selectRatingStats(productId);

        int totalReviews = 0;
        int weightedSum = 0;
        int fiveStar = 0;
        int fourStar = 0;
        int threeStar = 0;
        int twoStar = 0;
        int oneStar = 0;

        for (Map<String, Object> row : ratingStats) {
            int rating = ((Number) row.get("rating")).intValue();
            int count = ((Number) row.get("cnt")).intValue();
            totalReviews += count;
            weightedSum += rating * count;

            switch (rating) {
                case 5 -> fiveStar = count;
                case 4 -> fourStar = count;
                case 3 -> threeStar = count;
                case 2 -> twoStar = count;
                case 1 -> oneStar = count;
            }
        }

        BigDecimal averageRating = totalReviews > 0
                ? BigDecimal.valueOf((double) weightedSum / totalReviews).setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new ReviewStatsDTO(productId, averageRating, totalReviews,
                fiveStar, fourStar, threeStar, twoStar, oneStar);
    }

    @Override
    public boolean hasUserReviewedProduct(Long userId, Long orderId, Long productId) {
        LambdaQueryWrapper<ProductReview> wrapper = new LambdaQueryWrapper<ProductReview>()
                .eq(ProductReview::getUserId, userId)
                .eq(ProductReview::getOrderId, orderId)
                .eq(ProductReview::getProductId, productId);
        return reviewMapper.selectCount(wrapper) > 0;
    }

    @Override
    public Page<ReviewDTO> listReviewsForAdmin(Long productId, Integer status, int page, int size) {
        LambdaQueryWrapper<ProductReview> wrapper = new LambdaQueryWrapper<ProductReview>()
                .eq(productId != null, ProductReview::getProductId, productId)
                .eq(status != null, ProductReview::getStatus, status)
                .orderByDesc(ProductReview::getCreatedAt);

        Page<ProductReview> reviewPage = reviewMapper.selectPage(new Page<>(page, size), wrapper);

        List<Long> skuIds = reviewPage.getRecords().stream()
                .map(ProductReview::getSkuId)
                .distinct()
                .filter(id -> id != null)
                .toList();

        Map<Long, String> skuAttributesMap = skuIds.isEmpty() ? Map.of() :
                skuMapper.selectBatchIds(skuIds).stream()
                        .collect(Collectors.toMap(ProductSku::getId, ProductSku::getAttributes));

        List<ReviewDTO> dtos = reviewPage.getRecords().stream()
                .map(review -> new ReviewDTO(
                        review.getId(),
                        review.getProductId(),
                        review.getUserId(),
                        buildMaskedUsername(review.getUserId()),
                        null,
                        review.getOrderId(),
                        review.getSkuId(),
                        skuAttributesMap.getOrDefault(review.getSkuId(), null),
                        review.getRating(),
                        review.getContent(),
                        parseImages(review.getImages()),
                        review.getStatus(),
                        review.getCreatedAt()
                ))
                .toList();

        Page<ReviewDTO> resultPage = new Page<>(
                reviewPage.getCurrent(), reviewPage.getSize(), reviewPage.getTotal());
        resultPage.setRecords(dtos);
        return resultPage;
    }

    @Override
    public ReviewDTO getReviewById(Long id) {
        ProductReview review = reviewMapper.selectById(id);
        if (review == null) {
            throw new BusinessException("REVIEW_NOT_FOUND", "评价不存在");
        }

        String skuAttributes = null;
        if (review.getSkuId() != null) {
            ProductSku sku = skuMapper.selectById(review.getSkuId());
            skuAttributes = sku != null ? sku.getAttributes() : null;
        }

        return new ReviewDTO(
                review.getId(),
                review.getProductId(),
                review.getUserId(),
                buildMaskedUsername(review.getUserId()),
                null,
                review.getOrderId(),
                review.getSkuId(),
                skuAttributes,
                review.getRating(),
                review.getContent(),
                parseImages(review.getImages()),
                review.getStatus(),
                review.getCreatedAt()
        );
    }

    @Override
    @Transactional
    public void updateReviewStatus(Long id, Integer status) {
        ProductReview review = reviewMapper.selectById(id);
        if (review == null) {
            throw new BusinessException("REVIEW_NOT_FOUND", "评价不存在");
        }
        review.setStatus(status);
        reviewMapper.updateById(review);
    }

    @Override
    @Transactional
    public void deleteReview(Long id) {
        ProductReview review = reviewMapper.selectById(id);
        if (review == null) {
            throw new BusinessException("REVIEW_NOT_FOUND", "评价不存在");
        }
        reviewMapper.deleteById(id);
    }

    private String buildMaskedUsername(Long userId) {
        String userIdStr = String.valueOf(userId);
        String suffix = userIdStr.length() > 4
                ? userIdStr.substring(userIdStr.length() - 4)
                : userIdStr;
        return "用户****" + suffix;
    }

    private List<String> parseImages(String imagesJson) {
        if (imagesJson == null || imagesJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(imagesJson, new TypeReference<List<String>>() {});
        } catch (JacksonException e) {
            return List.of();
        }
    }
}
