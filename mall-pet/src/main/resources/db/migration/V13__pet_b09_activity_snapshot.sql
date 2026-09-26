-- V13: B09 活动/职业工作开始时冻结规则快照
-- 现状：完成/领取时重新读取当前配置结算，运营在任务进行中修改奖励会改已开始任务的收益。
-- 方案：开始时把名称/消耗/时长/基础奖励/加成依据写入 snapshot JSON；完成结算只用快照。
--      存量无快照的活动按原配置兼容结算（迁移动作记录于 data-migration.md）。

ALTER TABLE `pet_activity`
    ADD COLUMN `snapshot` JSON DEFAULT NULL COMMENT '规则快照(开始时冻结:名称/消耗/时长/基础奖励/加成依据/配置版本)' AFTER `result`;
