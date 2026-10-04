package com.cloudmart.order.config;

/**
 * RocketMQ 拓扑常量定义。
 *
 * <p>Topic / Tag / ConsumerGroup 与原 RabbitMQ Exchange+RoutingKey+Queue 的映射关系：
 * <ul>
 *   <li>{@code order.events} exchange → {@code order-events} topic</li>
 *   <li>{@code payment.events} exchange → {@code payment-events} topic</li>
 *   <li>{@code marketing.events} exchange → {@code marketing-events} topic</li>
 *   <li>{@code seckill.events} exchange → {@code seckill-events} topic</li>
 *   <li>RabbitMQ routing key → RocketMQ tag</li>
 *   <li>RabbitMQ queue（独立消费逻辑）→ RocketMQ consumer group</li>
 * </ul>
 * 订单超时检查原为 TTL+DLX 延时队列（600s），改用 RocketMQ delayLevel=16（10min）。
 */
public final class RocketMQConfig {

    private RocketMQConfig() {
    }

    public static final String ORDER_TOPIC = "order-events";
    public static final String PAYMENT_TOPIC = "payment-events";
    public static final String MARKETING_TOPIC = "marketing-events";
    public static final String SECKILL_TOPIC = "seckill-events";

    public static final String ORDER_TAG_STATUS_CHANGE = "status-change";
    public static final String ORDER_TAG_PAID = "paid";
    public static final String ORDER_TAG_TIMEOUT_CHECK = "timeout-check";
    /** WMS-01：出库事件（wms Outbox 发布，OrderShippedConsumer 消费推进订单） */
    public static final String ORDER_TAG_SHIPPED = "shipped";
    public static final String CG_ORDER_SHIPPED = "cg-order-shipped";
    public static final String PAYMENT_TAG_RESULT = "result";
    public static final String PAYMENT_TAG_REFUND = "refund";
    public static final String MARKETING_TAG_GROUP_SUCCESS = "group-success";
    public static final String SECKILL_TAG_ORDER = "order";
    /** T09：秒杀结果回写事件（order Outbox 发布，mall-seckill Inbox 消费落终态） */
    public static final String ORDER_TAG_SECKILL_RESULT = "seckill-result";

    public static final String CG_ORDER_TIMEOUT = "order-timeout-cg";
    public static final String CG_ORDER_PAYMENT_RESULT = "order-payment-result-cg";
    public static final String CG_ORDER_PAYMENT_REFUND = "order-payment-refund-cg";
    public static final String CG_ORDER_GROUP_SUCCESS = "order-group-success-cg";
    public static final String CG_ORDER_SECKILL = "order-seckill-cg";
    /** T11：售后事件（payment Outbox 发往 order-events 的 after-sale tag） */
    public static final String TAG_AFTER_SALE = "after-sale";
    public static final String CG_ORDER_AFTER_SALE = "order-after-sale-cg";
    /** T04：售后案件退款提交消费者组（AFTER_SALE_REFUND_SUBMIT；与 WMS 入库消费者组隔离） */
    public static final String CG_ORDER_AFTER_SALE_REFUND = "order-after-sale-refund-submit-cg";

    /** RocketMQ 4.x delayLevel=16 对应 10 分钟，与原 RabbitMQ TTL 600000ms 一致。 */
    public static final int DELAY_LEVEL_ORDER_TIMEOUT = 16;
}
