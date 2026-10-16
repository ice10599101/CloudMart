package com.cloudmart.wms.mq;

import com.cloudmart.common.util.Ids;
import com.cloudmart.wms.config.RocketMQConfig;
import com.cloudmart.wms.dto.CreatePickOrderRequest;
import com.cloudmart.wms.service.PickOrderService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 订单支付事件消费者：ORDER_PAID（order-events:paid）→ 自动生成拣货单。
 *
 * <p>C20：仅接受 Outbox v2 信封（eventId/eventType/schemaVersion 必填，orderId 在
 * payload 内），旧顶层字段形状直接拒绝（MQ 重试→死信）。仓库由 WMS 侧分配：
 * 载荷显式 warehouseId 时用载荷，否则取第一个可用仓库（业务默认，非消息兼容）。</p>
 */
@Component
@RocketMQMessageListener(
        topic = RocketMQConfig.ORDER_TOPIC,
        consumerGroup = RocketMQConfig.CG_WMS_ORDER_PAID,
        selectorExpression = RocketMQConfig.ORDER_TAG_PAID
)
public class OrderPaidListener implements RocketMQListener<Map<String, Object>> {

    private static final Logger log = LoggerFactory.getLogger(OrderPaidListener.class);

    private final PickOrderService pickOrderService;

    private final com.cloudmart.wms.repository.WarehouseMapper warehouseMapper;

    public OrderPaidListener(PickOrderService pickOrderService,
                             com.cloudmart.wms.repository.WarehouseMapper warehouseMapper) {
        this.pickOrderService = pickOrderService;
        this.warehouseMapper = warehouseMapper;
    }

    @Override
    public void onMessage(Map<String, Object> message) {
        requireEnvelope(message);
        Long orderId = extractOrderId(message);
        Long warehouseId = extractWarehouseIdOrDefault(message);

        log.info("Received order paid event, creating pick order: orderId={}, warehouseId={}", orderId, warehouseId);

        if (pickOrderService.findByOrderId(orderId) != null) {
            log.info("Pick order already exists for orderId={}, skipping", orderId);
            return;
        }

        CreatePickOrderRequest request = new CreatePickOrderRequest(orderId, warehouseId, "订单支付成功自动生成");
        pickOrderService.createPickOrder(request);
        log.info("Pick order created for orderId={}", orderId);
    }

    /** C20：信封必填字段校验（缺 eventId/eventType/schemaVersion 即旧形状，拒绝）。 */
    static void requireEnvelope(Map<String, Object> message) {
        Object eventId = message.get("eventId");
        Object eventType = message.get("eventType");
        Object schemaVersion = message.get("schemaVersion");
        if (!(eventId instanceof String s) || s.isBlank()
                || !(eventType instanceof String t) || t.isBlank()
                || !(schemaVersion instanceof Number)) {
            throw new IllegalArgumentException(
                    "ORDER_PAID 事件信封缺少 eventId/eventType/schemaVersion（旧形状拒绝消费）: " + message.keySet());
        }
    }

    /** orderId 只从 payload 提取（旧顶层分支已删）；BASE-01：字符串/数字两种形态均接受。 */
    static Long extractOrderId(Map<String, Object> message) {
        if (message.get("payload") instanceof Map<?, ?> payload) {
            Long orderId = Ids.parse(payload.get("orderId"));
            if (orderId != null) {
                return orderId;
            }
        }
        throw new IllegalArgumentException("ORDER_PAID 事件 payload 缺少 orderId（旧形状拒绝消费）: " + message.keySet());
    }

    /** 仓库分配：载荷未指定时取第一个可用仓库（业务默认），无可用仓库抛错触发重试。 */
    Long extractWarehouseIdOrDefault(Map<String, Object> message) {
        if (message.get("payload") instanceof Map<?, ?> payload) {
            Long warehouseId = Ids.parse(payload.get("warehouseId"));
            if (warehouseId != null) {
                return warehouseId;
            }
        }
        var first = warehouseMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.cloudmart.wms.entity.Warehouse>()
                        .last("LIMIT 1"));
        if (first.isEmpty()) {
            throw new IllegalStateException("无可用仓库，拣货单创建失败（事件将重试）");
        }
        return first.get(0).getId();
    }
}
