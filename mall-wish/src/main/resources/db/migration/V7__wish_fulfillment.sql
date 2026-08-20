-- =============================================
-- CloudMart 心愿宇宙模块 数据库迁移 V7
-- 模块: mall-wish
-- 说明: Sprint 1.10 还愿链路（文档 2.4 / 表⑨）
-- 状态机: ACTIVE/OVERDUE --提交还愿--> FULFILLED（产品决策 2026-08-20：
--         统一即时生效，先发后审；FULFILLING 保留给后续 STRICT 审核流）
-- ID策略: 雪花算法 (MyBatis-Plus assign_id), 不使用 AUTO_INCREMENT
-- 字符集: utf8mb4_0900_ai_ci, 引擎: InnoDB
-- 时区: 所有 DATETIME 字段统一存储 UTC (见文档第26章)
-- =============================================

-- ---------------------------------------------
-- wish_fulfillment 还愿记录（与 wish 1:1，uk_fulfillment_wish 兜底防重复提交）
-- feeling 列为 API 契约字段（文档 2.4 body: feeling String(1000)?），
-- 表⑨原文遗漏该列，以 API 契约为准补充
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_fulfillment` (
    `id`            BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `wish_id`       BIGINT UNSIGNED NOT NULL COMMENT '心愿ID(1:1)',
    `user_id`       BIGINT UNSIGNED NOT NULL COMMENT '还愿用户ID(即作者)',
    `story`         TEXT NOT NULL COMMENT '还愿故事(≤5000字, 已XSS转义)',
    `media_urls`    JSON DEFAULT NULL COMMENT '完成照片/视频URL列表(OSS Key)',
    `feeling`       VARCHAR(1000) DEFAULT NULL COMMENT '感悟(≤1000字, API契约字段)',
    `audit_status`  ENUM('PENDING','APPROVED','REJECTED','AUTO_HIDDEN') NOT NULL DEFAULT 'PENDING' COMMENT '审核状态: 先发后审, 提交即生效仅标记待审',
    `is_visible`    TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否可见(与audit_status解耦, 先发后审)',
    `is_inherited`  TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已传承推送(Sprint 2.7 愿望传承)',
    `deleted_at`    DATETIME DEFAULT NULL COMMENT '软删除时间(作者撤回还愿故事, 保留审计)',
    `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_wish_fulfillment` (`id`),
    UNIQUE KEY `uk_fulfillment_wish` (`wish_id`),
    INDEX `idx_fulfillment_user` (`user_id`, `created_at`),
    INDEX `idx_fulfillment_audit` (`audit_status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='还愿记录(与wish 1:1)';
