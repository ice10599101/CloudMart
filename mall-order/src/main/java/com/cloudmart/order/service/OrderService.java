package com.cloudmart.order.service;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.api.ApiResponse.Meta;
import com.cloudmart.order.dto.CreateOrderRequest;
import com.cloudmart.order.dto.OrderDTO;
import com.cloudmart.order.dto.OrderTodayStatsResponse;

import java.util.List;

public interface OrderService {

    OrderDTO createOrder(Long userId, CreateOrderRequest request);

    OrderDTO cancelOrder(Long userId, Long orderId);



    void notifyOrderCancel(Long orderId);

    ApiResponse<List<OrderDTO>> listOrders(Long userId, String status, int page, int size);

    /**
     * TRADE-01：从服务端报价创建订单——金额/商品信息全部取报价快照，
     * 客户端不提交任何价格字段；报价 CAS 消费（一报价一单）。
     *
     * @throws BusinessException QUOTE_NOT_FOUND / QUOTE_NOT_AVAILABLE
     */
        /**
     * T03：报价下单（快照权威）——expectedQuoteVersion 校验 + 价格/可售/券 QUOTE_STALE 复核
     * + DB 幂等（X-Idempotency-Key 优先，缺省 quote-{id}）+ 报价 CAS 消费与建单同事务。
     */
    OrderDTO createOrderFromQuote(Long userId, Long quoteId, Integer expectedQuoteVersion,
                                  String requestKey, String receiverName,
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

    /** OPS-01：已推进资金状态的订单分页（对账核对用）。 */
    java.util.Map.Entry<java.util.List<com.cloudmart.order.dto.OrderInternalInfoDTO>, Long>
            listPaidOrdersForReconciliation(int page, int size);

    /** WMS-01 余量：自动收货——发货超 N 天未确认的订单批量确认（mall-job 触发）。 */
    int autoConfirmReceipts(int days);

    /** T02：REFUND_SUCCEEDED 事件驱动的退款推进（Inbox 幂等；CAS REFUNDING → REFUNDED） */
    void notifyRefundSucceeded(Long orderId);

    /**
     * T05：支付成功唯一推进入口（支付事件 MQ 消费调用；Inbox + 金额/币种校验 + CAS + 订单
     * Outbox 同一本地事务）。expectedPayAmount/currency 为支付事件中的渠道事实，
     * 与订单应付不符时拒绝推进。
     */
    void applyPaymentSucceeded(Long orderId, String expectedPayAmount, String currency);

    /** REVIEW-01：用户已完成且包含该 SKU 的订单 ID 列表（评价资格判定，服务令牌可达） */
    List<Long> findCompletedOrderIdsWithSku(Long userId, Long skuId);

    /** T11 切片二 C：系统代批退款（售后质检通过自动流转；approveRefund 同逻辑无操作者限制） */
    OrderDTO approveRefundSystem(Long orderId);

    /** T11：售后案件退款完成回填（payment refund 事件消费者调用，按 refundNo 幂等） */
    void onAfterSaleRefundCompleted(String refundNo);

    /** T06：注销去标识化——收货人 PII 就地脱敏（幂等），交易记录按保留策略留存；返回改写行数 */
    int anonymizeReceiverForErasure(Long userId);

    /** T09：按 requestId（订单 request_key）查订单 ID；不存在返回 null（秒杀恢复对账用） */
    Long findOrderIdByRequestId(String requestId);

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

    /**
     * E02：超时订单批量兜底扫描（mall-job 定时触发）——延迟消息丢失/Redis
     * 投影丢失时的恢复入口：扫描超时未支付订单逐单走 cancelTimeoutOrder
     * （CAS 保护，重复扫描无副作用）。
     *
     * @param timeoutMinutes 超时分钟数（与下单超时一致，默认 15）
     * @param batchSize      单批上限
     * @return 本轮实际取消的订单数
     */
    int cancelTimeoutOrders(int timeoutMinutes, int batchSize);

    ApiResponse<List<OrderDTO>> listAllOrders(String status, Long userId, String orderNo, int page, int size);

    OrderDTO getAdminOrderById(Long orderId);

    OrderDTO adminCancelOrder(Long orderId);

    ApiResponse<OrderTodayStatsResponse> getTodayStats();

    /** 心愿关联商品闭环（§6）：还愿购买凭证校验（订单属主+已完成+含该商品条目） */
    boolean hasCompletedOrderWithProduct(Long userId, Long productId, Long orderId);

    /** N-5 问大家：用户是否已完成购买含该商品的订单 */
    boolean hasPurchasedProduct(Long userId, Long productId);
}