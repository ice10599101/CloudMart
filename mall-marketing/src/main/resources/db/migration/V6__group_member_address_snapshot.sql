-- V6 (T11)：参团地址快照——成团建单使用参团时的地址，不被异步建单时的
-- 新默认地址悄悄替换；缺地址成员返回"补充地址"动作而非盲目失败。

ALTER TABLE group_members
    ADD COLUMN `address_id` BIGINT UNSIGNED DEFAULT NULL
        COMMENT '参团时选定的收货地址ID（成团建单地址快照权威）' AFTER is_leader,
    ADD COLUMN `receiver_name` VARCHAR(64) DEFAULT NULL COMMENT '收货人快照' AFTER address_id,
    ADD COLUMN `receiver_phone` VARCHAR(32) DEFAULT NULL COMMENT '收货电话快照' AFTER receiver_name,
    ADD COLUMN `receiver_address` VARCHAR(500) DEFAULT NULL COMMENT '收货地址快照' AFTER receiver_phone;

-- 建单任务台账：每成员建单状态/失败原因/重试依据（T11 §5）
ALTER TABLE group_members
    ADD COLUMN `order_task_status` VARCHAR(20) DEFAULT NULL
        COMMENT '建单任务:PENDING/SUCCEEDED/FAILED_ADDRESS/FAILED/ABSENT_ADDRESS' AFTER receiver_address,
    ADD COLUMN `order_task_error` VARCHAR(200) DEFAULT NULL COMMENT '建单失败原因（脱敏）' AFTER order_task_status;
