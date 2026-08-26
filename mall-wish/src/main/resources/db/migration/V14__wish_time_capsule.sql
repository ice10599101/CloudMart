-- =============================================
-- CloudMart 心愿宇宙模块 数据库迁移 V12
-- 模块: mall-wish
-- 说明: Sprint 2.4 时间胶囊（文档 2.7 / 表⑦）
-- 状态机: SEALED --(mall-job 扫描 open_at 到期)--> AVAILABLE
--         --(用户点击开启)--> OPENED；SEALED/AVAILABLE --(用户取消)--> CANCELLED
-- 时区策略(文档 26.3): open_at/opened_at 统一存储 UTC，到期判定直接比较
--         UTC open_at（用户跨时区旅行不影响到期）；open_at_timezone 仅记录
--         创建时用户 IANA 时区，用于回溯展示"创建时本地时间"与审计
-- ID策略: 雪花算法 (MyBatis-Plus assign_id), 不使用 AUTO_INCREMENT
-- 字符集: utf8mb4_0900_ai_ci, 引擎: InnoDB
-- =============================================

-- ---------------------------------------------
-- time_capsule 时间胶囊
-- title 列为 API 契约字段（文档 2.7 body: title String(100, required)），
-- 表⑦原文遗漏该列，以 API 契约为准补充（先例：V7 feeling 列）
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `time_capsule` (
                                              `id`                 BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
                                              `user_id`            BIGINT UNSIGNED NOT NULL COMMENT '所属用户ID',
                                              `title`              VARCHAR(100) NOT NULL COMMENT '胶囊标题(≤100字)',
    `content`            TEXT NOT NULL COMMENT '胶囊内容(≤5000字, 未开启不返回)',
    `media_urls`         JSON DEFAULT NULL COMMENT '封存媒体URL列表(OSS Key)',
    `open_at`            DATETIME NOT NULL COMMENT '预定开启时间(UTC, 到期判定唯一依据)',
    `open_at_timezone`   VARCHAR(32) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '创建时用户IANA时区(仅回溯展示/审计, 不参与到期判定)',
    `opened_at`          DATETIME DEFAULT NULL COMMENT '实际开启时间(UTC)',
    `status`             ENUM('SEALED','AVAILABLE','OPENED','CANCELLED') NOT NULL DEFAULT 'SEALED' COMMENT '状态: 封印中/已到期待开启/已开启/已取消',
    `created_at`         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_time_capsule` (`id`),
    INDEX `idx_capsule_user` (`user_id`, `id`),
    INDEX `idx_capsule_open` (`open_at`, `status`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='时间胶囊(表⑦)';
