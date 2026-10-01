-- T02：退款前履约状态——退款审批与渠道事实分离的最小闭环。
-- requestRefund 进入 REFUNDING 时记录原状态（PAID/SHIPPED）；
-- rejectRefund 恢复该状态（修复"一律回 PAID 丢失 SHIPPED"缺陷，QA08）。
ALTER TABLE `orders`
    ADD COLUMN `before_refund_status` VARCHAR(20)
        DEFAULT NULL COMMENT '退款前履约状态(T02:PAID/SHIPPED;拒绝退款时恢复,退款成功后保留追溯)' AFTER `refund_reason`;
