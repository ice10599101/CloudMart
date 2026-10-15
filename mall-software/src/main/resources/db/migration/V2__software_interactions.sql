-- =============================================================================
-- V2__software_interactions.sql — 评论/点赞/收藏、失效反馈、软件举报、
--                                  日获取去重事实（方案 6.8/6.9/6.12）
-- 唯一关系表物理删除；核心聚合软删除；唯一约束是计数正确性的最终防线。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 评论（6.8）：两层展示（根评论+楼中楼）；parent_id 始终指向根评论
-- -----------------------------------------------------------------------------
CREATE TABLE `software_comment` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `software_id` bigint unsigned NOT NULL COMMENT '所属软件（宿主关系，独立于社区帖子评论）',
  `user_id` bigint unsigned NOT NULL COMMENT '评论人（前台会员ID）',
  `parent_id` bigint unsigned DEFAULT NULL COMMENT '根评论ID（NULL=根楼；非 NULL 恒指向根楼，不做无限嵌套）',
  `reply_to_comment_id` bigint unsigned DEFAULT NULL COMMENT '精确回复目标评论ID（同软件同根楼内）',
  `reply_to_user_id` bigint unsigned DEFAULT NULL COMMENT '被回复人（服务端从目标评论推导，不接受客户端传入）',
  `content` varchar(1000) NOT NULL COMMENT '评论正文（纯文本+表情，1~1000 字）',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态:PENDING/VISIBLE/HIDDEN/DELETED（删除保留楼层占位；隐藏不暴露原内容）',
  `like_count` int unsigned NOT NULL DEFAULT '0' COMMENT '点赞数（唯一关系计数）',
  `pinned` tinyint(1) NOT NULL DEFAULT '0' COMMENT '作者置顶（P1：每软件最多一个可见根评论，主行锁保护）',
  `review_reason` varchar(500) DEFAULT NULL COMMENT '审核说明',
  `deleted_at` datetime(3) DEFAULT NULL COMMENT '软删除时间（保留最少关系数据供占位，不再回传原 content）',
  `lock_version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_comment_thread` (`software_id`,`parent_id`,`status`,`created_at`,`id`),
  KEY `idx_comment_user` (`user_id`,`created_at`,`id`),
  CONSTRAINT `chk_comment_status` CHECK (`status` IN ('PENDING','VISIBLE','HIDDEN','DELETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='软件评论（独立宿主，复用社区交互模式但不共用表）';

-- 评论点赞（唯一关系：幂等 PUT/DELETE；无 updated_at）
CREATE TABLE `software_comment_like` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `comment_id` bigint unsigned NOT NULL COMMENT '评论ID',
  `user_id` bigint unsigned NOT NULL COMMENT '点赞人',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_comment_like` (`comment_id`,`user_id`),
  KEY `idx_comment_like_user` (`user_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评论点赞关系（物理删除）';

-- 收藏（唯一关系：每用户每软件一条，幂等取消；无 updated_at）
CREATE TABLE `software_favorite` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `software_id` bigint unsigned NOT NULL COMMENT '软件ID',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_favorite_user_software` (`user_id`,`software_id`),
  KEY `idx_favorite_software` (`software_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='软件收藏关系（物理删除）';

-- -----------------------------------------------------------------------------
-- 失效反馈（6.9）：同指纹同人去重；3 人/24h 标 SUSPECTED；管理员判 INVALID/DISABLED
-- -----------------------------------------------------------------------------
CREATE TABLE `software_link_feedback` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `software_id` bigint unsigned NOT NULL COMMENT '软件ID',
  `source_key` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '稳定来源键（修复通过新快照上线后按 key 关联闭环）',
  `source_id_snapshot` bigint unsigned NOT NULL COMMENT '反馈时来源行ID快照（来源可能已随快照更替）',
  `source_fingerprint` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '反馈时来源内容指纹（去重键之一；修复后 RESOLVED 保留 original）',
  `reporter_id` bigint unsigned NOT NULL COMMENT '反馈人',
  `reason_code` varchar(32) NOT NULL COMMENT '反馈类型:LINK_INVALID/CODE_WRONG/FILE_MISMATCH/SUSPICIOUS/OTHER',
  `description` varchar(500) DEFAULT NULL COMMENT '补充说明',
  `status` varchar(16) NOT NULL DEFAULT 'OPEN' COMMENT '状态:OPEN/VERIFYING/RESOLVED/REJECTED（用户反馈不是最终检测结果）',
  `handler_id` bigint unsigned DEFAULT NULL COMMENT '处理管理员ID',
  `handle_note` varchar(500) DEFAULT NULL COMMENT '处理备注',
  `resolved_revision_id` bigint unsigned DEFAULT NULL COMMENT '修复上线快照ID（RESOLVED 时记录）',
  `handled_at` datetime(3) DEFAULT NULL COMMENT '处理时间',
  `lock_version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_feedback_reporter_fingerprint` (`reporter_id`,`software_id`,`source_key`,`source_fingerprint`),
  KEY `idx_feedback_status` (`status`,`created_at`,`id`),
  KEY `idx_feedback_source` (`software_id`,`source_key`,`source_fingerprint`,`created_at`),
  CONSTRAINT `chk_feedback_status` CHECK (`status` IN ('OPEN','VERIFYING','RESOLVED','REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='链接失效反馈（按指纹合并，不等于自动检测结果）';

-- -----------------------------------------------------------------------------
-- 日获取去重事实（6.9）：同用户同来源同上海自然日最多计一次；复合主键去重
-- 不保存完整链接、IP 或密码；首次 INSERT 成功才增累计计数
-- -----------------------------------------------------------------------------
CREATE TABLE `software_link_access_daily` (
  `software_id` bigint unsigned NOT NULL COMMENT '软件ID',
  `source_key` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '稳定来源键（改链沿用，避免复制快照重复计数）',
  `user_id` bigint unsigned NOT NULL COMMENT '获取人（发布者本人/管理员预览不计）',
  `access_date` date NOT NULL COMMENT '获取日期（Asia/Shanghai 切日）',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '首次获取时间',
  PRIMARY KEY (`software_id`,`source_key`,`user_id`,`access_date`),
  KEY `idx_access_daily_date` (`access_date`,`software_id`,`user_id`),
  KEY `idx_access_daily_user` (`user_id`,`access_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='日获取链接去重事实（业务热度唯一口径，非外部下载成功）';

-- -----------------------------------------------------------------------------
-- 软件举报（6.12）：落 software_report，软件服务内事务处置；不写社区 reports
-- -----------------------------------------------------------------------------
CREATE TABLE `software_report` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `reporter_id` bigint unsigned NOT NULL COMMENT '举报人',
  `software_id` bigint unsigned NOT NULL COMMENT '被举报软件',
  `target_type` varchar(32) NOT NULL COMMENT '目标类型:SOFTWARE/SOFTWARE_COMMENT',
  `target_id` bigint unsigned NOT NULL COMMENT '目标ID（须属于该软件，服务端校验）',
  `reason` varchar(50) NOT NULL COMMENT '举报理由码（侵权/恶意内容/违规评论等）',
  `description` varchar(500) DEFAULT NULL COMMENT '补充说明',
  `evidence_asset_ids` json DEFAULT NULL COMMENT '证据素材ID数组（仅本人文件资产，bizType=SOFTWARE_REPORT）',
  `status` varchar(16) NOT NULL DEFAULT 'OPEN' COMMENT '状态:OPEN/VERIFYING/REJECTED/RESOLVED',
  `handler_id` bigint unsigned DEFAULT NULL COMMENT '处理管理员ID',
  `handle_note` varchar(500) DEFAULT NULL COMMENT '处理备注',
  `handled_at` datetime(3) DEFAULT NULL COMMENT '处理时间',
  `lock_version` int unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_report_status` (`status`,`created_at`,`id`),
  KEY `idx_report_target` (`target_type`,`target_id`),
  KEY `idx_report_reporter` (`reporter_id`,`created_at`,`id`),
  CONSTRAINT `chk_report_status` CHECK (`status` IN ('OPEN','VERIFYING','REJECTED','RESOLVED')),
  CONSTRAINT `chk_report_target_type` CHECK (`target_type` IN ('SOFTWARE','SOFTWARE_COMMENT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='软件举报（处置动作与软件封禁/评论隐藏同库事务）';
