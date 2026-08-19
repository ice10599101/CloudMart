-- =============================================
-- CloudMart 心愿宇宙模块 数据库迁移 V2
-- 模块: mall-wish
-- 说明: Sprint 1.2 心愿评论表
-- 决策: 文档 2.2 节原计划 Feign 调用 mall-community 评论 API，
--       但 mall-community PostCommentService 强校验 posts 表存在性（POST_NOT_FOUND），
--       无法服务 wish 资源；为避免侵入式改造社区模块、保证评论与互动计数同库事务一致性，
--       本模块自建 wish_comment 表（架构偏差已在开发进度文档中说明）。
-- ID策略: 雪花算法 (MyBatis-Plus assign_id), 不使用 AUTO_INCREMENT
-- 字符集: utf8mb4_0900_ai_ci, 引擎: InnoDB
-- 时区: 所有 DATETIME 字段统一存储 UTC (见文档第26章)
-- =============================================

-- ---------------------------------------------
-- wish_comment 心愿评论（支持二级回复 parentId）
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_comment` (
    `id`                BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `wish_id`           BIGINT UNSIGNED NOT NULL COMMENT '心愿ID',
    `user_id`           BIGINT UNSIGNED NOT NULL COMMENT '评论用户ID',
    `parent_id`         BIGINT UNSIGNED DEFAULT NULL COMMENT '父评论ID(顶级评论为NULL, 仅支持二级回复)',
    `reply_to_user_id`  BIGINT UNSIGNED DEFAULT NULL COMMENT '被回复用户ID(回复时冗余存储)',
    `content`           VARCHAR(500) NOT NULL COMMENT '评论内容(已XSS转义+敏感词过滤后存储)',
    `like_count`        INT NOT NULL DEFAULT 0 COMMENT '点赞数(预留,Sprint 1.2 不启用点赞API)',
    `status`            ENUM('VISIBLE','HIDDEN') NOT NULL DEFAULT 'VISIBLE' COMMENT '状态: VISIBLE可见/HIDDEN已下架(含敏感词自动下架与管理员手动下架)',
    `sensitive_hit`     TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否命中敏感词(仅标记不阻断,先发后审,管理后台筛选用)',
    `deleted_at`        DATETIME DEFAULT NULL COMMENT '软删除时间(用户删除自己的评论)',
    `created_at`        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_wish_comment` (`id`),
    INDEX `idx_comment_wish` (`wish_id`, `status`, `created_at`),
    INDEX `idx_comment_parent` (`parent_id`),
    INDEX `idx_comment_user` (`user_id`, `created_at`),
    INDEX `idx_comment_sensitive` (`sensitive_hit`, `status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='心愿评论(二级回复)';
