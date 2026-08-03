package com.cloudmart.admin.controller;

import com.cloudmart.admin.dto.feign.*;
import com.cloudmart.admin.feign.AiFeignClient;
import com.cloudmart.admin.feign.BrandFeignClient;
import com.cloudmart.admin.feign.CartFeignClient;
import com.cloudmart.admin.feign.CategoryFeignClient;
import com.cloudmart.admin.feign.CouponFeignClient;
import com.cloudmart.admin.feign.InventoryFeignClient;
import com.cloudmart.admin.feign.LiveFeignClient;
import com.cloudmart.admin.feign.MarketingFeignClient;
import com.cloudmart.admin.feign.MemberUserFeignClient;
import com.cloudmart.admin.feign.NotificationFeignClient;
import com.cloudmart.admin.feign.OrderFeignClient;
import com.cloudmart.admin.feign.PaymentFeignClient;
import com.cloudmart.admin.feign.ProductFeignClient;
import com.cloudmart.admin.feign.ReviewFeignClient;
import com.cloudmart.admin.feign.RiskFeignClient;
import com.cloudmart.admin.feign.SeckillActivityFeignClient;
import com.cloudmart.admin.feign.SeckillProductFeignClient;
import com.cloudmart.admin.feign.WmsFeignClient;
import com.cloudmart.common.annotation.OperLog;
import com.cloudmart.common.annotation.RequiresPermission;
import com.cloudmart.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/business")
@Tag(name = "业务管理", description = "管理后台对业务模块的代理接口")
public class AdminBusinessController {

    private final ProductFeignClient productFeignClient;
    private final CategoryFeignClient categoryFeignClient;
    private final OrderFeignClient orderFeignClient;
    private final MemberUserFeignClient memberUserFeignClient;
    private final CouponFeignClient couponFeignClient;
    private final InventoryFeignClient inventoryFeignClient;
    private final PaymentFeignClient paymentFeignClient;
    private final NotificationFeignClient notificationFeignClient;
    private final SeckillActivityFeignClient seckillActivityFeignClient;
    private final SeckillProductFeignClient seckillProductFeignClient;
    private final CartFeignClient cartFeignClient;
    private final ReviewFeignClient reviewFeignClient;
    private final MarketingFeignClient marketingFeignClient;
    private final LiveFeignClient liveFeignClient;
    private final WmsFeignClient wmsFeignClient;
    private final RiskFeignClient riskFeignClient;
    private final AiFeignClient aiFeignClient;
    private final BrandFeignClient brandFeignClient;

    public AdminBusinessController(ProductFeignClient productFeignClient,
                                   CategoryFeignClient categoryFeignClient,
                                   OrderFeignClient orderFeignClient,
                                   MemberUserFeignClient memberUserFeignClient,
                                   CouponFeignClient couponFeignClient,
                                   InventoryFeignClient inventoryFeignClient,
                                   PaymentFeignClient paymentFeignClient,
                                   NotificationFeignClient notificationFeignClient,
                                   SeckillActivityFeignClient seckillActivityFeignClient,
                                   SeckillProductFeignClient seckillProductFeignClient,
                                   CartFeignClient cartFeignClient,
                                   ReviewFeignClient reviewFeignClient,
                                   MarketingFeignClient marketingFeignClient,
                                   LiveFeignClient liveFeignClient,
                                   WmsFeignClient wmsFeignClient,
                                   RiskFeignClient riskFeignClient,
                                   AiFeignClient aiFeignClient,
                                   BrandFeignClient brandFeignClient) {
        this.productFeignClient = productFeignClient;
        this.categoryFeignClient = categoryFeignClient;
        this.orderFeignClient = orderFeignClient;
        this.memberUserFeignClient = memberUserFeignClient;
        this.couponFeignClient = couponFeignClient;
        this.inventoryFeignClient = inventoryFeignClient;
        this.paymentFeignClient = paymentFeignClient;
        this.notificationFeignClient = notificationFeignClient;
        this.seckillActivityFeignClient = seckillActivityFeignClient;
        this.seckillProductFeignClient = seckillProductFeignClient;
        this.cartFeignClient = cartFeignClient;
        this.reviewFeignClient = reviewFeignClient;
        this.marketingFeignClient = marketingFeignClient;
        this.liveFeignClient = liveFeignClient;
        this.wmsFeignClient = wmsFeignClient;
        this.riskFeignClient = riskFeignClient;
        this.aiFeignClient = aiFeignClient;
        this.brandFeignClient = brandFeignClient;
    }

    // ==================== 品牌 ====================

    @GetMapping("/brands")
    @RequiresPermission("business:brand:list")
    @Operation(summary = "品牌列表", description = "查询品牌列表")
    public ApiResponse<Object> listBrands(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        Map<String, Object> params = new java.util.HashMap<>();
        if (name != null) params.put("name", name);
        if (status != null) params.put("status", status);
        params.put("page", page);
        params.put("size", pageSize);
        return brandFeignClient.listBrands(params);
    }

    @GetMapping("/brands/{id}")
    @RequiresPermission("business:brand:query")
    @Operation(summary = "品牌详情", description = "查询品牌详情")
    public ApiResponse<Object> getBrand(@PathVariable Long id) {
        return brandFeignClient.getBrand(id);
    }

    @PostMapping("/brands")
    @OperLog(title = "品牌管理", businessType = 1)
    @RequiresPermission("business:brand:add")
    @Operation(summary = "创建品牌", description = "创建新品牌")
    public ApiResponse<Object> createBrand(@RequestBody Map<String, Object> body) {
        return brandFeignClient.createBrand(body);
    }

    @PutMapping("/brands/{id}")
    @OperLog(title = "品牌管理", businessType = 2)
    @RequiresPermission("business:brand:edit")
    @Operation(summary = "更新品牌", description = "更新品牌信息")
    public ApiResponse<Object> updateBrand(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return brandFeignClient.updateBrand(id, body);
    }

    @DeleteMapping("/brands/{id}")
    @OperLog(title = "品牌管理", businessType = 3)
    @RequiresPermission("business:brand:remove")
    @Operation(summary = "删除品牌", description = "删除品牌")
    public ApiResponse<Void> deleteBrand(@PathVariable Long id) {
        return brandFeignClient.deleteBrand(id);
    }

    // ==================== 商品 ====================

    @GetMapping("/products")
    @RequiresPermission("business:product:list")
    @Operation(summary = "商品列表", description = "查询商品列表，支持搜索")
    public ApiResponse<List<ProductDTO>> listProducts(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "minPrice", required = false) java.math.BigDecimal minPrice,
            @RequestParam(value = "maxPrice", required = false) java.math.BigDecimal maxPrice,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        ProductSearchRequest request = new ProductSearchRequest(keyword, categoryId, minPrice, maxPrice, sort, status, page, pageSize);
        ApiResponse<ProductSearchResultDTO> response = productFeignClient.searchProducts(request);
        if (response.success() && response.data() != null) {
            ProductSearchResultDTO result = response.data();
            return ApiResponse.ok(result.products(), result.page(), result.size(), result.total());
        }
        return ApiResponse.fail("PRODUCT_SERVICE_UNAVAILABLE", "商品服务不可用，请稍后重试");
    }

    @GetMapping("/products/{id}")
    @RequiresPermission("business:product:query")
    @Operation(summary = "商品详情", description = "查询商品详情")
    public ApiResponse<ProductDTO> getProduct(@PathVariable Long id) {
        return productFeignClient.getProductById(id);
    }

    @PostMapping("/products")
    @OperLog(title = "商品管理", businessType = 1)
    @RequiresPermission("business:product:add")
    @Operation(summary = "创建商品", description = "创建新商品")
    public ApiResponse<ProductDTO> createProduct(@RequestBody CreateProductRequest request) {
        return productFeignClient.createProduct(request);
    }

    @PutMapping("/products/{id}")
    @OperLog(title = "商品管理", businessType = 2)
    @RequiresPermission("business:product:edit")
    @Operation(summary = "更新商品", description = "更新商品信息")
    public ApiResponse<ProductDTO> updateProduct(@PathVariable Long id, @RequestBody UpdateProductRequest request) {
        return productFeignClient.updateProduct(id, request);
    }

    @DeleteMapping("/products/{id}")
    @OperLog(title = "商品管理", businessType = 3)
    @RequiresPermission("business:product:remove")
    @Operation(summary = "删除商品", description = "删除商品")
    public ApiResponse<Void> deleteProduct(@PathVariable Long id) {
        return productFeignClient.deleteProduct(id);
    }

    // ==================== 分类 ====================

    @GetMapping("/categories")
    @RequiresPermission("business:product:list")
    @Operation(summary = "分类列表", description = "查询商品分类列表")
    public ApiResponse<List<CategoryDTO>> listCategories() {
        return categoryFeignClient.listCategories();
    }

    @PostMapping("/categories")
    @OperLog(title = "分类管理", businessType = 1)
    @RequiresPermission("business:product:add")
    @Operation(summary = "创建分类", description = "创建商品分类")
    public ApiResponse<CategoryDTO> createCategory(@RequestParam String name, @RequestParam(required = false) Long parentId) {
        return categoryFeignClient.createCategory(name, parentId);
    }

    @PutMapping("/categories/{id}")
    @OperLog(title = "分类管理", businessType = 2)
    @RequiresPermission("business:product:edit")
    @Operation(summary = "更新分类", description = "更新商品分类信息")
    public ApiResponse<CategoryDTO> updateCategory(@PathVariable Long id, @RequestParam String name,
                                                    @RequestParam(required = false) Long parentId,
                                                    @RequestParam(required = false) Integer sortOrder,
                                                    @RequestParam(required = false) Integer status) {
        return categoryFeignClient.updateCategory(id, name, parentId, sortOrder, status);
    }

    @DeleteMapping("/categories/{id}")
    @OperLog(title = "分类管理", businessType = 3)
    @RequiresPermission("business:product:remove")
    @Operation(summary = "删除分类", description = "删除商品分类")
    public ApiResponse<Void> deleteCategory(@PathVariable Long id) {
        return categoryFeignClient.deleteCategory(id);
    }

    // ==================== 订单 ====================

    @GetMapping("/orders")
    @RequiresPermission("business:order:list")
    @Operation(summary = "订单列表", description = "查询所有订单，支持按状态、订单号筛选")
    public ApiResponse<Object> listOrders(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "orderNo", required = false) String orderNo,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int size) {
        return orderFeignClient.listOrders(status, userId, orderNo, page, size);
    }

    @GetMapping("/orders/{orderId}")
    @RequiresPermission("business:order:query")
    @Operation(summary = "订单详情", description = "查询订单详情")
    public ApiResponse<Object> getOrder(@PathVariable Long orderId) {
        return orderFeignClient.getOrderById(orderId);
    }

    @PutMapping("/orders/{orderId}/ship")
    @OperLog(title = "订单管理", businessType = 2)
    @RequiresPermission("business:order:ship")
    @Operation(summary = "订单发货", description = "将已支付订单标记为已发货")
    public ApiResponse<Object> shipOrder(@PathVariable Long orderId) {
        return orderFeignClient.shipOrder(orderId);
    }

    @PutMapping("/orders/{orderId}/cancel")
    @OperLog(title = "订单管理", businessType = 2)
    @RequiresPermission("business:order:cancel")
    @Operation(summary = "取消订单", description = "管理员取消订单")
    public ApiResponse<Object> cancelOrder(@PathVariable Long orderId) {
        return orderFeignClient.cancelOrder(orderId);
    }

    @PutMapping("/orders/{orderId}/refund/approve")
    @OperLog(title = "订单管理", businessType = 2)
    @RequiresPermission("business:order:refund")
    @Operation(summary = "审批通过退款", description = "管理员审批通过退款申请")
    public ApiResponse<Object> approveRefund(@PathVariable Long orderId) {
        return orderFeignClient.approveRefund(orderId);
    }

    @PutMapping("/orders/{orderId}/refund/reject")
    @OperLog(title = "订单管理", businessType = 2)
    @RequiresPermission("business:order:refund")
    @Operation(summary = "拒绝退款", description = "管理员拒绝退款申请")
    public ApiResponse<Object> rejectRefund(
            @PathVariable Long orderId,
            @RequestBody Map<String, String> body) {
        return orderFeignClient.rejectRefund(orderId, body.get("rejectReason"));
    }

    @GetMapping("/orders/stats/today")
    @RequiresPermission("business:order:list")
    @Operation(summary = "今日订单统计", description = "返回今日订单数量和营收总额")
    public ApiResponse<OrderTodayStatsResponse> getTodayOrderStats() {
        return orderFeignClient.getTodayStats();
    }

    // ==================== 会员 ====================

    @GetMapping("/members")
    @RequiresPermission("business:member:list")
    @Operation(summary = "会员列表", description = "查询前台用户列表")
    public ApiResponse<List<UserDTO>> listMembers(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int size) {
        return memberUserFeignClient.listUsers(page, size);
    }

    @GetMapping("/members/{id}")
    @RequiresPermission("business:member:query")
    @Operation(summary = "会员详情", description = "查询前台用户详情")
    public ApiResponse<UserDTO> getMember(@PathVariable Long id) {
        return memberUserFeignClient.getUserById(id);
    }

    @PutMapping("/members/{id}")
    @OperLog(title = "会员管理", businessType = 2)
    @RequiresPermission("business:member:edit")
    @Operation(summary = "编辑会员信息", description = "管理员编辑会员昵称、手机号、邮箱")
    public ApiResponse<UserDTO> updateMember(@PathVariable Long id, @RequestBody AdminUpdateUserRequest request) {
        return memberUserFeignClient.updateUser(id, request);
    }

    @PutMapping("/members/{id}/status")
    @OperLog(title = "会员管理", businessType = 2)
    @RequiresPermission("business:member:edit")
    @Operation(summary = "切换会员状态", description = "启用或禁用会员账号")
    public ApiResponse<Void> toggleMemberStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        return memberUserFeignClient.toggleUserStatus(id, body.get("status"));
    }

    // ==================== 优惠券 ====================

    @GetMapping("/coupons")
    @RequiresPermission("business:coupon:list")
    @Operation(summary = "优惠券列表", description = "查询优惠券模板列表")
    public ApiResponse<List<CouponTemplateDTO>> listCoupons(@SpringQueryMap CouponSearchRequest request) {
        return couponFeignClient.listTemplates(request);
    }

    @GetMapping("/coupons/{id}")
    @RequiresPermission("business:coupon:query")
    @Operation(summary = "优惠券详情", description = "查询优惠券模板详情")
    public ApiResponse<CouponTemplateDTO> getCoupon(@PathVariable Long id) {
        return couponFeignClient.getTemplateById(id);
    }

    @PostMapping("/coupons")
    @OperLog(title = "优惠券管理", businessType = 1)
    @RequiresPermission("business:coupon:add")
    @Operation(summary = "创建优惠券", description = "创建优惠券模板")
    public ApiResponse<CouponTemplateDTO> createCoupon(@RequestBody CreateCouponTemplateRequest request) {
        return couponFeignClient.createTemplate(request);
    }

    @PutMapping("/coupons/{id}")
    @OperLog(title = "优惠券管理", businessType = 2)
    @RequiresPermission("business:coupon:edit")
    @Operation(summary = "更新优惠券", description = "更新优惠券信息")
    public ApiResponse<Object> updateCoupon(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return couponFeignClient.updateTemplate(id, body);
    }

    @PutMapping("/coupons/{id}/disable")
    @OperLog(title = "优惠券管理", businessType = 2)
    @RequiresPermission("business:coupon:disable")
    @Operation(summary = "禁用优惠券", description = "禁用优惠券模板")
    public ApiResponse<CouponTemplateDTO> disableCoupon(@PathVariable Long id) {
        return couponFeignClient.disableTemplate(id);
    }

    @PutMapping("/coupons/{id}/enable")
    @OperLog(title = "优惠券管理", businessType = 2)
    @RequiresPermission("business:coupon:edit")
    @Operation(summary = "启用优惠券", description = "启用已禁用的优惠券模板")
    public ApiResponse<CouponTemplateDTO> enableCoupon(@PathVariable Long id) {
        return couponFeignClient.enableTemplate(id);
    }

    @DeleteMapping("/coupons/{id}")
    @OperLog(title = "优惠券管理", businessType = 3)
    @RequiresPermission("business:coupon:remove")
    @Operation(summary = "删除优惠券", description = "删除优惠券模板")
    public ApiResponse<Void> deleteCoupon(@PathVariable Long id) {
        return couponFeignClient.deleteCoupon(id);
    }

    // ==================== 库存 ====================

    @GetMapping("/inventory")
    @RequiresPermission("business:inventory:list")
    @Operation(summary = "库存列表", description = "分页查询库存列表，支持按商品ID筛选")
    public ApiResponse<List<InventoryDTO>> listInventory(@SpringQueryMap InventorySearchRequest request) {
        return inventoryFeignClient.listInventory(request);
    }

    @GetMapping("/inventory/{skuId}")
    @RequiresPermission("business:inventory:list")
    @Operation(summary = "查询库存", description = "根据SKU ID查询库存")
    public ApiResponse<InventoryDTO> getInventory(@PathVariable Long skuId) {
        return inventoryFeignClient.getInventory(skuId);
    }

    @PostMapping("/inventory/init")
    @OperLog(title = "库存管理", businessType = 1)
    @RequiresPermission("business:inventory:init")
    @Operation(summary = "初始化库存", description = "初始化SKU库存")
    public ApiResponse<Void> initInventory(@RequestBody Map<String, Object> body) {
        return inventoryFeignClient.initStock(
                ((Number) body.get("skuId")).longValue(),
                ((Number) body.get("productId")).longValue(),
                ((Number) body.get("stock")).intValue());
    }

    // ==================== 支付 ====================

    @GetMapping("/payments")
    @RequiresPermission("business:payment:list")
    @Operation(summary = "支付列表", description = "分页查询支付记录，支持按状态筛选")
    public ApiResponse<List<PaymentDTO>> listPayments(@SpringQueryMap PaymentSearchRequest request) {
        return paymentFeignClient.listPayments(request);
    }

    @GetMapping("/payments/order/{orderId}")
    @RequiresPermission("business:payment:list")
    @Operation(summary = "查询支付记录", description = "根据订单ID查询支付记录")
    public ApiResponse<PaymentDTO> getPaymentByOrder(@PathVariable Long orderId) {
        return paymentFeignClient.getPaymentByOrderId(orderId);
    }

    @PostMapping("/payments/{paymentId}/refund")
    @OperLog(title = "支付管理", businessType = 1)
    @RequiresPermission("business:payment:refund")
    @Operation(summary = "退款", description = "对已支付订单发起退款")
    public ApiResponse<PaymentDTO> refundPayment(@PathVariable Long paymentId) {
        return paymentFeignClient.refund(paymentId);
    }

    // ==================== 通知 ====================

    @GetMapping("/notifications")
    @RequiresPermission("business:notification:list")
    @Operation(summary = "通知列表", description = "查询通知列表")
    public ApiResponse<Object> listNotifications(@SpringQueryMap NotificationSearchRequest request) {
        return notificationFeignClient.listNotifications(request);
    }

    @PostMapping("/notifications")
    @OperLog(title = "通知管理", businessType = 1)
    @RequiresPermission("business:notification:send")
    @Operation(summary = "发送通知", description = "发送通知给用户")
    public ApiResponse<Object> sendNotification(@RequestBody SendNotificationRequest request) {
        return notificationFeignClient.sendNotification(request);
    }

    // ==================== 秒杀活动 ====================

    @GetMapping("/seckill/activities")
    @RequiresPermission("business:seckill:list")
    @Operation(summary = "秒杀活动列表", description = "查询秒杀活动列表")
    public ApiResponse<List<SeckillActivityDTO>> listSeckillActivities(
            @RequestParam(value = "status", required = false) String status) {
        return seckillActivityFeignClient.listActivities(status);
    }

    @GetMapping("/seckill/activities/{activityId}")
    @RequiresPermission("business:seckill:query")
    @Operation(summary = "秒杀活动详情", description = "查询秒杀活动详情")
    public ApiResponse<SeckillActivityDTO> getSeckillActivity(@PathVariable Long activityId) {
        return seckillActivityFeignClient.getActivity(activityId);
    }

    @PostMapping("/seckill/activities")
    @OperLog(title = "秒杀活动", businessType = 1)
    @RequiresPermission("business:seckill:add")
    @Operation(summary = "创建秒杀活动", description = "创建新的秒杀活动")
    public ApiResponse<SeckillActivityDTO> createSeckillActivity(@RequestBody CreateActivityRequest request) {
        return seckillActivityFeignClient.createActivity(request);
    }

    @PutMapping("/seckill/activities/{activityId}")
    @OperLog(title = "秒杀活动", businessType = 2)
    @RequiresPermission("business:seckill:edit")
    @Operation(summary = "更新秒杀活动", description = "更新秒杀活动信息")
    public ApiResponse<Object> updateSeckillActivity(@PathVariable Long activityId, @RequestBody Map<String, Object> body) {
        return seckillActivityFeignClient.updateActivity(activityId, body);
    }

    @PutMapping("/seckill/activities/{activityId}/status")
    @OperLog(title = "秒杀活动", businessType = 2)
    @RequiresPermission("business:seckill:edit")
    @Operation(summary = "更新活动状态", description = "手动更新秒杀活动状态")
    public ApiResponse<SeckillActivityDTO> updateSeckillActivityStatus(
            @PathVariable Long activityId, @RequestParam String status) {
        return seckillActivityFeignClient.updateActivityStatus(activityId, status);
    }

    @DeleteMapping("/seckill/activities/{id}")
    @OperLog(title = "秒杀活动", businessType = 3)
    @RequiresPermission("business:seckill:remove")
    @Operation(summary = "删除秒杀活动", description = "删除秒杀活动")
    public ApiResponse<Void> deleteSeckillActivity(@PathVariable Long id) {
        return seckillActivityFeignClient.deleteActivity(id);
    }

    // ==================== 秒杀商品 ====================

    @GetMapping("/seckill/products/activity/{activityId}")
    @RequiresPermission("business:seckill:list")
    @Operation(summary = "活动秒杀商品", description = "查询指定活动下的秒杀商品")
    public ApiResponse<List<SeckillProductDTO>> listSeckillProducts(@PathVariable Long activityId) {
        return seckillProductFeignClient.listProductsByActivity(activityId);
    }

    @GetMapping("/seckill/products/{productId}")
    @RequiresPermission("business:seckill:query")
    @Operation(summary = "秒杀商品详情", description = "查询秒杀商品详情")
    public ApiResponse<SeckillProductDTO> getSeckillProduct(@PathVariable Long productId) {
        return seckillProductFeignClient.getProduct(productId);
    }

    @PostMapping("/seckill/products/{activityId}")
    @OperLog(title = "秒杀商品", businessType = 1)
    @RequiresPermission("business:seckill:add")
    @Operation(summary = "添加秒杀商品", description = "为指定活动添加秒杀商品")
    public ApiResponse<SeckillProductDTO> addSeckillProduct(
            @PathVariable Long activityId, @RequestBody AddSeckillProductRequest request) {
        return seckillProductFeignClient.addProduct(activityId, request);
    }

    @DeleteMapping("/seckill/products/{productId}")
    @OperLog(title = "秒杀商品", businessType = 3)
    @RequiresPermission("business:seckill:remove")
    @Operation(summary = "删除秒杀商品", description = "删除秒杀商品")
    public ApiResponse<Void> deleteSeckillProduct(@PathVariable Long productId) {
        return seckillProductFeignClient.deleteProduct(productId);
    }

    // ==================== 购物车 ====================

    @GetMapping("/carts/{userId}")
    @RequiresPermission("business:cart:query")
    @Operation(summary = "查看用户购物车", description = "管理员查看指定用户的购物车")
    public ApiResponse<Object> getCartByUserId(@PathVariable Long userId) {
        return cartFeignClient.getCartByUserId(userId);
    }

    @DeleteMapping("/carts/{userId}/items/{skuId}")
    @OperLog(title = "购物车管理", businessType = 3)
    @RequiresPermission("business:cart:edit")
    @Operation(summary = "删除购物车项", description = "管理员删除用户购物车中的指定商品")
    public ApiResponse<Void> removeCartItem(@PathVariable Long userId, @PathVariable Long skuId) {
        return cartFeignClient.removeCartItem(userId, skuId);
    }

    @DeleteMapping("/carts/{userId}/clear")
    @OperLog(title = "购物车管理", businessType = 9)
    @RequiresPermission("business:cart:edit")
    @Operation(summary = "清空购物车", description = "管理员清空指定用户的购物车")
    public ApiResponse<Void> clearCart(@PathVariable Long userId) {
        return cartFeignClient.clearCart(userId);
    }

    // ==================== 评价 ====================

    @GetMapping("/reviews")
    @RequiresPermission("business:review:list")
    @Operation(summary = "评价列表", description = "查询所有评价，支持按商品ID和状态筛选")
    public ApiResponse<Object> listReviews(@SpringQueryMap ReviewSearchRequest request) {
        return reviewFeignClient.listReviews(request);
    }

    @GetMapping("/reviews/{id}")
    @RequiresPermission("business:review:query")
    @Operation(summary = "评价详情", description = "查询评价详情")
    public ApiResponse<Object> getReview(@PathVariable Long id) {
        return reviewFeignClient.getReview(id);
    }

    @PutMapping("/reviews/{id}/status")
    @OperLog(title = "评价管理", businessType = 2)
    @RequiresPermission("business:review:edit")
    @Operation(summary = "更新评价状态", description = "隐藏或显示评价")
    public ApiResponse<Void> updateReviewStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        return reviewFeignClient.updateReviewStatus(id, body.get("status"));
    }

    @DeleteMapping("/reviews/{id}")
    @OperLog(title = "评价管理", businessType = 3)
    @RequiresPermission("business:review:remove")
    @Operation(summary = "删除评价", description = "删除评价")
    public ApiResponse<Void> deleteReview(@PathVariable Long id) {
        return reviewFeignClient.deleteReview(id);
    }

    @GetMapping("/reviews/stats/{productId}")
    @RequiresPermission("business:review:list")
    @Operation(summary = "评价统计", description = "查询商品评价统计")
    public ApiResponse<Object> getReviewStats(@PathVariable Long productId) {
        return reviewFeignClient.getReviewStats(productId);
    }

    // ==================== 拼团活动 ====================

    @GetMapping("/marketing/group/activities")
    @RequiresPermission("business:marketing:list")
    @Operation(summary = "拼团活动列表", description = "查询拼团活动列表")
    public ApiResponse<Object> listGroupActivities(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        GroupActivitySearchRequest request = new GroupActivitySearchRequest(status, page, pageSize);
        return marketingFeignClient.listGroupActivities(request);
    }

    @PostMapping("/marketing/group/activities")
    @OperLog(title = "拼团活动", businessType = 1)
    @RequiresPermission("business:marketing:add")
    @Operation(summary = "创建拼团活动", description = "创建新的拼团活动")
    public ApiResponse<GroupActivityDTO> createGroupActivity(@RequestBody CreateGroupActivityRequest request) {
        return marketingFeignClient.createGroupActivity(request);
    }

    @PutMapping("/marketing/group/activities/{id}")
    @OperLog(title = "拼团活动", businessType = 2)
    @RequiresPermission("business:marketing:edit")
    @Operation(summary = "更新拼团活动", description = "更新拼团活动信息")
    public ApiResponse<Object> updateGroupActivity(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return marketingFeignClient.updateGroupActivity(id, body);
    }

    @PutMapping("/marketing/group/activities/{id}/enable")
    @OperLog(title = "拼团活动", businessType = 2)
    @RequiresPermission("business:marketing:edit")
    @Operation(summary = "启用拼团活动", description = "启用拼团活动")
    public ApiResponse<GroupActivityDTO> enableGroupActivity(@PathVariable Long id) {
        return marketingFeignClient.enableGroupActivity(id);
    }

    @PutMapping("/marketing/group/activities/{id}/disable")
    @OperLog(title = "拼团活动", businessType = 2)
    @RequiresPermission("business:marketing:edit")
    @Operation(summary = "停用拼团活动", description = "停用拼团活动")
    public ApiResponse<GroupActivityDTO> disableGroupActivity(@PathVariable Long id) {
        return marketingFeignClient.disableGroupActivity(id);
    }

    @DeleteMapping("/marketing/group/activities/{id}")
    @OperLog(title = "拼团活动", businessType = 3)
    @RequiresPermission("business:marketing:remove")
    @Operation(summary = "删除拼团活动", description = "删除拼团活动")
    public ApiResponse<Void> deleteGroupActivity(@PathVariable Long id) {
        return marketingFeignClient.deleteGroupActivity(id);
    }

    @GetMapping("/marketing/group/orders")
    @RequiresPermission("business:marketing:list")
    @Operation(summary = "拼团组列表", description = "查询拼团组列表")
    public ApiResponse<Object> listGroupOrders(
            @RequestParam(value = "activityId", required = false) Long activityId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        GroupOrderSearchRequest request = new GroupOrderSearchRequest(activityId, status, page, pageSize);
        return marketingFeignClient.listGroupOrders(request);
    }

    // ==================== 阶梯满减 ====================

    @GetMapping("/marketing/tiered/promotions")
    @RequiresPermission("business:marketing:list")
    @Operation(summary = "满减活动列表", description = "查询阶梯满减活动列表")
    public ApiResponse<Object> listTieredPromotions(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        TieredPromotionSearchRequest request = new TieredPromotionSearchRequest(status, page, pageSize);
        return marketingFeignClient.listTieredPromotions(request);
    }

    @PostMapping("/marketing/tiered/promotions")
    @OperLog(title = "满减活动", businessType = 1)
    @RequiresPermission("business:marketing:add")
    @Operation(summary = "创建满减活动", description = "创建阶梯满减活动")
    public ApiResponse<TieredPromotionDTO> createTieredPromotion(@RequestBody CreateTieredPromotionRequest request) {
        return marketingFeignClient.createTieredPromotion(request);
    }

    @PutMapping("/marketing/tiered/promotions/{id}")
    @OperLog(title = "满减活动", businessType = 2)
    @RequiresPermission("business:marketing:edit")
    @Operation(summary = "更新满减活动", description = "更新阶梯满减活动信息")
    public ApiResponse<Object> updateTieredPromotion(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return marketingFeignClient.updateTieredPromotion(id, body);
    }

    @PutMapping("/marketing/tiered/promotions/{id}/enable")
    @OperLog(title = "满减活动", businessType = 2)
    @RequiresPermission("business:marketing:edit")
    @Operation(summary = "启用满减活动", description = "启用阶梯满减活动")
    public ApiResponse<TieredPromotionDTO> enableTieredPromotion(@PathVariable Long id) {
        return marketingFeignClient.enableTieredPromotion(id);
    }

    @PutMapping("/marketing/tiered/promotions/{id}/disable")
    @OperLog(title = "满减活动", businessType = 2)
    @RequiresPermission("business:marketing:edit")
    @Operation(summary = "停用满减活动", description = "停用阶梯满减活动")
    public ApiResponse<TieredPromotionDTO> disableTieredPromotion(@PathVariable Long id) {
        return marketingFeignClient.disableTieredPromotion(id);
    }

    @GetMapping("/marketing/tiered/promotions/{id}")
    @RequiresPermission("business:marketing:query")
    @Operation(summary = "满减活动详情", description = "查询阶梯满减活动详情")
    public ApiResponse<TieredPromotionDTO> getTieredPromotion(@PathVariable Long id) {
        return marketingFeignClient.getTieredPromotion(id);
    }

    @DeleteMapping("/marketing/tiered/promotions/{id}")
    @OperLog(title = "满减活动", businessType = 3)
    @RequiresPermission("business:marketing:remove")
    @Operation(summary = "删除满减活动", description = "删除阶梯满减活动")
    public ApiResponse<Void> deleteTieredPromotion(@PathVariable Long id) {
        return marketingFeignClient.deleteTieredPromotion(id);
    }

    // ==================== 直播管理 ====================

    @GetMapping("/live/rooms")
    @RequiresPermission("business:live:list")
    @Operation(summary = "直播间列表", description = "查询直播间列表")
    public ApiResponse<Object> listLiveRooms(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        LiveRoomSearchRequest request = new LiveRoomSearchRequest(status, page, pageSize);
        return liveFeignClient.listRooms(request);
    }

    @PostMapping("/live/rooms")
    @OperLog(title = "直播管理", businessType = 1)
    @RequiresPermission("business:live:add")
    @Operation(summary = "创建直播间", description = "创建新的直播间")
    public ApiResponse<LiveRoomDTO> createLiveRoom(@RequestBody CreateLiveRoomRequest request) {
        return liveFeignClient.createRoom(request);
    }

    @PutMapping("/live/rooms/{roomId}")
    @OperLog(title = "直播管理", businessType = 2)
    @RequiresPermission("business:live:edit")
    @Operation(summary = "更新直播间", description = "更新直播间信息")
    public ApiResponse<Object> updateLiveRoom(@PathVariable Long roomId, @RequestBody Map<String, Object> body) {
        return liveFeignClient.updateRoom(roomId, body);
    }

    @PutMapping("/live/rooms/{roomId}/start")
    @OperLog(title = "直播管理", businessType = 2)
    @RequiresPermission("business:live:edit")
    @Operation(summary = "开播", description = "开始直播")
    public ApiResponse<LiveRoomDTO> startLive(@PathVariable Long roomId) {
        return liveFeignClient.startLive(roomId);
    }

    @PutMapping("/live/rooms/{roomId}/end")
    @OperLog(title = "直播管理", businessType = 2)
    @RequiresPermission("business:live:edit")
    @Operation(summary = "结束直播", description = "结束直播")
    public ApiResponse<LiveRoomDTO> endLive(@PathVariable Long roomId) {
        return liveFeignClient.endLive(roomId);
    }

    @DeleteMapping("/live/rooms/{id}")
    @OperLog(title = "直播管理", businessType = 3)
    @RequiresPermission("business:live:remove")
    @Operation(summary = "删除直播间", description = "删除直播间")
    public ApiResponse<Void> deleteLiveRoom(@PathVariable Long id) {
        return liveFeignClient.deleteRoom(id);
    }

    // ==================== 仓储管理 ====================

    @GetMapping("/wms/pick-orders")
    @RequiresPermission("business:wms:list")
    @Operation(summary = "拣货单列表", description = "查询拣货单列表")
    public ApiResponse<Object> listPickOrders(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "warehouseId", required = false) Long warehouseId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        WmsSearchRequest request = new WmsSearchRequest(status, warehouseId, page, pageSize);
        return wmsFeignClient.listPickOrders(request);
    }

    @GetMapping("/wms/pick-orders/{id}")
    @RequiresPermission("business:wms:query")
    @Operation(summary = "拣货单详情", description = "查询拣货单详情")
    public ApiResponse<Object> getPickOrder(@PathVariable Long id) {
        return wmsFeignClient.getPickOrder(id);
    }

    @PutMapping("/wms/pick-orders/{id}/start")
    @OperLog(title = "仓储管理", businessType = 2)
    @RequiresPermission("business:wms:edit")
    @Operation(summary = "开始拣货", description = "分配拣货员并开始拣货")
    public ApiResponse<Object> startPick(@PathVariable Long id, @RequestParam Long assignedUserId) {
        return wmsFeignClient.startPick(id, assignedUserId);
    }

    @PutMapping("/wms/pick-orders/{id}/picked")
    @OperLog(title = "仓储管理", businessType = 2)
    @RequiresPermission("business:wms:edit")
    @Operation(summary = "确认拣货完成", description = "确认拣货完成")
    public ApiResponse<Object> confirmPicked(@PathVariable Long id) {
        return wmsFeignClient.confirmPicked(id);
    }

    @PutMapping("/wms/pick-orders/{id}/packed")
    @OperLog(title = "仓储管理", businessType = 2)
    @RequiresPermission("business:wms:edit")
    @Operation(summary = "确认打包完成", description = "确认打包完成")
    public ApiResponse<Object> confirmPacked(@PathVariable Long id) {
        return wmsFeignClient.confirmPacked(id);
    }

    @GetMapping("/wms/inbound-orders")
    @RequiresPermission("business:wms:list")
    @Operation(summary = "入库单列表", description = "查询入库单列表")
    public ApiResponse<Object> listInboundOrders(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "warehouseId", required = false) Long warehouseId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        WmsSearchRequest request = new WmsSearchRequest(status, warehouseId, page, pageSize);
        return wmsFeignClient.listInboundOrders(request);
    }

    @GetMapping("/wms/inbound-orders/{id}")
    @RequiresPermission("business:wms:query")
    @Operation(summary = "入库单详情", description = "查询入库单详情")
    public ApiResponse<Object> getInboundOrder(@PathVariable Long id) {
        return wmsFeignClient.getInboundOrder(id);
    }

    // ==================== 物流管理 ====================

    @GetMapping("/wms/shipping")
    @RequiresPermission("business:shipping:list")
    @Operation(summary = "物流列表", description = "查询物流订单列表")
    public ApiResponse<Object> listShipping(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "warehouseId", required = false) Long warehouseId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) {
        WmsSearchRequest request = new WmsSearchRequest(status, warehouseId, page, pageSize);
        return wmsFeignClient.listShipping(request);
    }

    @PutMapping("/wms/shipping/{id}/status")
    @OperLog(title = "仓储管理", businessType = 2)
    @RequiresPermission("business:shipping:edit")
    @Operation(summary = "更新物流状态", description = "更新物流订单状态")
    public ApiResponse<Object> updateShippingStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return wmsFeignClient.updateShippingStatus(id, body);
    }

    // ==================== 仓库管理 ====================

    @GetMapping("/wms/warehouses")
    @RequiresPermission("business:warehouse:list")
    @Operation(summary = "仓库列表", description = "查询仓库列表")
    public ApiResponse<Object> listWarehouses() {
        return wmsFeignClient.listWarehouses();
    }

    @PostMapping("/wms/warehouses")
    @OperLog(title = "仓储管理", businessType = 1)
    @RequiresPermission("business:warehouse:add")
    @Operation(summary = "创建仓库", description = "创建新仓库")
    public ApiResponse<Object> createWarehouse(@RequestBody Map<String, Object> body) {
        return wmsFeignClient.createWarehouse(body);
    }

    @PutMapping("/wms/warehouses/{id}")
    @OperLog(title = "仓储管理", businessType = 2)
    @RequiresPermission("business:warehouse:edit")
    @Operation(summary = "更新仓库", description = "更新仓库信息")
    public ApiResponse<Object> updateWarehouse(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return wmsFeignClient.updateWarehouse(id, body);
    }

    @DeleteMapping("/wms/warehouses/{id}")
    @OperLog(title = "仓储管理", businessType = 3)
    @RequiresPermission("business:warehouse:remove")
    @Operation(summary = "删除仓库", description = "删除仓库")
    public ApiResponse<Void> deleteWarehouse(@PathVariable Long id) {
        return wmsFeignClient.deleteWarehouse(id);
    }

    // ==================== 风控/黑名单 ====================

    @GetMapping("/risk/blacklist")
    @RequiresPermission("business:risk:list")
    @Operation(summary = "黑名单列表", description = "查询黑名单列表，支持按类型筛选")
    public ApiResponse<Object> listBlacklist(
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        Map<String, Object> params = new java.util.HashMap<>();
        if (type != null) params.put("type", type);
        params.put("page", page);
        params.put("pageSize", pageSize);
        return riskFeignClient.listBlacklist(params);
    }

    @PostMapping("/risk/blacklist")
    @OperLog(title = "风控管理", businessType = 1)
    @RequiresPermission("business:risk:add")
    @Operation(summary = "添加黑名单", description = "将用户/IP/设备加入黑名单")
    public ApiResponse<Object> addToBlacklist(@RequestBody Map<String, String> body) {
        java.time.LocalDateTime expiredAt = null;
        if (body.get("expiredAt") != null && !body.get("expiredAt").isEmpty()) {
            expiredAt = java.time.LocalDateTime.parse(body.get("expiredAt"));
        }
        return riskFeignClient.addToBlacklist(body.get("type"), body.get("value"), body.get("reason"), expiredAt);
    }

    @DeleteMapping("/risk/blacklist/{type}/{value}")
    @OperLog(title = "风控管理", businessType = 3)
    @RequiresPermission("business:risk:remove")
    @Operation(summary = "移除黑名单", description = "将用户/IP/设备从黑名单移除")
    public ApiResponse<Void> removeFromBlacklist(@PathVariable("type") String type, @PathVariable("value") String value) {
        return riskFeignClient.removeFromBlacklist(type, value);
    }

    @GetMapping("/risk/blacklist/check")
    @RequiresPermission("business:risk:query")
    @Operation(summary = "检查黑名单", description = "检查指定目标是否在黑名单中")
    public ApiResponse<Boolean> checkBlacklist(@RequestParam("type") String type, @RequestParam("value") String value) {
        return riskFeignClient.checkBlacklist(type, value);
    }

    // ==================== 风控记录 ====================

    @GetMapping("/risk/records")
    @RequiresPermission("business:risk:list")
    @Operation(summary = "风控记录列表", description = "查询风控记录列表")
    public ApiResponse<Object> listRiskRecords(
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        Map<String, Object> params = new java.util.HashMap<>();
        if (userId != null) params.put("userId", userId);
        params.put("page", page);
        params.put("size", pageSize);
        return riskFeignClient.listRiskRecords(params);
    }

    @GetMapping("/risk/records/{id}")
    @RequiresPermission("business:risk:query")
    @Operation(summary = "风控记录详情", description = "查询风控记录详情")
    public ApiResponse<Object> getRiskRecord(@PathVariable Long id) {
        return riskFeignClient.getRiskRecord(id);
    }

    // ==================== 风控规则 ====================

    @GetMapping("/risk/rules")
    @RequiresPermission("business:risk:list")
    @Operation(summary = "风控规则列表", description = "查询风控规则列表")
    public ApiResponse<Object> listRiskRules() {
        return riskFeignClient.listRiskRules(Map.of());
    }

    @PostMapping("/risk/rules")
    @OperLog(title = "风控管理", businessType = 1)
    @RequiresPermission("business:risk:add")
    @Operation(summary = "创建风控规则", description = "创建风控规则")
    public ApiResponse<Object> createRiskRule(@RequestBody Map<String, Object> body) {
        return riskFeignClient.createRiskRule(body);
    }

    @PutMapping("/risk/rules/{id}")
    @OperLog(title = "风控管理", businessType = 2)
    @RequiresPermission("business:risk:edit")
    @Operation(summary = "更新风控规则", description = "更新风控规则")
    public ApiResponse<Object> updateRiskRule(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return riskFeignClient.updateRiskRule(id, body);
    }

    @DeleteMapping("/risk/rules/{id}")
    @OperLog(title = "风控管理", businessType = 3)
    @RequiresPermission("business:risk:remove")
    @Operation(summary = "删除风控规则", description = "删除风控规则")
    public ApiResponse<Void> deleteRiskRule(@PathVariable Long id) {
        return riskFeignClient.deleteRiskRule(id);
    }

    // ==================== AI 管理 ====================

    @PostMapping("/ai/vector-sync/full")
    @OperLog(title = "AI管理", businessType = 1)
    @RequiresPermission("business:ai:sync")
    @Operation(summary = "全量向量同步", description = "触发全量商品向量同步到ES")
    public ApiResponse<Void> triggerFullVectorSync() {
        return aiFeignClient.triggerFullSync();
    }

    @PostMapping("/ai/vector-sync/product/{productId}")
    @OperLog(title = "AI管理", businessType = 1)
    @RequiresPermission("business:ai:sync")
    @Operation(summary = "增量向量同步", description = "同步单个商品向量到ES")
    public ApiResponse<Void> syncProductVector(@PathVariable Long productId) {
        return aiFeignClient.syncProduct(productId);
    }

    @DeleteMapping("/ai/vector-sync/product/{productId}")
    @OperLog(title = "AI管理", businessType = 3)
    @RequiresPermission("business:ai:sync")
    @Operation(summary = "删除商品向量", description = "从ES中删除商品向量")
    public ApiResponse<Void> deleteProductVector(@PathVariable Long productId) {
        return aiFeignClient.deleteProductVector(productId);
    }
}
