-- TRADE-01：售后申请请求事实键（同键同参返回原案件，同键异参拒绝）。
-- 新开发无历史数据：旧行为"同单同项 PENDING 唯一"由服务层升级为
-- "有效状态占用（PENDING/APPROVED/REFUNDED）覆盖整单与按行"，幂等事实落库。
ALTER TABLE `after_sale_case`
  ADD COLUMN `request_key` varchar(128) DEFAULT NULL COMMENT '请求事实键（客户端 X-Idempotency-Key；同键同参返回原案件）' AFTER `item_id`,
  ADD COLUMN `payload_hash` varchar(128) DEFAULT NULL COMMENT '请求载荷摘要（同键异参拒绝）' AFTER `request_key`,
  ADD UNIQUE KEY `uk_after_sale_request` (`user_id`, `request_key`);
