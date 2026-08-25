-- =============================================
-- CloudMart 心愿宇宙模块 数据库迁移 V12
-- 模块: mall-wish
-- 说明: 心愿宇宙背景音乐曲库（管理端上传歌曲 + 勾选播放列表）
-- 背景:
--   四端 WishBGM 组件原硬编码单曲 URL（OSS bgm/wish-universe-ambient.mp3），
--   管理后台无法换歌。本表落地"管理员上传 mp3 → 登记 → 勾选哪几首播放"
--   的曲库能力（文档 Sprint 2.3 补充需求，2026-08-22 用户确认）。
-- 链路:
--   管理后台上传 mp3 → mall-file POST /file/upload（白名单已含 mp3，上限
--   50MB）→ 拿到 URL 调 mall-wish 登记本表 → 四端 GET /bgm/playlist
--   拉 is_active=1 列表顺序循环播放；空列表回退默认曲（前端内置）。
-- 设计口径:
--   单表 + is_active 勾选 + sort 排序（不建歌库/播放列表双表——管理场景
--   简单，避免过度设计）；物理删除（音频元数据非核心业务数据，OSS 文件
--   本身保留）。
-- 时区: created_at/updated_at 由数据库 DEFAULT CURRENT_TIMESTAMP 维护 (UTC)
-- =============================================

CREATE TABLE IF NOT EXISTS `wish_bgm_song` (
    `id`          BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花ID)',
    `title`       VARCHAR(128) NOT NULL COMMENT '歌曲标题(管理端展示与四端播放器曲名)',
    `url`         VARCHAR(512) NOT NULL COMMENT '音频地址(mall-file 上传 OSS 后的 URL,http(s)直链)',
    `file_size`   BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '文件大小(字节,mall-file 上传返回;展示用)',
    `sort`        INT NOT NULL DEFAULT 0 COMMENT '播放顺序(升序;同序按 id 稳定排序)',
    `is_active`   TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否在播放列表(1=播放;多首激活=顺序循环播放)',
    `uploaded_by` BIGINT UNSIGNED NOT NULL COMMENT '上传管理员用户ID(审计)',
    `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_wish_bgm_song` (`id`),
    KEY `idx_bgm_playlist` (`is_active`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='心愿宇宙背景音乐曲库(Sprint 2.3,管理端上传歌曲+勾选播放列表)';
