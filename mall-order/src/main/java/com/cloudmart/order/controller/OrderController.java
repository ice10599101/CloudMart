package com.cloudmart.order.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.order.converter.OrderConverter;
import com.cloudmart.order.dto.CreateOrderRequest;
import com.cloudmart.order.dto.OrderDTO;
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
    @Operation(summary = "创建订单", description = "旧结算入口（TRADE-01 迁移期兼容）："
            + "价格与商品信息以下单时服务端校验为准，客户端金额不再作为记账依据")
    public ApiResponse<OrderVO> createOrder(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "创建订单请求") @Valid @RequestBody CreateOrderRequest request) {
        OrderDTO dto = orderService.createOrder(userId, request);
        return ApiResponse.ok(orderConverter.orderDtoToVO(dto));
    }

    public record CreateOrderFromQuoteRequest(
            @jakarta.validation.constraints.NotNull Long quoteId,
            String receiverName,
            String receiverPhone,
            String receiverAddress) {
    }

    @PostMapping("/v2")
    @Operation(summary = "创建订单 v2（报价下单）", description = "TRADE-01：必须引用本人有效报价，"
            + "金额/商品信息全部取报价快照，本接口没有价格字段；报价 CAS 消费（一报价一单）")
    public ApiResponse<OrderVO> createOrderFromQuote(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @Parameter(description = "报价下单请求") @Valid @RequestBody CreateOrderFromQuoteRequest request) {
        OrderDTO dto = orderService.createOrderFromQuote(userId, request.quoteId(),
                request.receiverName(), request.receiverPhone(), request.receiverAddress());
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

    // SEC-01：payment-success/cancel-notify 属支付服务回调，已迁移至
    // InternalOrderController（/internal/**，mall-payment 服务令牌可达）；
    // ship 属履约动作，统一走 AdminOrderController（mall-admin 服务令牌可达）。
    // T01：旧 /pay 与 /payment 代理端点已删除——收银台直连 mall-payment /payment-attempts。

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
