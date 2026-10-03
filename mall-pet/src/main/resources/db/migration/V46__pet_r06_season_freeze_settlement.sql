-- V46 (R06): 赛季冻结结算改造——冻榜/发奖分离 + 恢复租约，SETTLED 只在全量发奖后写
-- 原缺陷：调度器/管理端在结算前 CAS ACTIVE→SETTLED，kill 后 catch 回退不执行（进程死亡），
-- 后续只扫 ACTIVE → 该赛季永久漏结算；且发奖从实时 pet 表 LIMIT/OFFSET 分页——
-- 批间经验变化使排名漂移，快照不稳定。

-- 1) 赛季状态机扩展：ACTIVE → FREEZING（冻榜中）→ SETTLING（发奖中）→ SETTLED；
--    失败不回退 ACTIVE，记录在结算作业行由租约接管重试。
ALTER TABLE `pet_season`
    MODIFY `status` ENUM('ACTIVE','FREEZING','SETTLING','SETTLED') NOT NULL DEFAULT 'ACTIVE'
        COMMENT '状态: ACTIVE进行中/FREEZING冻榜中/SETTLING发奖中/SETTLED已结算',
    ADD COLUMN `freeze_at` DATETIME NULL COMMENT '冻榜完成时间(UTC，延迟冻榜时≠endsAt)' AFTER `settled_at`,
    ADD COLUMN `snapshot_complete` TINYINT NOT NULL DEFAULT 0 COMMENT '排名快照是否完整(1=完整)' AFTER `freeze_at`;

-- 2) 排名快照表：id 改 AUTO_INCREMENT（INSERT...SELECT 一次成型窗口排名需 DB 生成 id）；
--    增加 reward_status 一次性发奖事实（NULL=未发，SUCCEEDED=已发；CAS 保证并发只发一次）
ALTER TABLE `pet_season_ranking`
    MODIFY `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键(自增;快照INSERT...SELECT生成)',
    ADD COLUMN `reward_status` VARCHAR(20) NULL COMMENT '奖励状态: NULL未发/SUCCEEDED已发' AFTER `exp`,
    ADD COLUMN `rewarded_at` DATETIME NULL COMMENT '发奖时间(UTC)' AFTER `reward_status`,
    ADD INDEX `idx_season_ranking_cursor` (`season_id`, `rank_no`);

-- 3) 结算作业表：checkpoint/租约/错误——进程 kill 后由租约到期接管，绝不重冻榜
CREATE TABLE IF NOT EXISTS `pet_season_settlement_job` (
    `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '作业ID',
    `season_id`      BIGINT UNSIGNED NOT NULL COMMENT '赛季ID(唯一)',
    `status`         VARCHAR(20) NOT NULL DEFAULT 'RUNNING' COMMENT '状态: RUNNING/COMPLETED',
    `cursor_rank`    INT NOT NULL DEFAULT 0 COMMENT '已处理到的名次游标(按rank_no升序)',
    `total_count`    INT NOT NULL DEFAULT 0 COMMENT '快照总人数',
    `success_count`  INT NOT NULL DEFAULT 0 COMMENT '累计发奖成功数',
    `failure_count`  INT NOT NULL DEFAULT 0 COMMENT '累计发奖失败数',
    `lease_owner`    VARCHAR(64) NULL COMMENT '当前执行者租约标识',
    `lease_version`  BIGINT NOT NULL DEFAULT 0 COMMENT '租约版本(CAS推进)',
    `lease_until`    DATETIME NULL COMMENT '租约到期时间(UTC)',
    `last_error`     VARCHAR(500) NULL COMMENT '最近一次错误摘要',
    `next_retry_at`  DATETIME NULL COMMENT '下次重试时间(UTC)',
    `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_season_settlement_job` (`id`),
    UNIQUE KEY `uk_season_settlement_season` (`season_id`),
    INDEX `idx_season_settlement_lease` (`status`, `lease_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='赛季结算作业(checkpoint+租约)';

-- 4) 赛季创建守卫（单例行）：并发创建在行锁内复验"仅一个进行中"，先 count 后 insert 的
--    读快照竞态不再产生双 ACTIVE
CREATE TABLE IF NOT EXISTS `pet_season_guard` (
    `id`   TINYINT UNSIGNED NOT NULL COMMENT '守卫行(恒为1)',
    PRIMARY KEY `pk_pet_season_guard` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='赛季创建串行守卫';

INSERT IGNORE INTO `pet_season_guard` (`id`) VALUES (1);
