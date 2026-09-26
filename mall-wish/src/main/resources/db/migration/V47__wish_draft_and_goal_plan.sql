-- V47: 心愿草稿 + AI 目标计划补齐（N03/N04，P2）
CREATE TABLE IF NOT EXISTS `wish_draft` (
    `id`              BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花)',
    `user_id`         BIGINT UNSIGNED NOT NULL COMMENT '作者',
    `client_draft_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '客户端草稿标识(断网恢复不重复建)',
    `title`           VARCHAR(120) DEFAULT NULL COMMENT '标题(可不完整)',
    `description`     MEDIUMTEXT DEFAULT NULL COMMENT '描述(可不完整)',
    `category_id`     BIGINT UNSIGNED DEFAULT NULL COMMENT '分类',
    `media_urls`      VARCHAR(2000) DEFAULT NULL COMMENT '媒体(JSON)',
    `tags`            VARCHAR(500) DEFAULT NULL COMMENT '标签(JSON)',
    `expected_at`     DATETIME DEFAULT NULL COMMENT '预计完成时间',
    `expected_timezone` VARCHAR(64) DEFAULT NULL COMMENT '业务时区',
    `visibility`      VARCHAR(16) DEFAULT NULL COMMENT '可见性',
    `published_wish_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '发布后的心愿ID(发布幂等)',
    `version`         INT NOT NULL DEFAULT 0 COMMENT '乐观锁',
    `deleted_at`      DATETIME(3) DEFAULT NULL COMMENT '软删',
    `created_at`      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY `pk_wish_draft` (`id`),
    UNIQUE KEY `uk_draft_client` (`user_id`, `client_draft_id`),
    INDEX `idx_draft_user` (`user_id`, `deleted_at`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='心愿草稿(N03:每人最多20份,不进公共feed)';

ALTER TABLE `wish_ai_goal`
    ADD COLUMN `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序(N04)' AFTER `priority`,
    ADD COLUMN `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁(N04)' AFTER `sort_order`;
