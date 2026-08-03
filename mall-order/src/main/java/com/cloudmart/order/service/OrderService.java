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

    OrderDTO getOrderById(Long userId, Long orderId);

    PaymentDTO payForOrder(Long userId, Long orderId);

    PaymentDTO getPaymentByOrderId(Long userId, Long orderId);

    OrderDTO shipOrder(Long orderId);

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
