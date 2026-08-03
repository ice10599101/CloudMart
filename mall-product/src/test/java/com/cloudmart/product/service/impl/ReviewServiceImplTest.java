package com.cloudmart.product.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class ReviewServiceImplTest {

    private ProductReviewMapper reviewMapper;
    private ProductMapper productMapper;
    private ProductSkuMapper skuMapper;
    private ObjectMapper objectMapper;
    private ReviewServiceImpl reviewService;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        for (Class<?> clazz : new Class<?>[]{ProductReview.class, Product.class, ProductSku.class}) {
            if (TableInfoHelper.getTableInfo(clazz) == null) {
                MybatisConfiguration configuration = new MybatisConfiguration();
                MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
                assistant.setCurrentNamespace("com.cloudmart.product.repository." + clazz.getSimpleName() + "Mapper");
                TableInfoHelper.initTableInfo(assistant, clazz);
            }
        }
    }

    @BeforeEach
    void setUp() {
        reviewMapper = mock(ProductReviewMapper.class);
        productMapper = mock(ProductMapper.class);
        skuMapper = mock(ProductSkuMapper.class);
        objectMapper = new ObjectMapper();
        reviewService = new ReviewServiceImpl(reviewMapper, productMapper, skuMapper, objectMapper);
    }

    private Product buildProduct(Long id) {
        Product product = new Product();
        product.setId(id);
        product.setName("Test Product");
        product.setStatus(1);
        return product;
    }

    private ProductSku buildSku(Long id) {
        ProductSku sku = new ProductSku();
        sku.setId(id);
        sku.setAttributes("颜色:红色;尺码:XL");
        sku.setPrice(new BigDecimal("99.99"));
        return sku;
    }

    @Nested
    @DisplayName("createReview")
    class CreateReviewTests {

        @Test
        @DisplayName("valid request -> creates review and returns ReviewDTO")
        void createReview_ValidRequest_ShouldCreateAndReturnDTO() {
            Product product = buildProduct(1L);
            ProductSku sku = buildSku(10L);
            when(productMapper.selectById(1L)).thenReturn(product);
            when(reviewMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(skuMapper.selectById(10L)).thenReturn(sku);

            CreateReviewRequest request = new CreateReviewRequest(100L, 1L, 10L, 5, "Great product!", List.of("img1.jpg"));
            ReviewDTO result = reviewService.createReview(200L, request);

            assertThat(result.productId()).isEqualTo(1L);
            assertThat(result.userId()).isEqualTo(200L);
            assertThat(result.orderId()).isEqualTo(100L);
            assertThat(result.skuId()).isEqualTo(10L);
            assertThat(result.skuAttributes()).isEqualTo("颜色:红色;尺码:XL");
            assertThat(result.rating()).isEqualTo(5);
            assertThat(result.content()).isEqualTo("Great product!");
            assertThat(result.images()).containsExactly("img1.jpg");
            verify(reviewMapper).insert(any(ProductReview.class));
        }

        @Test
        @DisplayName("product not found -> throws PRODUCT_NOT_FOUND")
        void createReview_ProductNotFound_ShouldThrowBusinessException() {
            when(productMapper.selectById(999L)).thenReturn(null);

            CreateReviewRequest request = new CreateReviewRequest(100L, 999L, 10L, 5, "Good", null);

            assertThatThrownBy(() -> reviewService.createReview(200L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("PRODUCT_NOT_FOUND"));
            verify(reviewMapper, never()).insert(any(ProductReview.class));
        }

        @Test
        @DisplayName("already reviewed -> throws REVIEW_ALREADY_EXISTS")
        void createReview_AlreadyReviewed_ShouldThrowBusinessException() {
            Product product = buildProduct(1L);
            when(productMapper.selectById(1L)).thenReturn(product);
            when(reviewMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            CreateReviewRequest request = new CreateReviewRequest(100L, 1L, 10L, 5, "Good", null);

            assertThatThrownBy(() -> reviewService.createReview(200L, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("REVIEW_ALREADY_EXISTS"));
            verify(reviewMapper, never()).insert(any(ProductReview.class));
        }

        @Test
        @DisplayName("null images -> review created without images")
        void createReview_NullImages_ShouldCreateWithoutImages() {
            Product product = buildProduct(1L);
            ProductSku sku = buildSku(10L);
            when(productMapper.selectById(1L)).thenReturn(product);
            when(reviewMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(skuMapper.selectById(10L)).thenReturn(sku);

            CreateReviewRequest request = new CreateReviewRequest(100L, 1L, 10L, 4, "OK", null);
            ReviewDTO result = reviewService.createReview(200L, request);

            assertThat(result.images()).isNull();
            verify(reviewMapper).insert(any(ProductReview.class));
        }
    }

    @Nested
    @DisplayName("getReviewById")
    class GetReviewByIdTests {

        @Test
        @DisplayName("review exists -> returns ReviewDTO")
        void getReviewById_Exists_ShouldReturnDTO() {
            ProductReview review = new ProductReview();
            review.setId(1L);
            review.setProductId(10L);
            review.setUserId(200L);
            review.setOrderId(100L);
            review.setSkuId(50L);
            review.setRating(5);
            review.setContent("Excellent!");
            review.setImages(null);
            review.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
            when(reviewMapper.selectById(1L)).thenReturn(review);

            ProductSku sku = buildSku(50L);
            when(skuMapper.selectById(50L)).thenReturn(sku);

            ReviewDTO result = reviewService.getReviewById(1L);

            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.rating()).isEqualTo(5);
            assertThat(result.content()).isEqualTo("Excellent!");
            assertThat(result.skuAttributes()).isEqualTo("颜色:红色;尺码:XL");
            assertThat(result.username()).isEqualTo("用户****200");
        }

        @Test
        @DisplayName("review not found -> throws REVIEW_NOT_FOUND")
        void getReviewById_NotFound_ShouldThrowBusinessException() {
            when(reviewMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> reviewService.getReviewById(999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("REVIEW_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("updateReviewStatus")
    class UpdateReviewStatusTests {

        @Test
        @DisplayName("review exists -> updates status")
        void updateReviewStatus_Exists_ShouldUpdate() {
            ProductReview review = new ProductReview();
            review.setId(1L);
            review.setStatus(1);
            when(reviewMapper.selectById(1L)).thenReturn(review);

            reviewService.updateReviewStatus(1L, 0);

            assertThat(review.getStatus()).isEqualTo(0);
            verify(reviewMapper).updateById(review);
        }

        @Test
        @DisplayName("review not found -> throws REVIEW_NOT_FOUND")
        void updateReviewStatus_NotFound_ShouldThrowBusinessException() {
            when(reviewMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> reviewService.updateReviewStatus(999L, 0))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("REVIEW_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("deleteReview")
    class DeleteReviewTests {

        @Test
        @DisplayName("review exists -> deletes review")
        void deleteReview_Exists_ShouldDelete() {
            ProductReview review = new ProductReview();
            review.setId(1L);
            when(reviewMapper.selectById(1L)).thenReturn(review);

            reviewService.deleteReview(1L);

            verify(reviewMapper).deleteById(1L);
        }

        @Test
        @DisplayName("review not found -> throws REVIEW_NOT_FOUND")
        void deleteReview_NotFound_ShouldThrowBusinessException() {
            when(reviewMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> reviewService.deleteReview(999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("REVIEW_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("hasUserReviewedProduct")
    class HasUserReviewedProductTests {

        @Test
        @DisplayName("user has reviewed -> returns true")
        void hasUserReviewedProduct_Reviewed_ShouldReturnTrue() {
            when(reviewMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            assertThat(reviewService.hasUserReviewedProduct(200L, 100L, 1L)).isTrue();
        }

        @Test
        @DisplayName("user has not reviewed -> returns false")
        void hasUserReviewedProduct_NotReviewed_ShouldReturnFalse() {
            when(reviewMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

            assertThat(reviewService.hasUserReviewedProduct(200L, 100L, 1L)).isFalse();
        }
    }

    @Nested
    @DisplayName("getReviewStats")
    class GetReviewStatsTests {

        @Test
        @DisplayName("with reviews -> calculates stats correctly")
        void getReviewStats_WithReviews_ShouldCalculateCorrectly() {
            List<Map<String, Object>> stats = List.of(
                    Map.of("rating", 5, "cnt", 10),
                    Map.of("rating", 4, "cnt", 5),
                    Map.of("rating", 3, "cnt", 3),
                    Map.of("rating", 2, "cnt", 1),
                    Map.of("rating", 1, "cnt", 1)
            );
            when(reviewMapper.selectRatingStats(1L)).thenReturn(stats);

            ReviewStatsDTO result = reviewService.getReviewStats(1L);

            assertThat(result.productId()).isEqualTo(1L);
            assertThat(result.totalReviews()).isEqualTo(20);
            assertThat(result.fiveStarCount()).isEqualTo(10);
            assertThat(result.fourStarCount()).isEqualTo(5);
            assertThat(result.threeStarCount()).isEqualTo(3);
            assertThat(result.twoStarCount()).isEqualTo(1);
            assertThat(result.oneStarCount()).isEqualTo(1);
            assertThat(result.averageRating()).isEqualByComparingTo(new BigDecimal("4.1"));
        }

        @Test
        @DisplayName("no reviews -> returns zero stats")
        void getReviewStats_NoReviews_ShouldReturnZeroStats() {
            when(reviewMapper.selectRatingStats(1L)).thenReturn(List.of());

            ReviewStatsDTO result = reviewService.getReviewStats(1L);

            assertThat(result.totalReviews()).isEqualTo(0);
            assertThat(result.averageRating()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }
}
