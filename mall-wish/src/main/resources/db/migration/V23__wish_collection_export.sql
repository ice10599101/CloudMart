-- =============================================
-- CloudMart 心愿宇宙模块 数据库迁移 V23
-- 模块: mall-wish
-- 说明: 补齐缺口（审计发现）：心愿收藏 2.12 + 数据导出 2.13
--       wish_collection        心愿收藏（收藏他人心愿，uk 防重复）
--       wish_data_export       数据导出任务（合规 34.2 异步导出）
-- ID策略: 雪花算法 (MyBatis-Plus assign_id), 不使用 AUTO_INCREMENT
-- 字符集: utf8mb4_0900_ai_ci, 引擎: InnoDB
-- =============================================

-- ---------------------------------------------
-- wish_collection 心愿收藏（收藏他人心愿到个人收藏列表）
-- uk(user, wish) 防重复收藏；软删取消收藏。
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_collection` (
    `id`           BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `user_id`      BIGINT UNSIGNED NOT NULL COMMENT '收藏者用户ID',
    `wish_id`      BIGINT UNSIGNED NOT NULL COMMENT '被收藏心愿ID',
    `collected_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间(UTC)',
    `deleted_at`   DATETIME DEFAULT NULL COMMENT '取消收藏时间(UTC,软删)',
    PRIMARY KEY `pk_wish_collection` (`id`),
    UNIQUE KEY `uk_collection_user_wish` (`user_id`, `wish_id`),
    INDEX `idx_collection_user` (`user_id`, `deleted_at`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='心愿收藏(用户级)';

-- ---------------------------------------------
-- wish_data_export 数据导出任务（合规 34.2 异步导出）
-- status: PENDING → PROCESSING → SUCCESS/FAILED
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_data_export` (
    `id`          BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `user_id`     BIGINT UNSIGNED NOT NULL COMMENT '申请用户',
    `status`      ENUM('PENDING','PROCESSING','SUCCESS','FAILED') NOT NULL DEFAULT 'PENDING' COMMENT '任务状态',
    `download_url` VARCHAR(500) DEFAULT NULL COMMENT '下载链接(SUCCESS 后回填)',
    `expires_at`  DATETIME DEFAULT NULL COMMENT '下载链接过期时间(UTC)',
    `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_wish_data_export` (`id`),
    INDEX `idx_export_user` (`user_id`, `created_at`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='数据导出任务(合规34.2)';
