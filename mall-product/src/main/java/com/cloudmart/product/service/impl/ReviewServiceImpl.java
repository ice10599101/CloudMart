package com.cloudmart.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.product.dto.CreateReviewRequest;
import com.cloudmart.product.dto.ReviewDTO;
import com.cloudmart.product.dto.ReviewStatsDTO;
import com.cloudmart.product.entity.Product;
import com.cloudmart.product.entity.ProductRewardObligation;
import com.cloudmart.product.entity.ProductReview;
import com.cloudmart.product.entity.ProductSku;
import com.cloudmart.product.repository.ProductMapper;
import com.cloudmart.product.repository.ProductReviewMapper;
import com.cloudmart.product.repository.ProductSkuMapper;
import com.cloudmart.product.service.ReviewService;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
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
    private final com.cloudmart.product.feign.OrderPurchaseFeignClient orderPurchaseFeignClient;
    private final com.cloudmart.product.service.impl.ProductRewardObligationService rewardObligationService;

    /** N-2：评价返星光数量（0=关闭激励开关） */
    @org.springframework.beans.factory.annotation.Value("${review.reward-starlight:5}")
    private int rewardStarlight;

    public ReviewServiceImpl(ProductReviewMapper reviewMapper,
                             ProductMapper productMapper,
                             ProductSkuMapper skuMapper,
                             ObjectMapper objectMapper,
                             com.cloudmart.product.feign.OrderPurchaseFeignClient orderPurchaseFeignClient,
                             com.cloudmart.product.service.impl.ProductRewardObligationService rewardObligationService) {
        this.reviewMapper = reviewMapper;
        this.productMapper = productMapper;
        this.skuMapper = skuMapper;
        this.objectMapper = objectMapper;
        this.orderPurchaseFeignClient = orderPurchaseFeignClient;
        this.rewardObligationService = rewardObligationService;
    }

    @Override
    @Transactional
    public ReviewDTO createReview(Long userId, CreateReviewRequest request) {
        Product product = productMapper.selectById(request.productId());
        if (product == null) {
            throw new BusinessException("PRODUCT_NOT_FOUND", "商品不存在");
        }

        // R08：以订单行为唯一资格锚点——订单服务权威返回行事实，
        // 行属主=userId 且订单 COMPLETED 才可评价；订单服务不可用 fail-closed
        ApiResponse<com.cloudmart.product.dto.OrderItemEligibility> eligibilityResp =
                orderPurchaseFeignClient.reviewEligibility(userId, request.orderItemId());
        if (eligibilityResp == null || !eligibilityResp.success() || eligibilityResp.data() == null) {
            throw new BusinessException("REVIEW_NOT_ELIGIBLE", "未找到可评价的已完成订单行");
        }
        var fact = eligibilityResp.data();
        // R08：请求三要素必须与订单行权威事实逐项一致，防伪造组合与跨商品 SKU 错配
        if (!java.util.Objects.equals(fact.orderId(), request.orderId())
                || !java.util.Objects.equals(fact.productId(), request.productId())
                || !java.util.Objects.equals(fact.skuId(), request.skuId())) {
            throw new BusinessException("REVIEW_NOT_ELIGIBLE",
                    "评价商品/SKU/订单与购买记录不一致，请从订单详情发起评价");
        }

        // R08：每订单行一次评价（预检 + uk_user_order_item 兜底并发）
        Long existing = reviewMapper.selectCount(new LambdaQueryWrapper<ProductReview>()
                .eq(ProductReview::getUserId, userId)
                .eq(ProductReview::getOrderItemId, request.orderItemId()));
        if (existing != null && existing > 0) {
            throw new BusinessException("REVIEW_ALREADY_EXISTS", "该订单商品您已评价过");
        }

        ProductReview review = new ProductReview();
        review.setProductId(request.productId());
        review.setUserId(userId);
        review.setOrderId(request.orderId());
        review.setOrderItemId(request.orderItemId());
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

        try {
            reviewMapper.insert(review);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // R08：并发重复评价由 uk_user_order_item 兜底——同一订单行不可二次评价
            throw new BusinessException("REVIEW_ALREADY_EXISTS", "该订单商品您已评价过");
        }

        ProductSku sku = skuMapper.selectById(request.skuId());
        String skuAttributes = sku != null ? sku.getAttributes() : null;

        // N-2/R07 评价返星光：交付义务与评价行同事务落库（uk biz_key 每评价至多一次），
        // 提交后即送/失败退避补发——进程崩溃后义务行仍在，不再只靠日志补发
        if (rewardStarlight > 0 && review.getId() != null) {
            rewardObligationService.enqueueWithCommitDelivery(
                    "REVIEW_REWARD:" + review.getId(), "REVIEW_REWARD",
                    String.valueOf(review.getId()), userId,
                    ProductRewardObligation.TYPE_STARLIGHT, rewardStarlight, "评价返星光");
        }

        return toDto(review, skuAttributes, null, request.images());
    }

    /** R08：作者查自己在某订单行的评价（含下架理由；用于订单详情评价状态与理由回显） */
    @Override
    public ReviewDTO getOwnReviewByOrderItem(Long userId, Long orderItemId) {
        ProductReview review = reviewMapper.selectOne(new LambdaQueryWrapper<ProductReview>()
                .eq(ProductReview::getUserId, userId)
                .eq(ProductReview::getOrderItemId, orderItemId)
                .last("LIMIT 1"));
        if (review == null) {
            return null;
        }
        ProductSku sku = review.getSkuId() == null ? null : skuMapper.selectById(review.getSkuId());
        return toDto(review, sku != null ? sku.getAttributes() : null, null, parseImages(review.getImages()));
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
                .map(review -> toDto(review,
                        skuAttributesMap.getOrDefault(review.getSkuId(), null),
                        buildMaskedUsername(review.getUserId()),
                        parseImages(review.getImages())))
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
                .map(review -> toDto(review,
                        skuAttributesMap.getOrDefault(review.getSkuId(), null),
                        buildMaskedUsername(review.getUserId()),
                        parseImages(review.getImages())))
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

        return toDto(review, skuAttributes, buildMaskedUsername(review.getUserId()),
                parseImages(review.getImages()));
    }

    @Override
    @Transactional
    public void updateReviewStatus(Long id, Integer status) {
        updateReviewStatus(id, status, null);
    }

    /**
     * R08：管理端审核/下架——status=0（下架）必须给理由（<=200 字，作者可见）；
     * 恢复显示（status=1）清空理由。
     */
    @Override
    @Transactional
    public void updateReviewStatus(Long id, Integer status, String reason) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("REVIEW_STATUS_INVALID", "评价状态仅支持 0=下架 或 1=显示");
        }
        ProductReview review = reviewMapper.selectById(id);
        if (review == null) {
            throw new BusinessException("REVIEW_NOT_FOUND", "评价不存在");
        }
        if (status == 0 && (reason == null || reason.isBlank())) {
            throw new BusinessException("REVIEW_HIDE_REASON_REQUIRED", "下架评价必须填写理由");
        }
        review.setStatus(status);
        review.setHideReason(status == 0 ? reason.trim() : null);
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

    /** R08 统一 DTO 组装（含订单行锚点与下架理由） */
    private ReviewDTO toDto(ProductReview review, String skuAttributes, String username, List<String> images) {
        return new ReviewDTO(
                review.getId(),
                review.getProductId(),
                review.getUserId(),
                username,
                null,
                review.getOrderId(),
                review.getOrderItemId(),
                review.getSkuId(),
                skuAttributes,
                review.getRating(),
                review.getContent(),
                images,
                review.getStatus(),
                review.getHideReason(),
                review.getCreatedAt()
        );
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