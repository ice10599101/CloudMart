-- =============================================================================
-- V1__software_catalog.sql — 小答软件：软件主体、内容快照、版本与下载来源、
--                             来源状态、分类/标签/素材关系（方案 6.2~6.7）
-- 约定（6.1）：
--   * 主键 BIGINT UNSIGNED AUTO_INCREMENT，API 层对前端返回字符串；
--   * 引擎 InnoDB、utf8mb4_0900_ai_ci；opaque key / 指纹用 ascii_bin 大小写敏感；
--   * 跨服务 ID 只保存逻辑引用，不建跨库外键；同库关联由应用事务与索引保证；
--   * 时间列 DATETIME(3)，服务端产生。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 软件主体（6.2）：归属与运营状态；内容在快照，双指针 published/working
-- -----------------------------------------------------------------------------
CREATE TABLE `software` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `owner_id` bigint unsigned NOT NULL COMMENT '分享者（mall-user 前台会员ID，逻辑引用）',
  `lifecycle` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '生命周期:ACTIVE/UNLISTED/BANNED/DELETED（作者下架=UNLISTED；平台处置=BANNED）',
  `published_revision_id` bigint unsigned DEFAULT NULL COMMENT '当前公开快照指针（须属于本软件，应用层校验）',
  `working_revision_id` bigint unsigned DEFAULT NULL COMMENT '唯一工作快照指针（须属于本软件，应用层校验）',
  `publication_seq` int unsigned NOT NULL DEFAULT '0' COMMENT '内部通知去重序号（不进客户端协议；SW09 替换为 eventId）',
  `featured_rank` int NOT NULL DEFAULT '0' COMMENT '首页精选排序，0=不精选',
  `comment_count` int unsigned NOT NULL DEFAULT '0' COMMENT '可见评论数（可重建）',
  `favorite_count` int unsigned NOT NULL DEFAULT '0' COMMENT '收藏关系数（可重建）',
  `rating_count` int unsigned NOT NULL DEFAULT '0' COMMENT '有效评分人数（P1）',
  `rating_sum` int unsigned NOT NULL DEFAULT '0' COMMENT '有效评分星数和（P1）',
  `link_access_count` bigint unsigned NOT NULL DEFAULT '0' COMMENT '去重获取链接累计数（可重建，非外部下载量）',
  `published_at` datetime(3) DEFAULT NULL COMMENT '首次上线时间',
  `last_release_at` datetime(3) DEFAULT NULL COMMENT '最新新增版本上线时间',
  `deleted_at` datetime(3) DEFAULT NULL COMMENT '软删除时间',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  CONSTRAINT `chk_software_lifecycle` CHECK (`lifecycle` IN ('ACTIVE','UNLISTED','BANNED','DELETED')),
  KEY `idx_software_owner` (`owner_id`,`updated_at`,`id`),
  KEY `idx_software_lifecycle_release` (`lifecycle`,`last_release_at`,`id`),
  KEY `idx_software_lifecycle_featured` (`lifecycle`,`featured_rank`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='软件主体（归属/运营状态/双快照指针）';

-- -----------------------------------------------------------------------------
-- 内容快照（6.3）：名称/正文/查看条件整体克隆、冻结、审核、切换；已审快照不可原地编辑
-- -----------------------------------------------------------------------------
CREATE TABLE `software_revision` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `software_id` bigint unsigned NOT NULL COMMENT '所属软件',
  `editor_id` bigint unsigned NOT NULL COMMENT '编辑人（前台会员ID）',
  `review_status` varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT '审核状态:DRAFT/PENDING/APPROVED/REJECTED（PENDING 冻结，可撤回 DRAFT）',
  `name` varchar(80) NOT NULL DEFAULT '' COMMENT '软件名（草稿可空，提交审核时 2~80 字）',
  `summary` varchar(160) NOT NULL DEFAULT '' COMMENT '一句话简介（提交时 10~160 字）',
  `category_id` bigint unsigned DEFAULT NULL COMMENT '运营分类（逻辑引用 software_category）',
  `icon_asset_id` bigint unsigned DEFAULT NULL COMMENT '图标素材（mall-file 资产ID，逻辑引用）',
  `description_html` mediumtext COMMENT '服务端消毒后的富文本介绍',
  `description_text` text COMMENT '从消毒后 HTML 提取的纯文本（供搜索与敏感词审核）',
  `developer_name` varchar(80) DEFAULT NULL COMMENT '软件开发者名称（区别于本站分享者）',
  `official_url` varchar(2048) DEFAULT NULL COMMENT '官网 HTTPS 链接（可选）',
  `license_type` varchar(16) DEFAULT NULL COMMENT '授权类型:FREE/OPEN_SOURCE/TRIAL/COMMERCIAL',
  `attribution_type` varchar(24) DEFAULT NULL COMMENT '来源声明:ORIGINAL/AUTHORIZED/OFFICIAL_PUBLIC',
  `attribution_note` varchar(1000) DEFAULT NULL COMMENT '转载/授权说明（不冒充授权证明）',
  `system_requirements` varchar(300) DEFAULT NULL COMMENT '最低系统要求',
  `languages` json DEFAULT NULL COMMENT '语言代码数组',
  `access_mode` varchar(24) NOT NULL DEFAULT 'REGISTERED' COMMENT '链接查看条件:REGISTERED/COMMUNITY_LEVEL/ADMIN_PAID（整款软件单选，适用于全部来源）',
  `min_community_level` int unsigned DEFAULT NULL COMMENT '等级模式最低社区等级（须为现有启用等级；仅 COMMUNITY_LEVEL 允许非空）',
  `access_contact_id` bigint unsigned DEFAULT NULL COMMENT '付款模式管理员联系入口（仅 ADMIN_PAID 允许非空）',
  `access_note` varchar(500) DEFAULT NULL COMMENT '查看条件说明（不存源链接、密码或支付凭证）',
  `submitted_at` datetime(3) DEFAULT NULL COMMENT '最近提交审核时间',
  `reviewed_at` datetime(3) DEFAULT NULL COMMENT '审核完成时间',
  `reviewer_id` bigint unsigned DEFAULT NULL COMMENT '审核管理员ID（mall-admin 管理员，逻辑引用）',
  `review_reason` varchar(1000) DEFAULT NULL COMMENT '审核说明（退回原因等）',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_revision_software_submitted` (`software_id`,`submitted_at`,`id`),
  KEY `idx_revision_status_submit` (`review_status`,`submitted_at`,`id`),
  -- 按模式清除不适用字段（6.3）：草稿可暂缺所选模式必填项，提交时由服务端完整校验
  CONSTRAINT `chk_revision_access_combo` CHECK (
    (`access_mode` = 'REGISTERED' AND `min_community_level` IS NULL AND `access_contact_id` IS NULL)
    OR (`access_mode` = 'COMMUNITY_LEVEL' AND `access_contact_id` IS NULL)
    OR (`access_mode` = 'ADMIN_PAID' AND `min_community_level` IS NULL)
  ),
  CONSTRAINT `chk_revision_status` CHECK (`review_status` IN ('DRAFT','PENDING','APPROVED','REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='软件内容快照（草稿/待审/线上整体切换单元）';

-- -----------------------------------------------------------------------------
-- 版本（6.4）：快照内完整展示版本列表；复制快照保留 release_key
-- -----------------------------------------------------------------------------
CREATE TABLE `software_release` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `revision_id` bigint unsigned NOT NULL COMMENT '所属快照',
  `release_key` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '跨快照稳定版本键（服务端 UUID 生成，客户端不可伪造）',
  `version_name` varchar(40) NOT NULL COMMENT '版本号（1~40 字，不强制 SemVer）',
  `channel` varchar(16) NOT NULL DEFAULT 'STABLE' COMMENT '渠道:STABLE/BETA（recommended 每渠道最多一个，事务校验）',
  `release_order` int unsigned NOT NULL COMMENT '服务端排序值（禁止按版本字符串字典序判断新旧）',
  `release_date` date DEFAULT NULL COMMENT '发布日期（发布者声明）',
  `changelog` text COMMENT '更新日志（≤10000 字符）',
  `recommended` tinyint(1) NOT NULL DEFAULT '0' COMMENT '发布者推荐版本（每渠道最多一个）',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_release_key` (`revision_id`,`release_key`),
  UNIQUE KEY `uk_release_version` (`revision_id`,`version_name`,`channel`),
  KEY `idx_release_channel_order` (`revision_id`,`channel`,`release_order`,`id`),
  CONSTRAINT `chk_release_channel` CHECK (`channel` IN ('STABLE','BETA'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='软件版本（快照内版本列表）';

-- -----------------------------------------------------------------------------
-- 下载来源（6.5）：NETDISK/MAGNET/TORRENT_URL 外部链接；链接与提取码仅授权响应可返回
-- -----------------------------------------------------------------------------
CREATE TABLE `software_source` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `release_id` bigint unsigned NOT NULL COMMENT '所属版本',
  `source_key` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '跨快照稳定来源键（服务端 UUID；source_state/统计/反馈按此关联）',
  `source_type` varchar(16) NOT NULL COMMENT '来源类型:NETDISK/MAGNET/TORRENT_URL',
  `provider` varchar(24) NOT NULL COMMENT '网盘服务商:BAIDU/ALIYUN/QUARK/UC/ONEDRIVE/OTHER（服务端域名映射/审核确认）',
  `platform` varchar(16) NOT NULL COMMENT '平台:WINDOWS/MACOS/LINUX/ANDROID/IOS/CROSS_PLATFORM',
  `arch` varchar(16) NOT NULL COMMENT '架构:X64/X86/ARM64/UNIVERSAL/OTHER',
  `label` varchar(80) NOT NULL COMMENT '来源展示名（如“Windows 64 位稳定版”）',
  `url` text NOT NULL COMMENT '校验后的完整链接（服务端校验；禁止出现在公开详情/列表/日志）',
  `url_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '规范化 URL 哈希（服务端 SHA-256）',
  `content_fingerprint` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '内容指纹（规范化 URL+提取码+解压密码，服务端计算；核实结论按指纹匹配）',
  `extraction_code` varchar(32) DEFAULT NULL COMMENT '提取码（纯文本，保存 trim；仅授权响应可返回）',
  `archive_password` varchar(64) DEFAULT NULL COMMENT '压缩包解压密码（仅授权响应可返回）',
  `size_label` varchar(40) DEFAULT NULL COMMENT '文件大小描述（发布者声明，未经本站验证）',
  `claimed_sha256` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '发布者提供的 SHA-256（页面标注“发布者提供”）',
  `note` varchar(200) DEFAULT NULL COMMENT '来源说明（不含秘密信息）',
  `display_order` int NOT NULL DEFAULT '0' COMMENT '同版本内排序',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_source_key` (`release_id`,`source_key`),
  UNIQUE KEY `uk_source_target` (`release_id`,`platform`,`arch`,`url_hash`),
  KEY `idx_source_order` (`release_id`,`display_order`,`id`),
  CONSTRAINT `chk_source_type` CHECK (`source_type` IN ('NETDISK','MAGNET','TORRENT_URL')),
  CONSTRAINT `chk_source_platform` CHECK (`platform` IN ('WINDOWS','MACOS','LINUX','ANDROID','IOS','CROSS_PLATFORM')),
  CONSTRAINT `chk_source_arch` CHECK (`arch` IN ('X64','X86','ARM64','UNIVERSAL','OTHER')),
  CONSTRAINT `chk_source_provider` CHECK (`provider` IN ('BAIDU','ALIYUN','QUARK','UC','ONEDRIVE','OTHER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='下载来源（快照内外部链接，仅网盘/磁力/种子外链）';

-- -----------------------------------------------------------------------------
-- 来源状态（6.6）：核实结论独立于不可变已审快照；键 (software_id, source_key)
-- -----------------------------------------------------------------------------
CREATE TABLE `software_source_state` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `software_id` bigint unsigned NOT NULL COMMENT '软件ID',
  `source_key` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '稳定来源键（复制快照保持禁用继承）',
  `availability` varchar(16) NOT NULL DEFAULT 'UNKNOWN' COMMENT '可用性:UNKNOWN/AVAILABLE/SUSPECTED/INVALID/DISABLED（DISABLED 无条件禁用，其余按指纹匹配）',
  `checked_fingerprint` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '核实时的来源内容指纹（与当前不一致则结论不生效，展示 UNKNOWN）',
  `checked_at` datetime(3) DEFAULT NULL COMMENT '核实时间',
  `checked_by` bigint unsigned DEFAULT NULL COMMENT '核实管理员ID',
  `reason` varchar(500) DEFAULT NULL COMMENT '状态说明（对有权限主体可见）',
  `lock_version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_source_state` (`software_id`,`source_key`),
  CONSTRAINT `chk_source_state_availability` CHECK (`availability` IN ('UNKNOWN','AVAILABLE','SUSPECTED','INVALID','DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='来源核实/禁用状态（独立于不可变快照，按 source_key 继承）';

-- -----------------------------------------------------------------------------
-- 分类与标签（6.7）
-- -----------------------------------------------------------------------------
CREATE TABLE `software_category` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(40) NOT NULL COMMENT '分类名',
  `slug` varchar(64) NOT NULL COMMENT '分类标识（URL 用）',
  `icon` varchar(100) DEFAULT NULL COMMENT '图标标识',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '启用（禁用后不得新选，已发布项保留分类名）',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_category_slug` (`slug`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='软件运营分类（单层）';

CREATE TABLE `software_tag` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(20) NOT NULL COMMENT '标签名（2~20 字）',
  `normalized_name` varchar(40) NOT NULL COMMENT '规范化标签（去重键）',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '启用',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tag_normalized` (`normalized_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='软件标签（独立于社区 tags 语义）';

-- 快照-标签关系（纯关系表：无 id/updated_at；每快照 ≤5 由服务端校验）
CREATE TABLE `software_revision_tag` (
  `revision_id` bigint unsigned NOT NULL COMMENT '快照ID',
  `tag_id` bigint unsigned NOT NULL COMMENT '标签ID',
  PRIMARY KEY (`revision_id`,`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='快照标签关系（每快照≤5）';

-- 快照素材关系（6.7）：图标/截图/正文媒体；登记 mall-file 引用绑定（bizType=SOFTWARE_REVISION）
CREATE TABLE `software_revision_media` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `revision_id` bigint unsigned NOT NULL COMMENT '快照ID',
  `asset_id` bigint unsigned NOT NULL COMMENT '素材资产ID（mall-file，逻辑引用；归属校验在文件服务）',
  `kind` varchar(16) NOT NULL COMMENT '素材类型:SCREENSHOT/BODY',
  `display_order` int NOT NULL DEFAULT '0' COMMENT '排序',
  `alt_text` varchar(100) DEFAULT NULL COMMENT '无障碍替代文本',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_revision_media` (`revision_id`,`asset_id`,`kind`),
  KEY `idx_revision_media_order` (`revision_id`,`kind`,`display_order`),
  CONSTRAINT `chk_revision_media_kind` CHECK (`kind` IN ('SCREENSHOT','BODY'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='快照素材关系（图标/截图/正文媒体）';
