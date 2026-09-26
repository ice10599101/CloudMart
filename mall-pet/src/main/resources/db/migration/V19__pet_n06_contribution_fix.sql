-- V19: N06 修复——pet_cooperation_contribution 缺 updated_at 列
-- 现象：贡献实体带 FieldFill.INSERT_UPDATE 的 updatedAt 字段，但建表时漏建该列，
--      插入贡献行报 Unknown column 'updated_at'，并把喂食事务整体回滚。

ALTER TABLE `pet_cooperation_contribution`
    ADD COLUMN `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)' AFTER `created_at`;
