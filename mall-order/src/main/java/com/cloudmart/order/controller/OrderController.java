package com.cloudmart.order.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.order.converter.OrderConverter;
import com.cloudmart.order.dto.CreateOrderRequest;
import com.cloudmart.order.dto.OrderDTO;
import com.cloudmart.order.feign.PaymentFeignClient.PaymentDTO;
import com.cloudmart.order.service.OrderService;
import com.cloudmart.order.vo.OrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/orders")
@Tag(name = "订单管理", description = "订单创建、取消、查询、支付接口")
public class OrderController {

    private final OrderService orderService;
    private final OrderConverter orderConverter;

    public OrderController(OrderService orderService, OrderConverter orderConverter) {
        this.orderService = orderService;
        this.orderConverter = orderConverter;
    }

    @PostMapping
    @Operation(summary = "创建订单", description = "从购物车结算创建订单，预扣库存")
    public ApiResponse<OrderVO> createOrder(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "创建订单请求") @Valid @RequestBody CreateOrderRequest request) {
        OrderDTO dto = orderService.createOrder(userId, request);
        return ApiResponse.ok(orderConverter.orderDtoToVO(dto));
    }

    @PutMapping("/{orderId}/cancel")
    @Operation(summary = "取消订单", description = "取消未支付订单，释放预扣库存")
    public ApiResponse<OrderVO> cancelOrder(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "订单ID", required = true) @PathVariable("orderId") Long orderId) {
        OrderDTO dto = orderService.cancelOrder(userId, orderId);
        return ApiResponse.ok(orderConverter.orderDtoToVO(dto));
    }

    @GetMapping
    @Operation(summary = "订单列表", description = "查询当前用户的订单列表，支持按状态筛选")
    public ApiResponse<List<OrderVO>> listOrders(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "订单状态") @RequestParam(value = "status", required = false) String status,
            @Parameter(description = "页码") @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(value = "size", defaultValue = "10") int size) {
        ApiResponse<List<OrderDTO>> response = orderService.listOrders(userId, status, page, size);
        List<OrderVO> voList = orderConverter.orderDtoToVOList(response.data());
        return ApiResponse.ok(voList, response.meta());
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "订单详情", description = "查询订单详情，包含订单项列表")
    public ApiResponse<OrderVO> getOrderById(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "订单ID", required = true) @PathVariable("orderId") Long orderId) {
        OrderDTO dto = orderService.getOrderById(userId, orderId);
        return ApiResponse.ok(orderConverter.orderDtoToVO(dto));
    }

    @PostMapping("/{orderId}/pay")
    @Operation(summary = "订单支付", description = "为订单创建支付记录，返回支付信息")
    public ApiResponse<PaymentDTO> payForOrder(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "订单ID", required = true) @PathVariable("orderId") Long orderId) {
        return ApiResponse.ok(orderService.payForOrder(userId, orderId));
    }

    @GetMapping("/{orderId}/payment")
    @Operation(summary = "查询订单支付信息", description = "根据订单ID查询关联的支付记录")
    public ApiResponse<PaymentDTO> getPaymentByOrderId(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "订单ID", required = true) @PathVariable("orderId") Long orderId) {
        return ApiResponse.ok(orderService.getPaymentByOrderId(userId, orderId));
    }

    @PostMapping("/{orderId}/payment-success")
    @Operation(summary = "支付成功通知", description = "支付服务回调通知订单支付成功")
    public ApiResponse<Void> notifyPaymentSuccess(
            @Parameter(description = "订单ID", required = true) @PathVariable("orderId") Long orderId) {
        orderService.notifyPaymentSuccess(orderId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{orderId}/cancel-notify")
    @Operation(summary = "订单取消通知", description = "支付服务回调通知订单取消（退款）")
    public ApiResponse<Void> notifyOrderCancel(
            @Parameter(description = "订单ID", required = true) @PathVariable("orderId") Long orderId) {
        orderService.notifyOrderCancel(orderId);
        return ApiResponse.ok(null);
    }

    @PutMapping("/{orderId}/ship")
    @Operation(summary = "订单发货", description = "将已支付订单标记为已发货（管理员/卖家操作）")
    public ApiResponse<OrderVO> shipOrder(
            @Parameter(description = "订单ID", required = true) @PathVariable("orderId") Long orderId) {
        OrderDTO dto = orderService.shipOrder(orderId);
        return ApiResponse.ok(orderConverter.orderDtoToVO(dto));
    }

    @PutMapping("/{orderId}/confirm")
    @Operation(summary = "确认收货", description = "买家确认收货，订单完成，库存扣减从预占转为确认")
    public ApiResponse<OrderVO> confirmReceipt(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "订单ID", required = true) @PathVariable("orderId") Long orderId) {
        OrderDTO dto = orderService.confirmReceipt(userId, orderId);
        return ApiResponse.ok(orderConverter.orderDtoToVO(dto));
    }

    @PostMapping("/{orderId}/refund")
    @Operation(summary = "申请退款", description = "买家对已支付或已发货订单申请退款")
    public ApiResponse<OrderVO> requestRefund(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "订单ID", required = true) @PathVariable("orderId") Long orderId,
            @Parameter(description = "退款原因", required = true) @RequestParam("refundReason") String refundReason) {
        OrderDTO dto = orderService.requestRefund(userId, orderId, refundReason);
        return ApiResponse.ok(orderConverter.orderDtoToVO(dto));
    }
}
