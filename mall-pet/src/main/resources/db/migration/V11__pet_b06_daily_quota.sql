-- V11: B06 每日收益额度持久化（Redis 仅作快速限频，不能作为有价值奖励的唯一去重依据）
-- 设计：原子条件更新（used < limit 才递增）+ 行不存在插入占位；主体=用户（切换宠物/入口不绕过），
--      目标维度(target)支持"每用户对每目标"细粒度额度（如 PvP 每天对同一用户 1 场收益）。
--      business_date 按 businessZone 计算，配额按业务日隔离。

CREATE TABLE IF NOT EXISTS `pet_daily_quota` (
    `id`            BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `user_id`       BIGINT UNSIGNED NOT NULL COMMENT '主体用户ID(额度按用户共享,与主宠无关)',
    `quota_type`    VARCHAR(40) NOT NULL COMMENT '额度类型:FEED/PLAY_REWARD/REST_INTIMACY/BATTLE_REWARD/PVP_DAILY/WALL_POST/VISIT_REWARD/FRIEND_VISIT_REWARD/LIKE_REWARD/MINIGAME/DECORATE/DEFEAT_TARGET',
    `target_id`     BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '目标维度(0=用户级全局;非0=按目标细分的额度,如对战对手用户ID)',
    `business_date` DATE NOT NULL COMMENT '业务日(businessZone)',
    `used`          INT NOT NULL DEFAULT 0 COMMENT '已用次数(原子条件递增,不超过limit由服务层保证)',
    `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_daily_quota` (`id`),
    UNIQUE KEY `uk_pet_daily_quota` (`user_id`, `quota_type`, `target_id`, `business_date`),
    INDEX `idx_pet_daily_quota_user` (`user_id`, `business_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户每日收益额度(数据库权威,Redis故障不发奖)';
