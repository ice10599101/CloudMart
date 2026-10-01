-- T05/QA14：履约单以 orderId 唯一——MQ 重复/乱序/多实例消费、应用层查重与插入的
-- 竞态窗口都由 DB 唯一约束兜底，保证"一个订单至多一份履约单"。
ALTER TABLE `pick_orders`
    ADD UNIQUE KEY `uk_pick_orders_order` (`order_id`);
