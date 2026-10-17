-- =============================================================================
-- V2__file_asset_admin_domain.sql — ADMIN-04：管理员文件域与业务用途
-- owner_type 区分 USER/ADMIN：后台运营资产（BGM/礼物图/软件素材）不进入用户资产空间；
-- created_by 记录上传操作者（管理员域=管理员账号；用户域与 owner_id 一致）；
-- purpose 为业务用途标签（后台上传必填），用于检索与下载授权审计。
-- =============================================================================

ALTER TABLE `file_asset`
  ADD COLUMN `owner_type` varchar(20) NOT NULL DEFAULT 'USER' COMMENT '归属域：USER/ADMIN（管理员运营资产）' AFTER `owner_id`,
  ADD COLUMN `created_by` bigint unsigned DEFAULT NULL COMMENT '上传操作者ID（管理员域=管理员账号；用户域=owner_id）' AFTER `owner_type`,
  ADD COLUMN `purpose` varchar(64) DEFAULT NULL COMMENT '业务用途（GIFT_IMAGE/WISH_BGM/SOFTWARE_ASSET/ADMIN_ASSET 等）' AFTER `created_by`,
  ADD KEY `idx_file_asset_owner_type` (`owner_type`, `created_at`);

-- 存量回填：原 owner_id 为 NULL 的行即平台/无主资产，归入 ADMIN 域
UPDATE `file_asset`
SET `owner_type` = IF(`owner_id` IS NULL, 'ADMIN', 'USER'),
    `created_by` = `owner_id`;
