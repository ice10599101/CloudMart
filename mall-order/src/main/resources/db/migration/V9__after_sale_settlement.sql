-- V9 (T04)：售后按明细 case 结算——订单退款汇总 + 明细实付分摊 + 退款号唯一绑定。
-- 目标状态：订单履约状态不被部分退款覆盖；订单级退款汇总 NONE/PARTIAL/FULL
-- 保存已退累计；新订单明细持久化实付分摊（按价×量比例分摊，余数归属首项，
-- 由应用层写入）。
-- 注意：orders/order_items 为 ShardingSphere 分片表，本文件仅做可广播的 DDL；
-- 历史明细分摊回填涉及跨片聚合核对，见 sql/t04-item-pay-allocation-backfill.sql
-- （dry-run 后人工执行，不猜金额）。

ALTER TABLE `orders`
    ADD COLUMN `refund_status` VARCHAR(10) NOT NULL DEFAULT 'NONE'
        COMMENT '退款汇总状态:NONE/PARTIAL/FULL（T04）' AFTER refund_reject_reason,
    ADD COLUMN `refunded_amount` DECIMAL(18,2) NOT NULL DEFAULT 0.00
        COMMENT '已退累计金额（T04，case 退款成功回填）' AFTER refund_status;

ALTER TABLE `order_items`
    ADD COLUMN `pay_amount` DECIMAL(18,2) DEFAULT NULL
        COMMENT '明细实付分摊=成交价×数量-优惠分摊（T04；NULL=历史单未准确分摊，人工规则）' AFTER quantity;

-- 一笔退款申请绑定一个 case、一个稳定退款号（RFC{caseId}；orders.id 为雪花与
-- case 自增 ID 空间不重叠，与历史 RF{orderId} 无碰撞）。唯一键保证
-- refund_no → case 一对一回填（markRefunded 不可能多行命中）。
-- 若存量数据存在重复绑定（旧流程管理员手工输入同号），本迁移失败即暴露——
-- 先人工核对后升级（明确失败 > 假装成功）。
ALTER TABLE after_sale_case
    ADD UNIQUE KEY `uk_after_sale_refund_no` (`refund_no`);
