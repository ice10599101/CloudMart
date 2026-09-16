-- =============================================
-- CloudMart 心愿宇宙模块 数据库迁移 V34
-- 模块: mall-wish
-- 说明: 漂流瓶投瓶编辑器升级为富文本（与发帖同一编辑器），content 由 VARCHAR(500) 扩为 TEXT
-- =============================================

ALTER TABLE `wish_drift_bottle`
    MODIFY COLUMN `content` TEXT DEFAULT NULL COMMENT '自由匿名文字(富文本HTML,与wish_id二选一)';