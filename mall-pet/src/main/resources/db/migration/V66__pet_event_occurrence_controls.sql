-- V66 (R33/§8.2): 期次"停止计数/停止领奖"分开——运营三段式控制
-- 用时间戳而非布尔：计数停止后，停止时点之前的事实仍按窗口统计（不追溯清零），
-- 领奖停止后立即拒绝领取。报名关闭不适用（当前活动模型无报名环节，发布即开窗）。

ALTER TABLE `pet_event_occurrence`
    ADD COLUMN `counting_stopped_at` DATETIME DEFAULT NULL COMMENT '计数停止时刻(UTC)；此前事实仍计入，此后不计' AFTER `status`,
    ADD COLUMN `claim_stopped_at` DATETIME DEFAULT NULL COMMENT '领奖停止时刻(UTC)；到点即拒绝领取' AFTER `counting_stopped_at`;

-- 每日任务实例取消（§8.2 管理后台：受审计命令，取消已发奖实例必须走收回流程，此处拒绝）
ALTER TABLE `pet_daily_quest`
    ADD COLUMN `cancel_reason` VARCHAR(200) DEFAULT NULL COMMENT '取消原因（管理端受审计命令必填）' AFTER `status`,
    ADD COLUMN `cancelled_by` VARCHAR(64) DEFAULT NULL COMMENT '取消操作管理员（服务令牌 admin_username，P0-3）' AFTER `cancel_reason`;
