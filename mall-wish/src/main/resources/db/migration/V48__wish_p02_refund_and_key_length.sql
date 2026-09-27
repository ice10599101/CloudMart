-- V48: P02/TX-04 + TX-05 旧单退款与操作键扩容（mall-wish 侧）
-- 1) operation_id 扩为 160：与 pet_operation.operation_id(V21) 同宽，容纳
--    "业务键:客户端幂等键" 组合与最长雪花 ID，杜绝截断碰撞（恢复任务按原单收敛依赖全键匹配）。
-- 2) 新增 refund_of_operation_id：退款流水关联原扣款单（累计退款校验、审计、对账按原单聚合）。
--    字段为 VARCHAR 可空，仅 PET_REFUND 来源流水填写。

ALTER TABLE `wish_pet_operation`
    MODIFY COLUMN `operation_id` VARCHAR(160) NOT NULL
        COMMENT '业务操作唯一键(与pet_operation.operation_id同宽160，调用方生成，禁止截断)',
    ADD COLUMN `refund_of_operation_id` VARCHAR(160) DEFAULT NULL
        COMMENT '退款原单操作键(P02/TX-04，仅退款流水填写；累计退款校验按原单聚合) AFTER request_digest',
    ADD INDEX `idx_wish_pet_operation_refund_of` (`refund_of_operation_id`);
