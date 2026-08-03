package com.cloudmart.admin.controller;

import com.cloudmart.admin.dto.feign.*;
import com.cloudmart.admin.feign.*;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminBusinessControllerTest {

    private MockMvc mockMvc;
    private ProductFeignClient productFeignClient;
    private CategoryFeignClient categoryFeignClient;
    private OrderFeignClient orderFeignClient;
    private CouponFeignClient couponFeignClient;
    private SeckillActivityFeignClient seckillActivityFeignClient;
    private SeckillProductFeignClient seckillProductFeignClient;
    private BrandFeignClient brandFeignClient;

    @BeforeEach
    void setUp() {
        productFeignClient = mock(ProductFeignClient.class);
        categoryFeignClient = mock(CategoryFeignClient.class);
        orderFeignClient = mock(OrderFeignClient.class);
        couponFeignClient = mock(CouponFeignClient.class);
        seckillActivityFeignClient = mock(SeckillActivityFeignClient.class);
        seckillProductFeignClient = mock(SeckillProductFeignClient.class);
        brandFeignClient = mock(BrandFeignClient.class);

        AdminBusinessController controller = new AdminBusinessController(
                productFeignClient,
                categoryFeignClient,
                orderFeignClient,
                mock(MemberUserFeignClient.class),
                couponFeignClient,
                mock(InventoryFeignClient.class),
                mock(PaymentFeignClient.class),
                mock(NotificationFeignClient.class),
                seckillActivityFeignClient,
                seckillProductFeignClient,
                mock(CartFeignClient.class),
                mock(ReviewFeignClient.class),
                mock(MarketingFeignClient.class),
                mock(LiveFeignClient.class),
                mock(WmsFeignClient.class),
                mock(RiskFeignClient.class),
                mock(AiFeignClient.class),
                brandFeignClient
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listBrands_returnsBrandList() throws Exception {
        given(brandFeignClient.listBrands(any(Map.class)))
                .willReturn(ApiResponse.ok(List.of()));

        mockMvc.perform(get("/business/brands").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getBrand_returnsBrandDetail() throws Exception {
        given(brandFeignClient.getBrand(1L)).willReturn(ApiResponse.ok("brand"));

        mockMvc.perform(get("/business/brands/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void createBrand_brandCreatedSuccessfully() throws Exception {
        given(brandFeignClient.createBrand(any(Map.class)))
                .willReturn(ApiResponse.ok("brand"));

        mockMvc.perform(post("/business/brands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"TestBrand\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void updateBrand_brandUpdatedSuccessfully() throws Exception {
        given(brandFeignClient.updateBrand(anyLong(), any(Map.class)))
                .willReturn(ApiResponse.ok("brand"));

        mockMvc.perform(put("/business/brands/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"TestBrand\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void listProducts_returnsProductList() throws Exception {
        given(productFeignClient.searchProducts(any(ProductSearchRequest.class)))
                .willReturn(ApiResponse.ok(new ProductSearchResultDTO(List.of(), List.of(), List.of(), 0, 1, 10)));

        mockMvc.perform(get("/business/products").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getProduct_returnsProductDetail() throws Exception {
        ProductDTO product = new ProductDTO(1L, "Test", "desc", 1L, "cat", "brand", "img", 1, List.of(), LocalDateTime.now());
        given(productFeignClient.getProductById(1L))
                .willReturn(ApiResponse.ok(product));

        mockMvc.perform(get("/business/products/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void createProduct_productCreatedSuccessfully() throws Exception {
        ProductDTO product = new ProductDTO(1L, "Test", "desc", 1L, "cat", "brand", "img", 1, List.of(), LocalDateTime.now());
        given(productFeignClient.createProduct(any(CreateProductRequest.class)))
                .willReturn(ApiResponse.ok(product));

        mockMvc.perform(post("/business/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Product\",\"price\":99.99}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void updateProduct_productUpdatedSuccessfully() throws Exception {
        ProductDTO product = new ProductDTO(1L, "Updated", "desc", 1L, "cat", "brand", "img", 1, List.of(), LocalDateTime.now());
        given(productFeignClient.updateProduct(anyLong(), any(UpdateProductRequest.class)))
                .willReturn(ApiResponse.ok(product));

        mockMvc.perform(put("/business/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void deleteProduct_productDeletedSuccessfully() throws Exception {
        given(productFeignClient.deleteProduct(1L)).willReturn(ApiResponse.ok(null));

        mockMvc.perform(delete("/business/products/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void listCategories_returnsCategoryList() throws Exception {
        given(categoryFeignClient.listCategories()).willReturn(ApiResponse.ok(List.of()));

        mockMvc.perform(get("/business/categories").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void createCategory_categoryCreatedSuccessfully() throws Exception {
        CategoryDTO category = new CategoryDTO(1L, "电子产品", 0L, 1, "icon", 1);
        given(categoryFeignClient.createCategory("电子产品", null))
                .willReturn(ApiResponse.ok(category));

        mockMvc.perform(post("/business/categories")
                        .param("name", "电子产品")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void deleteCategory_categoryDeletedSuccessfully() throws Exception {
        given(categoryFeignClient.deleteCategory(1L)).willReturn(ApiResponse.ok(null));

        mockMvc.perform(delete("/business/categories/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void listOrders_returnsOrderList() throws Exception {
        given(orderFeignClient.listOrders(null, null, null, 1, 10))
                .willReturn(ApiResponse.ok(List.of()));

        mockMvc.perform(get("/business/orders").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getOrder_returnsOrderDetail() throws Exception {
        given(orderFeignClient.getOrderById(1L)).willReturn(ApiResponse.ok("order"));

        mockMvc.perform(get("/business/orders/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void shipOrder_orderShippedSuccessfully() throws Exception {
        given(orderFeignClient.shipOrder(1L)).willReturn(ApiResponse.ok("order"));

        mockMvc.perform(put("/business/orders/1/ship").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void cancelOrder_orderCancelledSuccessfully() throws Exception {
        given(orderFeignClient.cancelOrder(1L)).willReturn(ApiResponse.ok("order"));

        mockMvc.perform(put("/business/orders/1/cancel").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void approveRefund_refundApprovedSuccessfully() throws Exception {
        given(orderFeignClient.approveRefund(1L)).willReturn(ApiResponse.ok("order"));

        mockMvc.perform(put("/business/orders/1/refund/approve").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void rejectRefund_refundRejectedSuccessfully() throws Exception {
        given(orderFeignClient.rejectRefund(1L, "不符合退款条件"))
                .willReturn(ApiResponse.ok("order"));

        mockMvc.perform(put("/business/orders/1/refund/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rejectReason\":\"不符合退款条件\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void listCoupons_returnsCouponList() throws Exception {
        given(couponFeignClient.listTemplates(any(CouponSearchRequest.class)))
                .willReturn(ApiResponse.ok(List.of()));

        mockMvc.perform(get("/business/coupons").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getCoupon_returnsCouponDetail() throws Exception {
        CouponTemplateDTO coupon = new CouponTemplateDTO(1L, "满100减20", "FIXED",
                new BigDecimal("100"), new BigDecimal("20"), null, 1000, 800, 1,
                "FIXED_DATE", LocalDateTime.now(), LocalDateTime.now().plusDays(30),
                null, "ACTIVE", LocalDateTime.now());
        given(couponFeignClient.getTemplateById(1L))
                .willReturn(ApiResponse.ok(coupon));

        mockMvc.perform(get("/business/coupons/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void createCoupon_couponCreatedSuccessfully() throws Exception {
        CouponTemplateDTO coupon = new CouponTemplateDTO(1L, "满100减20", "FIXED",
                new BigDecimal("100"), new BigDecimal("20"), null, 1000, 800, 1,
                "FIXED_DATE", LocalDateTime.now(), LocalDateTime.now().plusDays(30),
                null, "ACTIVE", LocalDateTime.now());
        given(couponFeignClient.createTemplate(any(CreateCouponTemplateRequest.class)))
                .willReturn(ApiResponse.ok(coupon));

        mockMvc.perform(post("/business/coupons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"满100减20\",\"type\":\"FIXED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void disableCoupon_couponDisabledSuccessfully() throws Exception {
        CouponTemplateDTO coupon = new CouponTemplateDTO(1L, "满100减20", "FIXED",
                new BigDecimal("100"), new BigDecimal("20"), null, 1000, 800, 1,
                "FIXED_DATE", LocalDateTime.now(), LocalDateTime.now().plusDays(30),
                null, "DISABLED", LocalDateTime.now());
        given(couponFeignClient.disableTemplate(1L))
                .willReturn(ApiResponse.ok(coupon));

        mockMvc.perform(put("/business/coupons/1/disable").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void enableCoupon_couponEnabledSuccessfully() throws Exception {
        CouponTemplateDTO coupon = new CouponTemplateDTO(1L, "满100减20", "FIXED",
                new BigDecimal("100"), new BigDecimal("20"), null, 1000, 800, 1,
                "FIXED_DATE", LocalDateTime.now(), LocalDateTime.now().plusDays(30),
                null, "ACTIVE", LocalDateTime.now());
        given(couponFeignClient.enableTemplate(1L))
                .willReturn(ApiResponse.ok(coupon));

        mockMvc.perform(put("/business/coupons/1/enable").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void listSeckillActivities_returnsActivityList() throws Exception {
        given(seckillActivityFeignClient.listActivities(null))
                .willReturn(ApiResponse.ok(List.of()));

        mockMvc.perform(get("/business/seckill/activities").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getSeckillActivity_returnsActivityDetail() throws Exception {
        SeckillActivityDTO activity = new SeckillActivityDTO(1L, "限时秒杀", "描述",
                LocalDateTime.now(), LocalDateTime.now().plusHours(2), "ACTIVE", LocalDateTime.now());
        given(seckillActivityFeignClient.getActivity(1L))
                .willReturn(ApiResponse.ok(activity));

        mockMvc.perform(get("/business/seckill/activities/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void createSeckillActivity_activityCreatedSuccessfully() throws Exception {
        SeckillActivityDTO activity = new SeckillActivityDTO(1L, "限时秒杀", "描述",
                LocalDateTime.now(), LocalDateTime.now().plusHours(2), "PENDING", LocalDateTime.now());
        given(seckillActivityFeignClient.createActivity(any(CreateActivityRequest.class)))
                .willReturn(ApiResponse.ok(activity));

        mockMvc.perform(post("/business/seckill/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"限时秒杀\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void updateSeckillActivityStatus_statusUpdatedSuccessfully() throws Exception {
        SeckillActivityDTO activity = new SeckillActivityDTO(1L, "限时秒杀", "描述",
                LocalDateTime.now(), LocalDateTime.now().plusHours(2), "ACTIVE", LocalDateTime.now());
        given(seckillActivityFeignClient.updateActivityStatus(1L, "ACTIVE"))
                .willReturn(ApiResponse.ok(activity));

        mockMvc.perform(put("/business/seckill/activities/1/status")
                        .param("status", "ACTIVE")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void listSeckillProducts_returnsProductList() throws Exception {
        given(seckillProductFeignClient.listProductsByActivity(1L))
                .willReturn(ApiResponse.ok(List.of()));

        mockMvc.perform(get("/business/seckill/products/activity/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void addSeckillProduct_productAddedSuccessfully() throws Exception {
        SeckillProductDTO product = new SeckillProductDTO(1L, 1L, 1L,
                new BigDecimal("99.00"), new BigDecimal("199.00"), 100, 80, 1,
                "ACTIVE", LocalDateTime.now());
        given(seckillProductFeignClient.addProduct(anyLong(), any(AddSeckillProductRequest.class)))
                .willReturn(ApiResponse.ok(product));

        mockMvc.perform(post("/business/seckill/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skuId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
