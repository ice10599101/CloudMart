-- C03/QA25：投票选票唯一事实——uk(poll_id, user_id) 保证每用户每投票至多一张选票。
-- 原uk(poll_id,user_id,option_id) 允许单选投票并发提交不同选项各成一行（双选票缺陷）；
-- 选票表先行 insert 判重，选项明细随后同事务写入。
CREATE TABLE IF NOT EXISTS `community_poll_ballots` (
  `id`         bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '选票ID',
  `poll_id`    varchar(36)    NOT NULL COMMENT '投票ID',
  `user_id`    bigint unsigned NOT NULL COMMENT '投票用户ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '投票时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ballot_poll_user` (`poll_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='投票选票(每用户每投票唯一)';

-- 存量按既有投票记录收敛（每 poll+user 取最早一条）
INSERT IGNORE INTO `community_poll_ballots` (`poll_id`, `user_id`, `created_at`)
SELECT `poll_id`, `user_id`, MIN(`created_at`) FROM `community_poll_votes` GROUP BY `poll_id`, `user_id`;
