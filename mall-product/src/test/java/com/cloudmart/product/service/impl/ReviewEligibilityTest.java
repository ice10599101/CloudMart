package com.cloudmart.product.service.impl;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.product.dto.CreateReviewRequest;
import com.cloudmart.product.dto.ReviewDTO;
import com.cloudmart.product.entity.Product;
import com.cloudmart.product.entity.ProductReview;
import com.cloudmart.product.feign.OrderPurchaseFeignClient;
import com.cloudmart.product.repository.ProductMapper;
import com.cloudmart.product.repository.ProductReviewMapper;
import com.cloudmart.product.repository.ProductSkuMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * REVIEW-01：已购评价资格——未购/他人订单/未完成订单/SKU 与订单不符一律拒绝；
 * 订单服务不可用 fail-closed；并发重复由唯一键转译 REVIEW_ALREADY_EXISTS。
 */
@DisplayName("ReviewServiceImpl 评价资格校验")
class ReviewEligibilityTest {

    private static final Long USER_ID = 42L;
    private static final Long ORDER_ID = 9001L;
    private static final Long PRODUCT_ID = 300L;
    private static final Long SKU_ID = 200L;

    private ReviewServiceImpl reviewService;
    private ProductReviewMapper reviewMapper;
    private ProductMapper productMapper;
    private ProductSkuMapper skuMapper;
    private OrderPurchaseFeignClient orderPurchaseFeignClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        reviewMapper = mock(ProductReviewMapper.class);
        productMapper = mock(ProductMapper.class);
        skuMapper = mock(ProductSkuMapper.class);
        orderPurchaseFeignClient = mock(OrderPurchaseFeignClient.class);
        reviewService = new ReviewServiceImpl(reviewMapper, productMapper, skuMapper,
                objectMapper, orderPurchaseFeignClient);

        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setStatus(1);
        when(productMapper.selectById(PRODUCT_ID)).thenReturn(product);
    }

    private CreateReviewRequest request(Long orderId) {
        return new CreateReviewRequest(orderId, PRODUCT_ID, SKU_ID, 5, "很好", List.of());
    }

    private void stubEligible(Long... orderIds) {
        when(orderPurchaseFeignClient.purchaseEligibility(USER_ID, SKU_ID))
                .thenReturn(ApiResponse.ok(List.of(orderIds)));
    }

    @Test
    @DisplayName("已购（订单在资格列表中）→ 评价成功入库")
    void createReview_eligible_ok() {
        stubEligible(ORDER_ID, 8888L);
        when(reviewMapper.selectCount(any())).thenReturn(0L);
        when(skuMapper.selectById(SKU_ID)).thenReturn(null);
        org.mockito.Mockito.doAnswer(inv -> {
            ProductReview r = inv.getArgument(0);
            r.setId(1L);
            return 1;
        }).when(reviewMapper).insert(any(ProductReview.class));

        ReviewDTO dto = reviewService.createReview(USER_ID, request(ORDER_ID));

        assertThat(dto).isNotNull();
        ArgumentCaptor<ProductReview> captor = ArgumentCaptor.forClass(ProductReview.class);
        verify(reviewMapper).insert(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo(ORDER_ID);
        assertThat(captor.getValue().getSkuId()).isEqualTo(SKU_ID);
    }

    @Test
    @DisplayName("未购（该 SKU 无已完成订单）→ 拒绝")
    void createReview_neverPurchased_rejected() {
        stubEligible();

        assertThatThrownBy(() -> reviewService.createReview(USER_ID, request(ORDER_ID)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "REVIEW_NOT_ELIGIBLE");
        verify(reviewMapper, never()).insert(any(ProductReview.class));
    }

    @Test
    @DisplayName("他人订单（orderId 不在本人资格列表）→ 拒绝")
    void createReview_othersOrder_rejected() {
        stubEligible(7777L);

        assertThatThrownBy(() -> reviewService.createReview(USER_ID, request(ORDER_ID)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "REVIEW_NOT_ELIGIBLE");
    }

    @Test
    @DisplayName("订单服务不可用 → fail-closed 拒绝评价")
    void createReview_orderServiceDown_failsClosed() {
        when(orderPurchaseFeignClient.purchaseEligibility(anyLong(), anyLong())).thenReturn(null);

        assertThatThrownBy(() -> reviewService.createReview(USER_ID, request(ORDER_ID)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "REVIEW_NOT_ELIGIBLE");
    }

    @Test
    @DisplayName("订单服务显式失败（降级抛错）→ 异常透传拒绝")
    void createReview_orderServiceError_propagates() {
        when(orderPurchaseFeignClient.purchaseEligibility(anyLong(), anyLong()))
                .thenThrow(new BusinessException("ORDER_SERVICE_UNAVAILABLE", "订单服务暂不可用"));

        assertThatThrownBy(() -> reviewService.createReview(USER_ID, request(ORDER_ID)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "ORDER_SERVICE_UNAVAILABLE");
    }

    @Test
    @DisplayName("并发重复评价：预检竞态下的唯一键冲突转译为 REVIEW_ALREADY_EXISTS")
    void createReview_duplicateKey_translated() {
        stubEligible(ORDER_ID);
        when(reviewMapper.selectCount(any())).thenReturn(0L);
        when(reviewMapper.insert(any(ProductReview.class)))
                .thenThrow(new DuplicateKeyException("uk_user_order_product"));

        assertThatThrownBy(() -> reviewService.createReview(USER_ID, request(ORDER_ID)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "REVIEW_ALREADY_EXISTS");
    }
}
