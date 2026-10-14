-- C26 clean development/test baseline. Recreated EMPTY databases only.
-- Materialized from verified MySQL schema; never repair an old history to adopt this file.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE `file_asset` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '资产ID（对外 string）',
  `owner_id` bigint unsigned DEFAULT NULL COMMENT '归属用户ID；NULL=平台/存量无主（LEGACY_UNCLAIMED 仅管理员可处置）',
  `original_name` varchar(255) NOT NULL COMMENT '原始文件名（展示用，已去路径）',
  `storage_key` varchar(512) NOT NULL COMMENT '存储键（相对存储根路径，唯一）',
  `mime` varchar(100) NOT NULL COMMENT '实际嗅探的 MIME 类型（魔数判定，非客户端声明）',
  `size_bytes` bigint unsigned NOT NULL COMMENT '文件大小（字节）',
  `sha256` char(64) NOT NULL COMMENT '内容 SHA-256（去重与完整性）',
  `visibility` varchar(20) NOT NULL DEFAULT 'PUBLIC' COMMENT '可见性：PUBLIC/PRIVATE（私聊/售后凭证等）',
  `status` varchar(30) NOT NULL DEFAULT 'READY' COMMENT '状态：READY/DELETED/LEGACY_UNCLAIMED',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_asset_storage_key` (`storage_key`),
  KEY `idx_file_asset_owner` (`owner_id`,`created_at`),
  KEY `idx_file_asset_sha256` (`sha256`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文件资产台账';

CREATE TABLE `file_reference` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '引用ID',
  `asset_id` bigint unsigned NOT NULL COMMENT '被引用的 file_asset.id',
  `biz_type` varchar(50) NOT NULL COMMENT '业务类型（如 POST/AVATAR/AFTER_SALE）',
  `biz_id` varchar(64) NOT NULL COMMENT '业务主键',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_reference_biz` (`asset_id`,`biz_type`,`biz_id`),
  KEY `idx_file_reference_asset` (`asset_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文件业务引用';
