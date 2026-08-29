-- =============================================
-- CloudMart 心愿宇宙模块 数据库迁移 V20
-- 模块: mall-wish
-- 说明: Sprint 3.4 心愿直播挂件（文档 1.2 ㊱ wish_live_widget_config）
--       主播挂件配置表（streamer_id 唯一 + position 枚举 + style JSON）
--       挂件数据本身实时聚合（无表）；全局降级开关复用灰度配置
--       （feature_key=wish_live_widget，比例 0=隐藏/100=展示）
-- ID策略: 雪花算法 (MyBatis-Plus assign_id), 不使用 AUTO_INCREMENT
-- 字符集: utf8mb4_0900_ai_ci, 引擎: InnoDB
-- =============================================

CREATE TABLE IF NOT EXISTS `wish_live_widget_config` (
    `id`           BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `streamer_id`  BIGINT UNSIGNED NOT NULL COMMENT '主播用户ID(唯一)',
    `position`     ENUM('TOP_LEFT','TOP_RIGHT','BOTTOM_LEFT','BOTTOM_RIGHT') NOT NULL DEFAULT 'BOTTOM_RIGHT' COMMENT '挂件位置',
    `style_config` JSON DEFAULT NULL COMMENT '样式配置 JSON(如 {"transparent":true,"accent":"#ffd700"})',
    `is_visible`   TINYINT(1) NOT NULL DEFAULT 1 COMMENT '该主播挂件是否展示',
    `updated_by`   BIGINT UNSIGNED DEFAULT NULL COMMENT '最后修改人(管理后台用户ID)',
    `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_wish_live_widget_config` (`id`),
    UNIQUE KEY `uk_widget_streamer` (`streamer_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='直播心愿挂件配置(主播维度)';
