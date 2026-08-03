package com.cloudmart.product.service.impl;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ProductReviewMapper reviewMapper;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private ProductSkuMapper skuMapper;

    @Mock
    private ObjectMapper objectMapper;

    private ReviewServiceImpl reviewService;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewServiceImpl(reviewMapper, productMapper, skuMapper, objectMapper);
    }

    @Test
    @DisplayName("createReview: 商品存在且无重复评价时应成功创建")
    void createReview_WhenProductExistsAndNoDuplicate_ShouldCreateReview() throws JacksonException {
        Long userId = 1001L;
        List<String> images = List.of("https://img.example.com/1.jpg");
        CreateReviewRequest request = new CreateReviewRequest(
                2001L, 3001L, 4001L, 5, "非常好", images);

        Product product = new Product();
        product.setId(3001L);
        product.setName("测试商品");
        when(productMapper.selectById(3001L)).thenReturn(product);

        when(reviewMapper.selectCount(any())).thenReturn(0L);

        when(objectMapper.writeValueAsString(images)).thenReturn("[\"https://img.example.com/1.jpg\"]");

        when(reviewMapper.insert(any(ProductReview.class))).thenAnswer(invocation -> {
            ProductReview review = invocation.getArgument(0);
            review.setId(1L);
            review.setCreatedAt(LocalDateTime.of(2026, 5, 13, 10, 0));
            return 1;
        });

        ProductSku sku = new ProductSku();
        sku.setId(4001L);
        sku.setAttributes("颜色:红色;尺码:XL");
        when(skuMapper.selectById(4001L)).thenReturn(sku);

        ReviewDTO result = reviewService.createReview(userId, request);

        assertThat(result).isNotNull();
        assertThat(result.productId()).isEqualTo(3001L);
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.orderId()).isEqualTo(2001L);
        assertThat(result.skuId()).isEqualTo(4001L);
        assertThat(result.skuAttributes()).isEqualTo("颜色:红色;尺码:XL");
        assertThat(result.rating()).isEqualTo(5);
        assertThat(result.content()).isEqualTo("非常好");
        assertThat(result.images()).containsExactly("https://img.example.com/1.jpg");
        assertThat(result.createdAt()).isEqualTo(LocalDateTime.of(2026, 5, 13, 10, 0));

        verify(reviewMapper).insert(any(ProductReview.class));
        verify(objectMapper).writeValueAsString(images);
    }

    @Test
    @DisplayName("createReview: 重复评价时应抛出BusinessException")
    void createReview_WhenDuplicateReview_ShouldThrowBusinessException() {
        Long userId = 1001L;
        CreateReviewRequest request = new CreateReviewRequest(
                2001L, 3001L, 4001L, 5, "非常好", null);

        Product product = new Product();
        product.setId(3001L);
        when(productMapper.selectById(3001L)).thenReturn(product);

        when(reviewMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> reviewService.createReview(userId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo("REVIEW_ALREADY_EXISTS");
                });
    }

    @Test
    @DisplayName("createReview: 商品不存在时应抛出BusinessException")
    void createReview_WhenProductNotFound_ShouldThrowBusinessException() {
        Long userId = 1001L;
        CreateReviewRequest request = new CreateReviewRequest(
                2001L, 9999L, 4001L, 5, "非常好", null);

        when(productMapper.selectById(9999L)).thenReturn(null);

        assertThatThrownBy(() -> reviewService.createReview(userId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo("PRODUCT_NOT_FOUND");
                });
    }

    @Test
    @DisplayName("getReviewStats: 多种评分时应正确计算统计数据")
    void getReviewStats_WithMultipleRatings_ShouldCalculateCorrectly() {
        Long productId = 3001L;

        List<Map<String, Object>> ratingStats = List.of(
                Map.of("rating", 5, "cnt", 2),
                Map.of("rating", 4, "cnt", 1),
                Map.of("rating", 1, "cnt", 1)
        );
        when(reviewMapper.selectRatingStats(productId)).thenReturn(ratingStats);

        ReviewStatsDTO result = reviewService.getReviewStats(productId);

        assertThat(result).isNotNull();
        assertThat(result.productId()).isEqualTo(3001L);
        assertThat(result.totalReviews()).isEqualTo(4);
        assertThat(result.averageRating())
                .isEqualByComparingTo(BigDecimal.valueOf(15.0 / 4).setScale(1, RoundingMode.HALF_UP));
        assertThat(result.averageRating()).isEqualByComparingTo(BigDecimal.valueOf(3.8));
        assertThat(result.fiveStarCount()).isEqualTo(2);
        assertThat(result.fourStarCount()).isEqualTo(1);
        assertThat(result.threeStarCount()).isEqualTo(0);
        assertThat(result.twoStarCount()).isEqualTo(0);
        assertThat(result.oneStarCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("hasUserReviewedProduct: 评价存在时应返回true")
    void hasUserReviewedProduct_WhenReviewExists_ShouldReturnTrue() {
        Long userId = 1001L;
        Long orderId = 2001L;
        Long productId = 3001L;

        when(reviewMapper.selectCount(any())).thenReturn(1L);

        boolean result = reviewService.hasUserReviewedProduct(userId, orderId, productId);

        assertThat(result).isTrue();
    }
}
