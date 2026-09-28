package com.cloudmart.wms.mq;

import com.cloudmart.wms.config.RocketMQConfig;
import com.cloudmart.wms.dto.CreatePickOrderRequest;
import com.cloudmart.wms.service.PickOrderService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

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

    /**
     * ASYNC-01 断点 3：形状判定——新格式为事件信封（orderId 在 payload 内），
     * 旧格式 orderId 在顶层。处理失败必须重抛给 MQ 重试：此前 catch 后只记日志，
     * 消息被 ACK，拣货单丢失且无恢复手段。
     */
    @Override
    public void onMessage(Map<String, Object> message) {
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

    /** 形状判定：新格式 orderId 在 payload 内；旧格式在顶层 */
    static Long extractOrderId(Map<String, Object> message) {
        Object payload = message.get("payload");
        if (payload instanceof Map<?, ?> payloadMap && payloadMap.get("orderId") instanceof Number n) {
            return n.longValue();
        }
        if (message.get("orderId") instanceof Number n) {
            return n.longValue();
        }
        throw new IllegalArgumentException("消息缺少 orderId（新旧形状均未命中）: " + message.keySet());
    }

    /** WMS-01：仓库分配——事件未指定时取第一个可用仓库，不再硬编码 1 */
    Long extractWarehouseIdOrDefault(Map<String, Object> message) {
        if (message.get("warehouseId") instanceof Number n) {
            return n.longValue();
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
