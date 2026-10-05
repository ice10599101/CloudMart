-- PET-09：每日任务集实体化——每 pet/businessDate 一个真实任务集（生成一次后冻结，
-- 不随当日升级/配置编辑扩大），setId 为真实实体 ID，领取/展示/进度全部绑定集与快照。
-- 表结构与索引遵循 §7.2 目标模型：唯一 (pet_id, business_date)，领取保留至下一业务日结束。

CREATE TABLE IF NOT EXISTS `pet_daily_quest_set` (
    `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`        BIGINT UNSIGNED NOT NULL                COMMENT '归属用户',
    `pet_id`         BIGINT UNSIGNED NOT NULL                COMMENT '绑定宠物（集内任务归属固定，切换主宠不改变集归属）',
    `business_date`  DATE            NOT NULL                COMMENT '业务日（Asia/Shanghai）',
    `timezone`       VARCHAR(64)     NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '业务日时区（集生成时固定）',
    `level_snapshot` INT             NOT NULL DEFAULT 1      COMMENT '生成时宠物等级（当日升级不扩大既有集合，新配置下一集生效）',
    `generated_at`   DATETIME        NOT NULL                COMMENT '集生成时间（UTC）',
    `claim_deadline` DATETIME        NOT NULL                COMMENT '领取截止（下一业务日结束，24h 宽限）',
    `status`         VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/EXPIRED',
    `chest_snapshot` VARCHAR(512)    NOT NULL DEFAULT '{}'   COMMENT '宝箱奖励快照（生成时冻结）',
    `chest_claimed_at` DATETIME      NULL                    COMMENT '宝箱领取时间',
    `created_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pet_quest_set_date` (`pet_id`, `business_date`),
    KEY `idx_pet_quest_set_user_date` (`user_id`, `business_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '宠物每日任务集（PET-09：每宠每业务日生成一次的冻结任务集合）';

ALTER TABLE `pet_daily_quest`
    ADD COLUMN `set_id` BIGINT UNSIGNED NULL COMMENT '所属任务集（PET-09：领取/展示按集绑定与归属校验）' AFTER `quest_date`,
    ADD INDEX `idx_pet_daily_quest_set` (`set_id`);

-- 存量回填：按 (pet_id, quest_date) 分组生成 legacy 集（一次性补齐，历史任务保留已领状态；
-- 宝箱快照不可恢复历史值，标 legacy 由服务按既有快照/配置兜底，禁止每次随当前配置漂移）
INSERT INTO `pet_daily_quest_set`
    (`user_id`, `pet_id`, `business_date`, `timezone`, `level_snapshot`, `generated_at`,
     `claim_deadline`, `status`, `chest_snapshot`, `created_at`, `updated_at`)
SELECT q.`user_id`, q.`pet_id`, q.`quest_date`, 'Asia/Shanghai', 1, UTC_TIMESTAMP(),
       q.`quest_date` + INTERVAL 1 DAY, 'ACTIVE',
       '{"legacy": true}', UTC_TIMESTAMP(), UTC_TIMESTAMP()
FROM (SELECT DISTINCT `user_id`, `pet_id`, `quest_date` FROM `pet_daily_quest`) q;

UPDATE `pet_daily_quest` q
    JOIN `pet_daily_quest_set` s
        ON s.`pet_id` = q.`pet_id` AND s.`business_date` = q.`quest_date`
SET q.`set_id` = s.`id`
WHERE q.`set_id` IS NULL;
