-- V2: 成长体系 - 用户等级、经验值、每日签到

CREATE TABLE IF NOT EXISTS `level_configs` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `level` INT UNSIGNED NOT NULL COMMENT '等级',
    `title` VARCHAR(30) NOT NULL COMMENT '等级称号',
    `min_exp` INT UNSIGNED NOT NULL COMMENT '所需最低经验值',
    `icon` VARCHAR(255) DEFAULT NULL COMMENT '等级图标URL',
    `benefits` JSON DEFAULT NULL COMMENT '等级权益描述',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-禁用 1-启用',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_level` (`level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='等级配置表';

CREATE TABLE IF NOT EXISTS `user_levels` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `level` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '当前等级',
    `exp` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '当前经验值',
    `total_exp` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '累计经验值',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户等级表';

CREATE TABLE IF NOT EXISTS `daily_check_ins` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `check_in_date` DATE NOT NULL COMMENT '签到日期',
    `continuous_days` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '连续签到天数',
    `exp_reward` INT UNSIGNED NOT NULL DEFAULT 10 COMMENT '获得经验值',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_date` (`user_id`, `check_in_date`),
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='每日签到记录';

CREATE TABLE IF NOT EXISTS `exp_logs` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `exp_change` INT NOT NULL COMMENT '经验值变化(正数增加/负数扣除)',
    `source` VARCHAR(30) NOT NULL COMMENT '来源: CHECK_IN/POST/LIKE_RECEIVED/COMMENT_RECEIVED/FOLLOW_RECEIVED/COLLECT_RECEIVED',
    `biz_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '关联业务ID',
    `description` VARCHAR(200) DEFAULT NULL COMMENT '描述',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_source` (`source`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='经验值变动日志';

INSERT IGNORE INTO `level_configs` (`level`, `title`, `min_exp`, `icon`, `benefits`) VALUES
(1, '小答新手', 0, '🌱', '["基础功能"]'),
(2, '小答达人', 100, '🌿', '["基础功能", "自定义头像框"]'),
(3, '小答高手', 500, '🌳', '["基础功能", "自定义头像框", "专属标签"]'),
(4, '小答专家', 2000, '⭐', '["基础功能", "自定义头像框", "专属标签", "优先推荐"]'),
(5, '小答大师', 5000, '🌟', '["基础功能", "自定义头像框", "专属标签", "优先推荐", "官方活动优先"]'),
(6, '小答传奇', 15000, '👑', '["全部功能", "专属标识", "官方认证", "活动特权"]');
