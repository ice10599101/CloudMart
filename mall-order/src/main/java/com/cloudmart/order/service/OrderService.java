package com.cloudmart.order.service;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.api.ApiResponse.Meta;
import com.cloudmart.order.dto.CreateOrderRequest;
import com.cloudmart.order.dto.OrderDTO;
import com.cloudmart.order.dto.OrderTodayStatsResponse;
import com.cloudmart.order.feign.PaymentFeignClient.PaymentDTO;

import java.util.List;

public interface OrderService {

    OrderDTO createOrder(Long userId, CreateOrderRequest request);

    OrderDTO cancelOrder(Long userId, Long orderId);

    void notifyPaymentSuccess(Long orderId);

    void markOrderPaid(Long orderId);

    void notifyOrderCancel(Long orderId);

    ApiResponse<List<OrderDTO>> listOrders(Long userId, String status, int page, int size);

    /**
     * TRADE-01：从服务端报价创建订单——金额/商品信息全部取报价快照，
     * 客户端不提交任何价格字段；报价 CAS 消费（一报价一单）。
     *
     * @throws BusinessException QUOTE_NOT_FOUND / QUOTE_NOT_AVAILABLE
     */
    OrderDTO createOrderFromQuote(Long userId, Long quoteId, String receiverName,
                                  String receiverPhone, String receiverAddress);

    OrderDTO getOrderById(Long userId, Long orderId);

    /**
     * 订单最小内部信息（SEC-04）：供支付/履约服务做对象归属校验，不含业务明细。
     *
     * @throws BusinessException ORDER_NOT_FOUND 订单不存在
     */
    com.cloudmart.order.dto.OrderInternalInfoDTO getInternalOrderInfo(Long orderId);

    /** USER-01：用户是否存在未结订单（PENDING_PAYMENT/PAID/SHIPPED，注销阻塞判定） */
    boolean hasOpenOrders(Long userId);

    /** WMS-01 余量：自动收货——发货超 N 天未确认的订单批量确认（mall-job 触发）。 */
    int autoConfirmReceipts(int days);

    /** REVIEW-01：用户已完成且包含该 SKU 的订单 ID 列表（评价资格判定，服务令牌可达） */
    List<Long> findCompletedOrderIdsWithSku(Long userId, Long skuId);

    PaymentDTO payForOrder(Long userId, Long orderId);

    PaymentDTO getPaymentByOrderId(Long userId, Long orderId);

    /**
     * WMS-01 闭环：管理端发货——先在 WMS 建立真实包裹（运单号必填）并出库
     * （WMS 发布 ORDER_SHIPPED 事件），再 CAS 推进订单 PAID → SHIPPED
     * （与事件消费者幂等，先到先赢）。
     *
     * @throws BusinessException ORDER_NOT_FOUND / ORDER_STATUS_ERROR / WMS_SERVICE_UNAVAILABLE
     */
    OrderDTO shipOrder(Long orderId, String carrier, String trackingNo, Long warehouseId);

    OrderDTO confirmReceipt(Long userId, Long orderId);

    OrderDTO requestRefund(Long userId, Long orderId, String refundReason);

    OrderDTO approveRefund(Long orderId);

    OrderDTO rejectRefund(Long orderId, String rejectReason);

    void cancelTimeoutOrder(String orderNo);

    ApiResponse<List<OrderDTO>> listAllOrders(String status, Long userId, String orderNo, int page, int size);

    OrderDTO getAdminOrderById(Long orderId);

    OrderDTO adminCancelOrder(Long orderId);

    ApiResponse<OrderTodayStatsResponse> getTodayStats();
}
