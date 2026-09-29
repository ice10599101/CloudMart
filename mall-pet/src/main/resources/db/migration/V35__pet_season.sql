-- F2：排行榜赛季制——赛季 + 奖励梯度 + 最终榜快照（历史名次）。
-- 结算由调度器在 ends_at 之后触发（CAS 置 SETTLED 防重），奖励经 PetEconomyService.credit
-- 幂等入账（operationKey=SEASON_REWARD:{seasonId}:{petId}），重复结算不重发。

CREATE TABLE IF NOT EXISTS `pet_season` (
    `id`         BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `name`       VARCHAR(64) NOT NULL COMMENT '赛季名称',
    `starts_at`  DATETIME NOT NULL COMMENT '开始时间(UTC)',
    `ends_at`    DATETIME NOT NULL COMMENT '结束时间(UTC)',
    `status`     ENUM('ACTIVE','SETTLED') NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE进行中/SETTLED已结算',
    `settled_at` DATETIME DEFAULT NULL COMMENT '结算时间(UTC)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    PRIMARY KEY `pk_pet_season` (`id`),
    INDEX `idx_pet_season_status_ends` (`status`, `ends_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物排行榜赛季';

CREATE TABLE IF NOT EXISTS `pet_season_reward` (
    `id`                BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `season_id`         BIGINT UNSIGNED NOT NULL COMMENT '赛季ID',
    `rank_min`          INT NOT NULL COMMENT '名次下界(含,1基)',
    `rank_max`          INT NOT NULL COMMENT '名次上界(含)',
    `reward_starlight`  INT NOT NULL DEFAULT 0 COMMENT '星光奖励',
    `reward_exp`        INT NOT NULL DEFAULT 0 COMMENT '经验奖励',
    `created_at`        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    PRIMARY KEY `pk_pet_season_reward` (`id`),
    UNIQUE KEY `uk_season_reward_range` (`season_id`, `rank_min`),
    INDEX `idx_season_reward_season` (`season_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='赛季奖励梯度(rank_min<=名次<=rank_max)';

CREATE TABLE IF NOT EXISTS `pet_season_ranking` (
    `id`         BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `season_id`  BIGINT UNSIGNED NOT NULL COMMENT '赛季ID',
    `pet_id`     BIGINT UNSIGNED NOT NULL COMMENT '宠物ID',
    `user_id`    BIGINT UNSIGNED NOT NULL COMMENT '主人用户ID',
    `rank_no`    INT NOT NULL COMMENT '最终名次(1基)',
    `level`      INT NOT NULL COMMENT '结算时等级',
    `exp`        INT NOT NULL COMMENT '结算时经验',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    PRIMARY KEY `pk_pet_season_ranking` (`id`),
    UNIQUE KEY `uk_season_ranking_pet` (`season_id`, `pet_id`),
    INDEX `idx_season_ranking_user` (`season_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='赛季最终榜快照(历史名次依据)';
