-- R12：满减真实报价——报价单与订单持久化优惠明细快照（JSON：
-- 阶梯满减活动/规则 ID + 优惠券 ID + 金额 + 门槛 + 名称）。
-- 下单锁定快照，活动过期/下架/规则变化 → QUOTE_STALE 重新报价；
-- 订单快照支持售后按原行实付分摊与"从订单反查命中规则"。
ALTER TABLE order_quote
    ADD COLUMN discount_lines TEXT NULL COMMENT 'R12 优惠明细快照 JSON（TIERED/COUPON 行，计算顺序 满减→券）'
    AFTER discount_amount;

ALTER TABLE orders
    ADD COLUMN discount_lines TEXT NULL COMMENT 'R12 下单锁定的优惠明细快照 JSON（报价快照复制，售后/反查依据）'
    AFTER discount_amount;
