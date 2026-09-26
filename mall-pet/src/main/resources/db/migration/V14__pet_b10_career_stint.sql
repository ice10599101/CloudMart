-- V14: B10 职业任职历史（stint）
-- 现状：pet_career_progress 单行承载"当前进度 + 历史关闭时间"，重新入职会把 promotedAt 清空，
--      原晋升历史被抹掉；晋升次数判断也混用历史累计。
-- 方案：独立任职记录 pet_career_stint——每段任职有入职/结束时间、结束原因与该段累计收益；
--      晋升条件按"当前开放段"的次数判断；pet_career_progress 保留终身聚合（展示兼容）。

CREATE TABLE IF NOT EXISTS `pet_career_stint` (
    `id`             BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `pet_id`         BIGINT UNSIGNED NOT NULL COMMENT '宠物ID',
    `user_id`        BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `career_code`    VARCHAR(60) NOT NULL COMMENT '职业编码(pet_career_config.code)',
    `started_at`     DATETIME NOT NULL COMMENT '入职时间(UTC)',
    `ended_at`       DATETIME DEFAULT NULL COMMENT '结束时间(UTC,开放段为空)',
    `end_reason`     ENUM('PROMOTED','LEFT') DEFAULT NULL COMMENT '结束原因:晋升离开/主动转职',
    `work_count`     INT NOT NULL DEFAULT 0 COMMENT '该段累计工作次数(晋升判断依据)',
    `total_currency` INT NOT NULL DEFAULT 0 COMMENT '该段累计星光收入',
    `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_career_stint` (`id`),
    UNIQUE KEY `uk_career_stint_open` (`pet_id`, `career_code`, (IF(`ended_at` IS NULL, 1, NULL))),
    INDEX `idx_career_stint_pet` (`pet_id`, `started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='职业任职记录(保留入职/离职时间与该段收益,晋升历史不清除)';

-- 存量回填：为每个有进度的 (pet, career) 生成一段历史记录（开启中的按开启处理）
INSERT IGNORE INTO `pet_career_stint` (`id`, `pet_id`, `user_id`, `career_code`, `started_at`,
                                       `ended_at`, `end_reason`, `work_count`, `total_currency`)
SELECT p.`id`, p.`pet_id`, p.`user_id`, p.`career_code`,
       COALESCE(p.`started_at`, p.`created_at`, UTC_TIMESTAMP()),
       p.`promoted_at`,
       CASE WHEN p.`promoted_at` IS NULL THEN NULL ELSE 'PROMOTED' END,
       COALESCE(p.`work_count`, 0), COALESCE(p.`total_currency`, 0)
FROM `pet_career_progress` p;
