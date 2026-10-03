package com.cloudmart.wms.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.wms.config.RocketMQConfig;
import com.cloudmart.wms.dto.CreateInboundOrderRequest;
import com.cloudmart.wms.dto.InboundItemRequest;
import com.cloudmart.wms.entity.Warehouse;
import com.cloudmart.wms.repository.WarehouseMapper;
import com.cloudmart.wms.service.InboundOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 售后退货入库监听（T11 切片三）：质检 PASSED 的 RETURN_REFUND 案件 →
 * 自动创建 RETURN 入库单（reference_no = 案件号），仓库侧收到货即可收货确认。
 *
 * <p>幂等：先查后建 + DB 唯一键 uk_inbound_orders_type_ref 兜底（并发双投递
 * 落到 DuplicateKeyException 视为已建）。REFUND_ONLY 仅退款无货退回，不联动。
 * SKU 明细缺失属数据完整性问题，显式抛出重试进死信由运营排查，不得静默丢件。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = RocketMQConfig.ORDER_TOPIC,
        consumerGroup = RocketMQConfig.CG_WMS_AFTER_SALE_RETURN,
        selectorExpression = RocketMQConfig.ORDER_TAG_AFTER_SALE
)
public class AfterSaleReturnListener implements RocketMQListener<Map<String, Object>> {

    static final String EVENT_TYPE = "AFTER_SALE_INSPECT_PASSED";
    static final String INBOUND_TYPE_RETURN = "RETURN";

    private final InboundOrderService inboundOrderService;
    private final WarehouseMapper warehouseMapper;

    @Override
    public void onMessage(Map<String, Object> message) {
        String eventId = (String) message.get("eventId");
        if (eventId == null || eventId.isBlank()) {
            log.warn("[T11] 售后退货入库事件缺少 eventId（旧形状），拒绝消费");
            return;
        }
        if (!EVENT_TYPE.equals(message.get("eventType"))) {
            log.warn("[T11] 售后退货入库消费者收到未知事件类型 {}", message.get("eventType"));
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) message.get("payload");
        String caseNo = payload == null ? null : (String) payload.get("caseNo");
        if (caseNo == null || caseNo.isBlank()) {
            throw new IllegalStateException("售后事件缺少 caseNo，无法建立退货入库关联（重试）");
        }
        String afterSaleType = payload == null ? null : (String) payload.get("afterSaleType");
        if (!"RETURN_REFUND".equals(afterSaleType)) {
            // REFUND_ONLY 仅退款无货退回，不产生入库单
            log.info("[T11] 售后案件 {} 类型 {} 无货退回，跳过入库联动", caseNo, afterSaleType);
            return;
        }
        if (inboundOrderService.findByTypeAndReferenceNo(INBOUND_TYPE_RETURN, caseNo) != null) {
            log.info("[T11] 售后案件 {} 退货入库单已存在（幂等跳过） eventId={}", caseNo, eventId);
            return;
        }
        Object skuIdRaw = payload.get("skuId");
        Object productNameRaw = payload.get("productName");
        if (!(skuIdRaw instanceof Number) || productNameRaw == null
                || String.valueOf(productNameRaw).isBlank()) {
            throw new IllegalStateException("退货案件 " + caseNo
                    + " 缺少 SKU 明细（skuId/productName），拒绝建单（重试进死信人工排查）");
        }
        int quantity = payload.get("quantity") instanceof Number n ? n.intValue() : 1;
        if (quantity <= 0) {
            throw new IllegalStateException("退货案件 " + caseNo + " 退货数量非法: " + quantity);
        }

        CreateInboundOrderRequest request = new CreateInboundOrderRequest(
                resolveWarehouseId(), INBOUND_TYPE_RETURN, caseNo,
                "售后质检通过自动生成（T11 退货入库联动）",
                List.of(new InboundItemRequest(((Number) skuIdRaw).longValue(),
                        String.valueOf(productNameRaw), quantity, null)));
        try {
            inboundOrderService.createInboundOrder(request);
            log.info("[T11] 退货入库单已创建 caseNo={} skuId={} quantity={}",
                    caseNo, skuIdRaw, quantity);
        } catch (DuplicateKeyException e) {
            log.info("[T11] 售后案件 {} 退货入库单并发已建（唯一键兜底，幂等跳过）", caseNo);
        }
    }

    /** 仓库分配与拣货联动（OrderPaidListener）同语义：取第一个可用仓库，无则显式失败重试 */
    private Long resolveWarehouseId() {
        List<Warehouse> first = warehouseMapper.selectList(
                new LambdaQueryWrapper<Warehouse>().last("LIMIT 1"));
        if (first.isEmpty()) {
            throw new IllegalStateException("无可用仓库，退货入库单创建失败（事件将重试）");
        }
        return first.get(0).getId();
    }
}
