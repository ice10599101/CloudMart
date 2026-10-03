-- V48 (R18): 调账审批审计补全——拒绝理由持久化 + 工单号唯一意图
-- 原缺口：approve/reject 的 reason 参数被丢弃（审批理由不可查）；
-- 同 ticketNo 重复提交可创建多张申请 → 多次审批发币。

ALTER TABLE `pet_wallet_adjustment`
    ADD COLUMN `review_reason` VARCHAR(255) DEFAULT NULL
        COMMENT '审批意见（拒绝必填/通过可填；审计可见）' AFTER `reviewed_at`;

-- 工单号唯一意图：同 ticketNo 至多一张申请（NULL 不受约束——允许无工单号申请）
ALTER TABLE `pet_wallet_adjustment`
    ADD UNIQUE KEY `uk_pet_wallet_adjustment_ticket` (`ticket_no`);
