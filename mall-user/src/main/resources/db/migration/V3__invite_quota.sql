-- T15 邀请额度原子预占：并发受邀人达标时不得越过邀请人日/月额度。
-- 此前用“扫描已 GRANTED 关系数”判断额度，无锁，多受邀人并发可超发。
-- 用 (inviter_id, quota_type, period) 唯一键 + 行锁/条件更新做原子预占。

CREATE TABLE `user_invite_quota` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `inviter_id` bigint unsigned NOT NULL COMMENT '邀请人用户 ID',
  `quota_type` varchar(16) NOT NULL COMMENT 'DAILY/MONTHLY',
  `period` varchar(16) NOT NULL COMMENT '业务周期标识：DAILY=yyyy-MM-dd，MONTHLY=yyyy-MM（Asia/Shanghai）',
  `used_count` int NOT NULL DEFAULT '0' COMMENT '已占用名额',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inviter_type_period` (`inviter_id`, `quota_type`, `period`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='邀请奖励周期额度（原子预占）';
