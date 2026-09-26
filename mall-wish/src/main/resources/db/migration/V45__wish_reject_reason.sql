-- V45: 审核驳回原因落库（B11）
-- 背景：auditWish 接收 rejectReason 但不校验必填、不落库——作者看不到驳回原因，
--      治理记录无法追溯。
ALTER TABLE `wish`
    ADD COLUMN `reject_reason` VARCHAR(500) DEFAULT NULL
        COMMENT '驳回原因(B11:REJECTED 必填;恢复上架时清空)' AFTER `audit_status`;
