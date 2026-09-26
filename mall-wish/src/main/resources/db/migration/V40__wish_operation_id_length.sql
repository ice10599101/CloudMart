-- V40: B01 修复——wish_pet_operation.operation_id 扩到 80
-- 现象：pet 侧确定性操作键（BIZ:user:pet:类型:编码:幂等键）可达 80 字符，
--      wish 侧列宽 64 导致插入超长报 500。两侧列宽对齐（pet_operation 为 80）。

ALTER TABLE `wish_pet_operation`
    MODIFY COLUMN `operation_id` VARCHAR(80) NOT NULL COMMENT '业务操作唯一键(与pet_operation.operation_id同宽80)';
