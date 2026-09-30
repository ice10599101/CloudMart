-- 管理端写操作 500 修正（远程联调验收发现）：pet_config_version 缺 updated_at 列——
-- 实体 updatedAt 标 FieldFill.INSERT_UPDATE，插入时带该列导致 Unknown column。
-- 与同族表对齐补列（审计版本行只需创建时间语义，updated_at 与 created_at 同步维护即可）。

ALTER TABLE `pet_config_version`
    ADD COLUMN `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
        COMMENT '更新时间(UTC)' AFTER `created_at`;
