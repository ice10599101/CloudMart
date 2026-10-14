-- C26 clean development/test baseline. Recreated EMPTY databases only.
-- Materialized from verified MySQL schema; never repair an old history to adopt this file.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE `starlight_daily_out_quota` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint unsigned NOT NULL COMMENT '转出用户 ID',
  `business_date` date NOT NULL COMMENT '业务日（Asia/Shanghai）',
  `used_amount` int NOT NULL DEFAULT '0' COMMENT '当日已转出累计',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_date` (`user_id`,`business_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='星光日转出额度（R06 原子占用）';

CREATE TABLE `starlight_transfer` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键（spend/earn 流水 refId）',
  `from_user_id` bigint unsigned NOT NULL COMMENT '转出用户 ID',
  `to_user_id` bigint unsigned NOT NULL COMMENT '转入用户 ID',
  `amount` int NOT NULL COMMENT '转赠星光数量',
  `request_key` varchar(64) DEFAULT NULL COMMENT '请求键（重放幂等；同键异载荷 409）',
  `status` varchar(20) NOT NULL DEFAULT 'SETTLED' COMMENT 'SETTLED=等额对转完成',
  `business_date` date DEFAULT NULL COMMENT '业务日（Asia/Shanghai，日额度归属）',
  `credited_amount` int DEFAULT NULL COMMENT '接收方实际入账（守恒要求=amount）',
  `sender_ledger_id` bigint unsigned DEFAULT NULL COMMENT '转出流水 ID',
  `receiver_ledger_id` bigint unsigned DEFAULT NULL COMMENT '转入流水 ID',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `message` varchar(100) DEFAULT NULL COMMENT '附言（可空）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_from_request` (`from_user_id`,`request_key`),
  KEY `idx_from_created` (`from_user_id`,`created_at`),
  KEY `idx_to_created` (`to_user_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='星光转赠流水（§6）';

CREATE TABLE `time_capsule` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `user_id` bigint unsigned NOT NULL COMMENT '所属用户ID',
  `title` varchar(100) NOT NULL COMMENT '胶囊标题(≤100字)',
  `content` text NOT NULL COMMENT '胶囊内容(≤5000字, 未开启不返回)',
  `media_urls` json DEFAULT NULL COMMENT '封存媒体URL列表(OSS Key)',
  `open_at` datetime NOT NULL COMMENT '预定开启时间(UTC, 到期判定唯一依据)',
  `open_at_timezone` varchar(32) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '创建时用户IANA时区(仅回溯展示/审计, 不参与到期判定)',
  `reschedule_count` int NOT NULL DEFAULT '0' COMMENT '已改期次数（T22：上限 3 次，超出拒绝）',
  `reschedule_limit` int NOT NULL DEFAULT '3' COMMENT '改期次数上限（T22：服务端权威，客户端不可指定）',
  `opened_at` datetime DEFAULT NULL COMMENT '实际开启时间(UTC)',
  `status` enum('SEALED','AVAILABLE','OPENED','CANCELLED') NOT NULL DEFAULT 'SEALED' COMMENT '状态: 封印中/已到期待开启/已开启/已取消',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  `wall_status` tinyint NOT NULL DEFAULT '0' COMMENT '公共胶囊墙：0 未申请 / 1 待审核 / 2 已上墙 / 3 已拒绝',
  `wall_applied_at` datetime DEFAULT NULL COMMENT '申请上墙时间',
  `wall_decided_at` datetime DEFAULT NULL COMMENT '审核决定时间',
  `wall_reason` varchar(200) DEFAULT NULL COMMENT '公共胶囊墙驳回/下架理由（作者可见）',
  `wall_actor_id` bigint unsigned DEFAULT NULL COMMENT '最后一次墙裁决操作者ID（管理员/作者本人）',
  PRIMARY KEY (`id`),
  KEY `idx_capsule_user` (`user_id`,`id`),
  KEY `idx_capsule_open` (`open_at`,`status`),
  KEY `idx_wall_status` (`wall_status`,`wall_decided_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='时间胶囊(表⑦)';

CREATE TABLE `wish` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `user_id` bigint unsigned NOT NULL COMMENT '作者用户ID',
  `title` varchar(120) NOT NULL COMMENT '心愿标题',
  `description` text NOT NULL COMMENT '心愿描述',
  `category_id` bigint unsigned NOT NULL COMMENT '分类ID',
  `visibility` enum('PUBLIC','PRIVATE','TREE_HOLE') NOT NULL DEFAULT 'PUBLIC' COMMENT '可见性: PUBLIC公开/PRIVATE私密/TREE_HOLE树洞',
  `enable_ai_reply` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否启用AI回复: TREE_HOLE=true, 其他=false',
  `audit_strategy` enum('LAZY','STRICT') NOT NULL DEFAULT 'LAZY' COMMENT '审核策略: LAZY先发后审/STRICT实时审核高危词',
  `trigger_env_emo` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否触发生命树情绪环境联动: 仅TREE_HOLE=true',
  `status` enum('DRAFT','ACTIVE','OVERDUE','FULFILLING','FULFILLED','ARCHIVED') NOT NULL DEFAULT 'ACTIVE' COMMENT '心愿状态机',
  `fruit_type` enum('GLOW','RESONANCE','BLOOM','SPARK') NOT NULL DEFAULT 'GLOW' COMMENT '果实类型: GLOW微光/RESONANCE共鸣/BLOOM绽放/SPARK星火',
  `expected_at` datetime DEFAULT NULL COMMENT '预计完成时间(UTC绝对时间, 跨时区比对用)',
  `version` bigint NOT NULL DEFAULT '0' COMMENT '乐观锁版本(B08:编辑条件更新,计数/审核状态不由普通编辑覆盖)',
  `fulfilled_at` datetime DEFAULT NULL COMMENT '实际还愿时间(UTC)',
  `geohash` char(7) DEFAULT NULL COMMENT 'LBS地理哈希(7位约153m, 仅PUBLIC, 模糊化后存储)',
  `tree_theta` decimal(9,7) unsigned DEFAULT NULL COMMENT '世界树球面经度角theta[0,2π)弧度(PUBLIC心愿发布时固化,果实挂载方位)',
  `tree_phi` decimal(9,7) unsigned DEFAULT NULL COMMENT '世界树球面纬度角phi(0,π]弧度(与tree_theta共同定位果实,北极0南极π)',
  `media_urls` json DEFAULT NULL COMMENT '媒体资源URL列表(OSS Key)',
  `tags` json DEFAULT NULL COMMENT '标签列表(最多5个)',
  `same_wish_count` int NOT NULL DEFAULT '0' COMMENT '同求计数(冗余, 避免count聚合)',
  `light_count` int NOT NULL DEFAULT '0' COMMENT '累计点亮数(含取消后回滚)',
  `bless_count` int NOT NULL DEFAULT '0' COMMENT '累计祝福数',
  `anon_star_count` int NOT NULL DEFAULT '0' COMMENT '累计匿名星光数(含取消后回滚)',
  `support_count` int GENERATED ALWAYS AS (((`light_count` + `same_wish_count`) + `bless_count`)) STORED COMMENT '总互动数(生成列: 点亮+同求+祝福)',
  `audit_status` enum('PENDING','APPROVED','REJECTED','AUTO_HIDDEN') NOT NULL DEFAULT 'PENDING' COMMENT '审核状态',
  `reject_reason` varchar(500) DEFAULT NULL COMMENT '驳回原因(B11:REJECTED 必填;恢复上架时清空)',
  `archived_from_status` varchar(16) DEFAULT NULL COMMENT '归档前状态(N03:unarchive 恢复依据)',
  `is_visible` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否对用户可见(与audit_status解耦, 便于先发后审)',
  `is_top` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否管理端置顶(置顶心愿在广场优先展示)',
  `deleted_at` datetime DEFAULT NULL COMMENT '软删除时间(核心业务数据物理删除禁止)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  `expected_timezone` varchar(64) DEFAULT NULL COMMENT '预计完成时区(IANA,B09/N03)',
  `linked_product_id` bigint DEFAULT NULL COMMENT '关联商品ID（心愿关联商品闭环，详情页去购买）',
  PRIMARY KEY (`id`),
  KEY `idx_wish_user` (`user_id`),
  KEY `idx_wish_category` (`category_id`),
  KEY `idx_wish_status` (`status`),
  KEY `idx_wish_expected` (`expected_at`),
  KEY `idx_wish_geohash` (`geohash`),
  KEY `idx_wish_support` (`support_count`),
  KEY `idx_wish_audit` (`audit_status`),
  KEY `idx_wish_visibility` (`visibility`),
  KEY `idx_wish_fruit_type` (`fruit_type`),
  KEY `idx_wish_created` (`created_at`),
  KEY `idx_wish_light` (`light_count`),
  KEY `idx_wish_same_wish` (`same_wish_count`),
  KEY `idx_wish_bless` (`bless_count`),
  KEY `idx_wish_tree_theta` (`tree_theta`),
  KEY `idx_lb_light` (`is_visible`,`audit_status`,`light_count`),
  KEY `idx_lb_bless` (`is_visible`,`audit_status`,`bless_count`),
  KEY `idx_linked_product` (`linked_product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='心愿主表';

CREATE TABLE `wish_activity` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `type` enum('WORLD_EVENT','FESTIVAL','CITY','WISH_PARTNER') NOT NULL COMMENT '活动类型',
  `title` varchar(120) NOT NULL COMMENT '活动标题',
  `description` varchar(1000) DEFAULT NULL COMMENT '活动描述/规则说明',
  `cover_image` varchar(500) DEFAULT NULL COMMENT '封面图(节日氛围装饰)',
  `condition_json` json DEFAULT NULL COMMENT '触发条件 JSON(类型/阈值)',
  `reward_json` json DEFAULT NULL COMMENT '奖励配置 JSON(星光/徽章)',
  `city_code` char(4) DEFAULT NULL COMMENT '城市代理(geohash4,城市活动专用)',
  `status` enum('DRAFT','ACTIVE','ENDED','ARCHIVED') NOT NULL DEFAULT 'DRAFT' COMMENT '状态机: 筹备/进行中/结束/归档',
  `valid_from` datetime DEFAULT NULL COMMENT '展示开始(UTC,NULL=不限)',
  `valid_to` datetime DEFAULT NULL COMMENT '展示结束(UTC,NULL=不限;到期入口消失但详情仍可访问)',
  `progress_counter` bigint NOT NULL DEFAULT '0' COMMENT '进度计数(Redis INCR 周期回写镜像)',
  `created_by` bigint unsigned NOT NULL COMMENT '创建管理员',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  KEY `idx_activity_status` (`status`,`type`,`valid_from`),
  KEY `idx_activity_city` (`city_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='社区活动配置(配置表化,状态机)';

CREATE TABLE `wish_activity_participant` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `activity_id` bigint unsigned NOT NULL COMMENT '活动ID',
  `user_id` bigint unsigned NOT NULL COMMENT '参与用户',
  `role` enum('LEADER','MEMBER') NOT NULL DEFAULT 'MEMBER' COMMENT '角色(合伙人招募作者=LEADER)',
  `status` enum('PENDING','APPROVED','REJECTED','JOINED') NOT NULL DEFAULT 'JOINED' COMMENT '状态(合伙人申请 PENDING;普通参与 JOINED)',
  `wish_id` bigint unsigned DEFAULT NULL COMMENT '协作心愿ID(合伙人申请提交)',
  `skills` json DEFAULT NULL COMMENT '技能标签 JSON(如 ["design","video"])',
  `match_score` int NOT NULL DEFAULT '0' COMMENT '技能匹配度(0-100,与招募需求交集占比)',
  `applied_at` datetime DEFAULT NULL COMMENT '申请时间(UTC)',
  `reviewed_at` datetime DEFAULT NULL COMMENT '审批时间(UTC)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_activity_user` (`activity_id`,`user_id`),
  KEY `idx_participant_activity` (`activity_id`,`status`),
  KEY `idx_participant_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='活动参与/合伙人申请记录';

CREATE TABLE `wish_activity_reward_log` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `activity_id` bigint unsigned NOT NULL COMMENT '活动ID',
  `user_id` bigint unsigned NOT NULL COMMENT '获奖励用户',
  `reward_type` enum('STARLIGHT','BADGE') NOT NULL COMMENT '奖励类型',
  `amount` int NOT NULL DEFAULT '0' COMMENT '数量(星光数;徽章恒 1)',
  `ref_id` bigint unsigned DEFAULT NULL COMMENT '关联 ID(徽章 ID)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发放时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reward_unique` (`activity_id`,`user_id`,`reward_type`),
  KEY `idx_reward_activity` (`activity_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='活动奖励发放日志(幂等+审计)';

CREATE TABLE `wish_ai_config` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `config_key` varchar(64) NOT NULL COMMENT '配置键',
  `config_value` varchar(500) NOT NULL COMMENT '配置值(字符串,业务层解析)',
  `description` varchar(200) DEFAULT NULL COMMENT '配置说明',
  `updated_by` bigint unsigned DEFAULT NULL COMMENT '最后修改人(管理后台用户ID)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI/提醒策略全局配置(管理后台实时生效)';

CREATE TABLE `wish_ai_conversation` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `session_id` varchar(64) NOT NULL COMMENT '会话ID(树洞: tree-hole-{wishId}-{userId})',
  `scene` enum('GOAL_BREAKDOWN','TREE_HOLE','ANNUAL_REPORT') NOT NULL COMMENT '场景: 目标拆解/树洞/年度报告',
  `role` enum('USER','ASSISTANT') NOT NULL COMMENT '角色: 用户消息/AI回复',
  `content` text NOT NULL COMMENT '消息内容(发送侧已脱敏,存储原始对话)',
  `sentiment_score` tinyint DEFAULT NULL COMMENT '情感分数(-100~100,仅TREE_HOLE场景ASSISTANT记录)',
  `resources` json DEFAULT NULL COMMENT '推荐资源JSON(仅TREE_HOLE场景ASSISTANT记录)',
  `deleted_at` datetime DEFAULT NULL COMMENT '软删除时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  KEY `idx_ai_conv_user` (`user_id`,`scene`,`created_at`),
  KEY `idx_ai_conv_session` (`session_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI对话历史(树洞/AI助手/年度报告)';

CREATE TABLE `wish_ai_goal` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `wish_id` bigint unsigned DEFAULT NULL COMMENT '关联心愿ID(可空,允许无心愿的自由目标拆解)',
  `title` varchar(100) NOT NULL COMMENT '步骤标题',
  `description` text NOT NULL COMMENT '步骤描述',
  `estimated_days` int NOT NULL DEFAULT '7' COMMENT '预计完成天数',
  `priority` tinyint NOT NULL DEFAULT '3' COMMENT '优先级(1-5, 1最高)',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序(N04)',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁(N04)',
  `status` enum('PENDING','IN_PROGRESS','COMPLETED','CANCELLED') NOT NULL DEFAULT 'PENDING' COMMENT '状态: 待开始/进行中/已完成/已取消',
  `ai_session_id` varchar(64) DEFAULT NULL COMMENT 'AI会话ID(关联wish_ai_conversation.session_id)',
  `started_at` datetime DEFAULT NULL COMMENT '开始时间(UTC, IN_PROGRESS时写入)',
  `completed_at` datetime DEFAULT NULL COMMENT '完成时间(UTC, COMPLETED时写入)',
  `deleted_at` datetime DEFAULT NULL COMMENT '软删除时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  KEY `idx_ai_goal_user` (`user_id`,`status`,`created_at`),
  KEY `idx_ai_goal_wish` (`wish_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI拆解目标(用户勾选的拆解步骤)';

CREATE TABLE `wish_ai_prompt` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `scene` enum('GOAL_BREAKDOWN','TREE_HOLE','ANNUAL_REPORT','EXPECTED_GUIDE') NOT NULL COMMENT 'AI场景: 目标拆解/树洞/年度报告/预期管理引导',
  `version` int NOT NULL COMMENT '版本号(scene内递增)',
  `name` varchar(100) NOT NULL COMMENT '模板名称(管理后台展示)',
  `content` text NOT NULL COMMENT 'Prompt正文(支持{placeholder}变量)',
  `ab_group` enum('ALL','A','B') NOT NULL DEFAULT 'ALL' COMMENT 'A/B分组: ALL=不分流',
  `traffic_percent` tinyint NOT NULL DEFAULT '100' COMMENT '流量百分比(1-100, 同scene的ACTIVE记录按此加权分流)',
  `status` enum('DRAFT','ACTIVE','ARCHIVED') NOT NULL DEFAULT 'DRAFT' COMMENT '状态: 草稿/生效中/已归档',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注(版本变更说明)',
  `created_by` bigint unsigned DEFAULT NULL COMMENT '创建人(管理后台用户ID)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_prompt_scene_version` (`scene`,`version`),
  KEY `idx_prompt_scene_status` (`scene`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI Prompt模板版本管理(A/B分流)';

CREATE TABLE `wish_ai_review` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `conversation_id` bigint unsigned NOT NULL COMMENT '被抽检的 AI 回复ID(wish_ai_conversation)',
  `scene` varchar(32) NOT NULL COMMENT 'AI场景(GOAL_BREAKDOWN/TREE_HOLE/ANNUAL_REPORT/EXPECTED_GUIDE)',
  `content` text NOT NULL COMMENT '抽检时回复内容快照(后改不影响评分对象)',
  `result` enum('PASS','FAIL') DEFAULT NULL COMMENT '人工评分(NULL=未评)',
  `issue_type` varchar(32) DEFAULT NULL COMMENT '问题分类(MECHANICAL机械感/ERROR错误信息/IRRELEVANT不相关,FAIL时填写)',
  `note` varchar(200) DEFAULT NULL COMMENT '评语',
  `reviewed_by` bigint unsigned DEFAULT NULL COMMENT '评分人(管理后台用户ID)',
  `reviewed_at` datetime DEFAULT NULL COMMENT '评分时间(UTC)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_review_conversation` (`conversation_id`),
  KEY `idx_review_scene` (`scene`,`result`),
  KEY `idx_review_reviewed` (`reviewed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI回复人工抽检(合格率/问题分类)';

CREATE TABLE `wish_appeal` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花)',
  `decision_id` bigint unsigned NOT NULL COMMENT '申诉针对的治理决定',
  `appellant_id` bigint unsigned NOT NULL COMMENT '申诉人(被处理作者)',
  `statement` varchar(1000) NOT NULL COMMENT '申诉陈述(1-1000)',
  `evidence_refs` varchar(1000) DEFAULT NULL COMMENT '证据附件(JSON,≤3)',
  `status` enum('PENDING','ACCEPTED','REJECTED') NOT NULL DEFAULT 'PENDING',
  `reviewer_id` bigint unsigned DEFAULT NULL COMMENT '复核人(不得为原决定处理人)',
  `result_reason` varchar(500) DEFAULT NULL COMMENT '复核结论说明',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `resolved_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_appeal_decision_appellant` (`decision_id`,`appellant_id`),
  KEY `idx_appeal_appellant` (`appellant_id`,`created_at`),
  KEY `idx_appeal_status` (`status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='治理申诉(同决定同人一条;7日内)';

CREATE TABLE `wish_badge` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `code` varchar(30) NOT NULL COMMENT '徽章编码(唯一)',
  `name` varchar(60) NOT NULL COMMENT '徽章名称',
  `icon` varchar(255) NOT NULL COMMENT '徽章图标URL',
  `rarity` varchar(16) NOT NULL DEFAULT 'COMMON' COMMENT '稀有度: COMMON普通/RARE稀有/EPIC史诗/LEGENDARY传说',
  `is_active` tinyint(1) NOT NULL DEFAULT '1' COMMENT '上架状态(1=上架,0=下架不判定不展示)',
  `condition` json DEFAULT NULL COMMENT '触发条件声明式定义(JSON)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_badge_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='心愿徽章定义';

CREATE TABLE `wish_bgm_song` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花ID)',
  `title` varchar(128) NOT NULL COMMENT '歌曲标题(管理端展示与四端播放器曲名)',
  `url` varchar(512) NOT NULL COMMENT '音频地址(mall-file 上传 OSS 后的 URL,http(s)直链)',
  `file_size` bigint unsigned NOT NULL DEFAULT '0' COMMENT '文件大小(字节,mall-file 上传返回;展示用)',
  `sort` int NOT NULL DEFAULT '0' COMMENT '播放顺序(升序;同序按 id 稳定排序)',
  `is_active` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否在播放列表(1=播放;多首激活=顺序循环播放)',
  `uploaded_by` bigint unsigned NOT NULL COMMENT '上传管理员用户ID(审计)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  KEY `idx_bgm_playlist` (`is_active`,`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='心愿宇宙背景音乐曲库(Sprint 2.3,管理端上传歌曲+勾选播放列表)';

CREATE TABLE `wish_brand` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `name` varchar(120) NOT NULL COMMENT '品牌名称',
  `logo` varchar(500) DEFAULT NULL COMMENT '品牌 Logo URL',
  `description` varchar(500) DEFAULT NULL COMMENT '品牌介绍',
  `category_id` bigint unsigned DEFAULT NULL COMMENT '认领心愿分类',
  `status` enum('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING' COMMENT '入驻审核状态',
  `created_by` bigint unsigned NOT NULL COMMENT '入驻提交人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  KEY `idx_brand_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='品牌(入驻审核)';

CREATE TABLE `wish_brand_pool` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `brand_id` bigint unsigned NOT NULL COMMENT '品牌ID',
  `category_id` bigint unsigned NOT NULL COMMENT '认领心愿分类(关联 wish_category)',
  `name` varchar(120) NOT NULL COMMENT '许愿池名称',
  `target_count` int NOT NULL COMMENT '目标人数',
  `current_count` int NOT NULL DEFAULT '0' COMMENT '当前参与人数',
  `reward_json` json DEFAULT NULL COMMENT '达成奖励 JSON(如 {"starlight":50})',
  `end_at` datetime DEFAULT NULL COMMENT '结束时间(UTC)',
  `status` enum('ACTIVE','ENDED') NOT NULL DEFAULT 'ACTIVE' COMMENT '池状态',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  KEY `idx_pool_brand` (`brand_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='品牌许愿池';

CREATE TABLE `wish_brand_pool_member` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `pool_id` bigint unsigned NOT NULL COMMENT '许愿池ID',
  `user_id` bigint unsigned NOT NULL COMMENT '加入用户',
  `joined_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pool_member` (`pool_id`,`user_id`),
  KEY `idx_pool_member_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='品牌许愿池成员';

CREATE TABLE `wish_brand_reward_log` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `pool_id` bigint unsigned NOT NULL COMMENT '许愿池 ID',
  `user_id` bigint unsigned NOT NULL COMMENT '获得奖励的用户 ID',
  `reward_type` varchar(20) NOT NULL DEFAULT 'STARLIGHT' COMMENT '奖励类型',
  `reward_amount` int NOT NULL DEFAULT '0' COMMENT '奖励数量',
  `coupon_code` varchar(64) DEFAULT NULL COMMENT '券码（如适用）',
  `granted_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发放时间',
  PRIMARY KEY (`id`),
  KEY `idx_reward_user` (`user_id`,`granted_at`),
  KEY `idx_reward_pool` (`pool_id`,`granted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='品牌奖励发放日志（合规 34.4 数据共享边界）';

CREATE TABLE `wish_category` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `code` varchar(30) NOT NULL COMMENT '分类编码(唯一): CAREER/GROWTH/RELATION/TRAVEL/WEALTH/HOBBY',
  `name` varchar(60) NOT NULL COMMENT '分类名称',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序(升序)',
  `icon` varchar(255) DEFAULT NULL COMMENT '分类图标URL',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_category_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='心愿分类字典';

CREATE TABLE `wish_checkin` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `wish_id` bigint unsigned NOT NULL COMMENT '心愿ID',
  `user_id` bigint unsigned NOT NULL COMMENT '打卡用户ID',
  `checkin_date` date NOT NULL COMMENT '打卡日期(用户时区, 按日去重)',
  `content` varchar(500) DEFAULT NULL COMMENT '打卡内容(可空)',
  `mood` varchar(20) DEFAULT NULL COMMENT '打卡心情（HAPPY/CALM/EXCITED/TIRED）',
  `is_makeup` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否补卡(离线缓存超7天上传)',
  `starlight_granted` tinyint(1) NOT NULL DEFAULT '1' COMMENT '本次打卡是否已发放星光(补卡=false, 避免重复发放)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_checkin_daily` (`wish_id`,`user_id`,`checkin_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='心愿打卡记录';

CREATE TABLE `wish_collection` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `user_id` bigint unsigned NOT NULL COMMENT '收藏者用户ID',
  `wish_id` bigint unsigned NOT NULL COMMENT '被收藏心愿ID',
  `collected_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间(UTC)',
  `deleted_at` datetime DEFAULT NULL COMMENT '取消收藏时间(UTC,软删)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_collection_user_wish` (`user_id`,`wish_id`),
  KEY `idx_collection_user` (`user_id`,`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='心愿收藏(用户级)';

CREATE TABLE `wish_comment` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `wish_id` bigint unsigned NOT NULL COMMENT '心愿ID',
  `user_id` bigint unsigned NOT NULL COMMENT '评论用户ID',
  `parent_id` bigint unsigned DEFAULT NULL COMMENT '父评论ID(顶级评论为NULL, 仅支持二级回复)',
  `reply_to_user_id` bigint unsigned DEFAULT NULL COMMENT '被回复用户ID(回复时冗余存储)',
  `content` varchar(500) NOT NULL COMMENT '评论内容(已XSS转义+敏感词过滤后存储)',
  `like_count` int NOT NULL DEFAULT '0' COMMENT '点赞数(预留,Sprint 1.2 不启用点赞API)',
  `status` enum('VISIBLE','HIDDEN') NOT NULL DEFAULT 'VISIBLE' COMMENT '状态: VISIBLE可见/HIDDEN已下架(含敏感词自动下架与管理员手动下架)',
  `sensitive_hit` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否命中敏感词(仅标记不阻断,先发后审,管理后台筛选用)',
  `deleted_at` datetime DEFAULT NULL COMMENT '软删除时间(用户删除自己的评论)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  KEY `idx_comment_wish` (`wish_id`,`status`,`created_at`),
  KEY `idx_comment_parent` (`parent_id`),
  KEY `idx_comment_user` (`user_id`,`created_at`),
  KEY `idx_comment_sensitive` (`sensitive_hit`,`status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='心愿评论(二级回复)';

CREATE TABLE `wish_consent` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `consent_type` enum('PRIVACY_POLICY','AI_DATA_PROCESSING','BRAND_DATA_SHARE') NOT NULL COMMENT '同意类型: 隐私政策/AI数据处理/品牌数据共享',
  `version` varchar(20) NOT NULL COMMENT '协议版本号(每次协议更新递增)',
  `consent_text_hash` varchar(64) NOT NULL COMMENT '同意时协议文本SHA-256哈希(防篡改)',
  `action` enum('GRANT','WITHDRAW') NOT NULL COMMENT '动作: GRANT同意/WITHDRAW撤回',
  `ip` varchar(45) DEFAULT NULL COMMENT '操作IP(IPv4/IPv6)',
  `user_agent` varchar(255) DEFAULT NULL COMMENT '操作User-Agent',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_consent_unique` (`user_id`,`consent_type`,`version`,`action`),
  KEY `idx_consent_user` (`user_id`,`consent_type`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户同意记录(合规留痕)';

CREATE TABLE `wish_content_flow_log` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `wish_id` bigint unsigned NOT NULL COMMENT '心愿ID',
  `fulfillment_id` bigint unsigned NOT NULL COMMENT '还愿记录ID',
  `post_id` bigint unsigned DEFAULT NULL COMMENT 'community 帖子ID(成功后回填)',
  `status` enum('SUCCESS','FAILED','HIDDEN') NOT NULL DEFAULT 'FAILED' COMMENT '流转状态',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '已重试次数',
  `error_msg` varchar(500) DEFAULT NULL COMMENT '最近一次失败原因',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_flow_fulfillment` (`fulfillment_id`),
  KEY `idx_flow_status` (`status`,`created_at`),
  KEY `idx_flow_wish` (`wish_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='还愿内容流转日志(wish→community)';

CREATE TABLE `wish_daily_signin` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `signin_date` date NOT NULL COMMENT '签到日期(UTC, 按日去重)',
  `starlight_granted` tinyint(1) NOT NULL DEFAULT '1' COMMENT '本次签到是否已发放星光(防重复发放)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_signin_daily` (`user_id`,`signin_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户每日签到记录';

CREATE TABLE `wish_data_export` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `user_id` bigint unsigned NOT NULL COMMENT '申请用户',
  `status` enum('PENDING','PROCESSING','SUCCESS','FAILED') NOT NULL DEFAULT 'PENDING' COMMENT '任务状态',
  `download_url` varchar(500) DEFAULT NULL COMMENT '下载链接(SUCCESS 后回填)',
  `content_enc` mediumtext COMMENT '加密导出内容(W04:AES-GCM,AAD=EXPORT:{taskId};明文不落库)',
  `content_sha256` char(64) DEFAULT NULL COMMENT '密文SHA-256(下载时完整性校验)',
  `lease_owner` varchar(64) DEFAULT NULL COMMENT '租约持有者(实例+线程;接管时轮换)',
  `lease_until` datetime(3) DEFAULT NULL COMMENT '租约到期(过期可被其他实例接管)',
  `content` longtext COMMENT '导出内容 JSON（SUCCESS 后写入，7 天过期清理）',
  `expires_at` datetime DEFAULT NULL COMMENT '下载链接过期时间(UTC)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  KEY `idx_export_user` (`user_id`,`created_at`),
  KEY `idx_export_recovery` (`status`,`lease_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='数据导出任务(合规34.2)';

CREATE TABLE `wish_draft` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花)',
  `user_id` bigint unsigned NOT NULL COMMENT '作者',
  `client_draft_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '客户端草稿标识(断网恢复不重复建)',
  `title` varchar(120) DEFAULT NULL COMMENT '标题(可不完整)',
  `description` mediumtext COMMENT '描述(可不完整)',
  `category_id` bigint unsigned DEFAULT NULL COMMENT '分类',
  `media_urls` varchar(2000) DEFAULT NULL COMMENT '媒体(JSON)',
  `tags` varchar(500) DEFAULT NULL COMMENT '标签(JSON)',
  `expected_at` datetime DEFAULT NULL COMMENT '预计完成时间',
  `expected_timezone` varchar(64) DEFAULT NULL COMMENT '业务时区',
  `visibility` varchar(16) DEFAULT NULL COMMENT '可见性',
  `published_wish_id` bigint unsigned DEFAULT NULL COMMENT '发布后的心愿ID(发布幂等)',
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT '状态:DRAFT/PUBLISHING/PUBLISHED(W01:发布 CAS 状态机)',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁',
  `deleted_at` datetime(3) DEFAULT NULL COMMENT '软删',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_draft_client` (`user_id`,`client_draft_id`),
  UNIQUE KEY `uk_draft_published_wish` (`published_wish_id`),
  KEY `idx_draft_user` (`user_id`,`deleted_at`,`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='心愿草稿(N03:每人最多20份,不进公共feed)';

CREATE TABLE `wish_drift_bottle` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `thrower_user_id` bigint unsigned NOT NULL COMMENT '投瓶人用户ID(对捞起者匿名)',
  `content` text COMMENT '自由匿名文字(富文本HTML,与wish_id二选一)',
  `is_anonymous` tinyint(1) NOT NULL DEFAULT '1' COMMENT '投瓶是否匿名(1匿名隐藏身份/0实名,捞起者可见投瓶人身份)',
  `picker_is_anonymous` tinyint(1) NOT NULL DEFAULT '1' COMMENT '捞瓶人是否匿名(1匿名/0实名,实名时投瓶人可见捞瓶人身份)',
  `is_collected` tinyint(1) NOT NULL DEFAULT '0' COMMENT '捞起人是否已收藏该瓶(扔回海里时重置)',
  `return_count` int NOT NULL DEFAULT '0' COMMENT '被扔回海里次数',
  `is_hidden` tinyint(1) NOT NULL DEFAULT '0' COMMENT '管理端下架(1用户端不可见,数据保留)',
  `wish_id` bigint unsigned DEFAULT NULL COMMENT '关联心愿ID(与content二选一)',
  `wish_title` varchar(200) DEFAULT NULL COMMENT '关联心愿标题快照(匿名展示)',
  `wish_tags` json DEFAULT NULL COMMENT '关联心愿标签快照',
  `status` enum('FLOATING','PICKED','RETURNED') NOT NULL DEFAULT 'FLOATING' COMMENT '状态:漂流中/已被捞起/被扔回海里(扔回后回到海面可再被捞起)',
  `picker_user_id` bigint unsigned DEFAULT NULL COMMENT '捞起人用户ID',
  `thrown_at` datetime NOT NULL COMMENT '投瓶时间(UTC)',
  `picked_at` datetime DEFAULT NULL COMMENT '捞瓶时间(UTC)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  `audio_url` varchar(500) DEFAULT NULL COMMENT '语音瓶音频 URL（S01 资产 /files/ 前缀；与 content/wishId 三选一）',
  `audio_duration_seconds` int DEFAULT NULL COMMENT '语音时长秒（≤60，服务端校验）',
  PRIMARY KEY (`id`),
  KEY `idx_drift_bottle_float` (`status`,`thrower_user_id`),
  KEY `idx_drift_bottle_thrower` (`thrower_user_id`,`id`),
  KEY `idx_drift_bottle_picker` (`picker_user_id`,`picked_at`),
  KEY `idx_drift_bottle_throw_time` (`thrower_user_id`,`thrown_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='漂流瓶(匿名,海面漂流)';

CREATE TABLE `wish_drift_bottle_comment` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `bottle_id` bigint unsigned NOT NULL COMMENT '漂流瓶ID',
  `user_id` bigint unsigned NOT NULL COMMENT '评论者ID(投瓶人或捞起人)',
  `parent_id` bigint unsigned DEFAULT NULL COMMENT '父评论ID(回复场景,顶级评论为NULL)',
  `reply_to_user_id` bigint unsigned DEFAULT NULL COMMENT '被回复人ID(顶级评论为NULL)',
  `content` varchar(500) NOT NULL COMMENT '评论内容(纯文本,最长500字)',
  `is_anonymous` tinyint(1) NOT NULL DEFAULT '1' COMMENT '评论是否匿名(1匿名/0实名)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  PRIMARY KEY (`id`),
  KEY `idx_drift_comment_bottle` (`bottle_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='漂流瓶评论(默认匿名,可切换实名)';

CREATE TABLE `wish_drift_bottle_fish_log` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `bottle_id` bigint unsigned NOT NULL COMMENT '漂流瓶ID',
  `user_id` bigint unsigned NOT NULL COMMENT '打捞人用户ID',
  `request_id` varchar(64) DEFAULT NULL COMMENT '业务请求标识(宠物代捞按活动生成,幂等重放依据)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '打捞时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fish_log_request` (`request_id`),
  KEY `idx_drift_fish_user` (`user_id`,`created_at`),
  KEY `idx_drift_fish_bottle` (`bottle_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='漂流瓶打捞流水(每日配额/看板趋势)';

CREATE TABLE `wish_drift_bottle_interaction` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `bottle_id` bigint unsigned NOT NULL COMMENT '漂流瓶ID',
  `user_id` bigint unsigned NOT NULL COMMENT '回应发起者(捞起人)',
  `type` enum('BLESS','LIGHT') NOT NULL COMMENT '匿名祝福/点亮对方心愿',
  `peer_wish_id` bigint unsigned NOT NULL COMMENT '对方心愿ID(LIGHT 时 light_count+1)',
  `interact_date` date NOT NULL COMMENT '互动日期(幂等键)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bottle_interact_daily` (`bottle_id`,`user_id`,`interact_date`),
  KEY `idx_bottle_interact_user` (`user_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='漂流瓶匿名回应(祝福/点亮,每日1次)';

CREATE TABLE `wish_encounter_letter` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `owner_user_id` bigint unsigned NOT NULL COMMENT '信笺归属用户',
  `peer_user_id` bigint unsigned NOT NULL COMMENT '对方用户(匿名展示,不外露)',
  `peer_wish_id` bigint unsigned NOT NULL COMMENT '对方心愿ID(点亮互动目标)',
  `geohash6` char(6) NOT NULL COMMENT '相遇网格(约1.2km)',
  `time_bucket` datetime NOT NULL COMMENT '30 分钟时间桶(向下取整,UTC)',
  `wish_tags` json DEFAULT NULL COMMENT '双方重叠心愿标签快照',
  `encounter_time` datetime NOT NULL COMMENT '相遇时刻(UTC,桶内实际时间)',
  `content` varchar(300) DEFAULT NULL COMMENT '诗意文案(PENDING 时对用户返回 null)',
  `status` enum('PENDING','DELIVERED','READ') NOT NULL DEFAULT 'PENDING' COMMENT '状态机',
  `deliver_after` datetime NOT NULL COMMENT '延迟投递时间(UTC,生成时随机 1-24h)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `delivered_at` datetime DEFAULT NULL COMMENT '投递时间(UTC)',
  `read_at` datetime DEFAULT NULL COMMENT '拆信时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_letter_pair` (`owner_user_id`,`peer_user_id`,`geohash6`,`time_bucket`),
  KEY `idx_letter_owner` (`owner_user_id`,`status`,`deliver_after`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='相遇信笺(匿名,延迟投递)';

CREATE TABLE `wish_encounter_letter_interaction` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `letter_id` bigint unsigned NOT NULL COMMENT '信笺ID',
  `user_id` bigint unsigned NOT NULL COMMENT '互动发起者(信笺归属人)',
  `type` enum('BLESS','LIGHT') NOT NULL COMMENT '匿名祝福/点亮对方心愿',
  `peer_wish_id` bigint unsigned NOT NULL COMMENT '对方心愿ID(LIGHT 时 support_count+1)',
  `interact_date` date NOT NULL COMMENT '互动日期(用户时区日,幂等键)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_letter_interact_daily` (`letter_id`,`user_id`,`interact_date`),
  KEY `idx_letter_interact_user` (`user_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='信笺匿名互动(祝福/点亮,每日1次)';

CREATE TABLE `wish_env_config` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花ID)',
  `env_code` varchar(48) NOT NULL COMMENT '环境代码(SUNNY/RAIN/SNOW/CLOUDY/RAINBOW/SPRING/.../METEOR_SHOWER;唯一)',
  `category` varchar(16) NOT NULL COMMENT '环境分类(WEATHER天气/SEASON季节/TIME时段/SPECIAL_EVENT特殊事件;仅管理端分组展示)',
  `name` varchar(64) NOT NULL COMMENT '环境名称(如流星雨/中秋)',
  `description` varchar(255) DEFAULT NULL COMMENT '环境描述(管理端展示)',
  `priority` int NOT NULL DEFAULT '0' COMMENT '渲染优先级(数值大者胜:SPECIAL_EVENT=100/MOOD=80/WEATHER=50/SEASON=30/TIME=10)',
  `visual` json DEFAULT NULL COMMENT '四端渲染视觉参数JSON(crownColor/skyColor/particle/lightCoreColor等,前端透传消费)',
  `is_active` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用(0=下架,读取方过滤)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_env_code` (`env_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='世界生命树环境配置(表化,Sprint2.2新增环境仅插行不改代码)';

CREATE TABLE `wish_event_inbox` (
  `consumer_name` varchar(64) NOT NULL COMMENT '消费者标识(每个消费逻辑一个)',
  `event_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '全局事件ID',
  `processed_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '处理时间(UTC)',
  PRIMARY KEY (`consumer_name`,`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='消费端事件去重(与本地副作用同事务写入)';

CREATE TABLE `wish_expected_at_action` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `wish_id` bigint unsigned NOT NULL COMMENT '心愿ID',
  `action` enum('EXTEND','ADJUST','TO_CAPSULE') NOT NULL COMMENT '选择: 延长预期/AI调整目标/转入时间胶囊',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  PRIMARY KEY (`id`),
  KEY `idx_expected_action_user` (`user_id`,`created_at`),
  KEY `idx_expected_action_wish` (`wish_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预期管理选项埋点(转化率分析)';

CREATE TABLE `wish_fence` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `name` varchar(60) NOT NULL COMMENT '围栏名称(如"老城书店")',
  `wish_id` bigint unsigned NOT NULL COMMENT '到达后触发绽放的心愿ID',
  `center_geohash` char(7) NOT NULL COMMENT '围栏中心 geohash7(服务端存储,客户端不可见)',
  `radius_m` int NOT NULL COMMENT '半径(米,最小10)',
  `valid_from` datetime DEFAULT NULL COMMENT '生效开始(UTC,NULL=不限)',
  `valid_to` datetime DEFAULT NULL COMMENT '生效结束(UTC,NULL=不限)',
  `is_active` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用',
  `created_by` bigint unsigned NOT NULL COMMENT '创建管理员(管理后台用户ID)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  KEY `idx_fence_wish` (`wish_id`,`is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='地理围栏(到达触发心愿绽放,坐标不回传)';

CREATE TABLE `wish_fence_arrival` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `fence_id` bigint unsigned NOT NULL COMMENT '围栏ID',
  `user_id` bigint unsigned NOT NULL COMMENT '到达用户',
  `wish_id` bigint unsigned NOT NULL COMMENT '触发绽放的心愿ID',
  `checkin_date` date NOT NULL COMMENT '到达日期(用户时区日)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_arrival_daily` (`fence_id`,`user_id`,`checkin_date`),
  KEY `idx_arrival_user` (`user_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='围栏到达记录(幂等防刷)';

CREATE TABLE `wish_fulfillment` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `wish_id` bigint unsigned NOT NULL COMMENT '心愿ID(1:1)',
  `user_id` bigint unsigned NOT NULL COMMENT '还愿用户ID(即作者)',
  `story` text NOT NULL COMMENT '还愿故事(≤5000字, 已XSS转义)',
  `media_urls` json DEFAULT NULL COMMENT '完成照片/视频URL列表(OSS Key)',
  `feeling` varchar(1000) DEFAULT NULL COMMENT '感悟(≤1000字, API契约字段)',
  `audit_status` enum('PENDING','APPROVED','REJECTED','AUTO_HIDDEN') NOT NULL DEFAULT 'PENDING' COMMENT '审核状态: 先发后审, 提交即生效仅标记待审',
  `is_visible` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否可见(与audit_status解耦, 先发后审)',
  `is_inherited` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否已传承推送(Sprint 2.7 愿望传承)',
  `share_to_community` tinyint NOT NULL DEFAULT '0' COMMENT '是否显式授权分享到社区(B03:默认0,不自动传播)',
  `share_consent_at` datetime DEFAULT NULL COMMENT '分享授权时间(作者勾选时刻)',
  `content_version` int NOT NULL DEFAULT '1' COMMENT '分享内容版本(去重与撤回判定依据)',
  `share_revoked_at` datetime DEFAULT NULL COMMENT '分享撤销时间(故事撤回/转私密时回填)',
  `deleted_at` datetime DEFAULT NULL COMMENT '软删除时间(作者撤回还愿故事, 保留审计)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  `purchase_order_id` bigint DEFAULT NULL COMMENT '还愿购买凭证（关联商品的已完成订单 ID，服务端校验后回填）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fulfillment_wish` (`wish_id`),
  KEY `idx_fulfillment_user` (`user_id`,`created_at`),
  KEY `idx_fulfillment_audit` (`audit_status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='还愿记录(与wish 1:1)';

CREATE TABLE `wish_fulfillment_inherit` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `wish_id` bigint unsigned NOT NULL COMMENT '心愿ID',
  `fulfillment_id` bigint unsigned NOT NULL COMMENT '还愿记录ID',
  `user_id` bigint unsigned NOT NULL COMMENT '发起传承的用户(心愿作者)',
  `target_count` int NOT NULL DEFAULT '0' COMMENT '快照:当时同求(SAME_WISH)用户数',
  `pushed_count` int NOT NULL DEFAULT '0' COMMENT '实际推送成功数',
  `message` varchar(500) DEFAULT NULL COMMENT '作者附言(可空,≤500字)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_inherit_fulfillment` (`fulfillment_id`),
  KEY `idx_inherit_user` (`user_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='还愿传承推送记录(同求用户定向通知)';

CREATE TABLE `wish_gift` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `name` varchar(50) NOT NULL COMMENT '礼物名称',
  `icon_url` varchar(500) DEFAULT NULL COMMENT '礼物图标URL(空时前端展示默认礼物图标)',
  `animation_url` varchar(500) DEFAULT NULL COMMENT '礼物动效资源URL(可选,送出时播放)',
  `price_starlight` int unsigned NOT NULL COMMENT '单价(星光,正整数)',
  `status` enum('ON_SHELF','OFF_SHELF') NOT NULL DEFAULT 'OFF_SHELF' COMMENT '状态:上架/下架(仅上架对用户可见)',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序值(越小越靠前)',
  `description` varchar(200) DEFAULT NULL COMMENT '礼物描述(可选)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  `deleted_at` datetime DEFAULT NULL COMMENT '软删时间(保留审计轨迹)',
  PRIMARY KEY (`id`),
  KEY `idx_gift_status_sort` (`status`,`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='礼物目录(管理后台维护)';

CREATE TABLE `wish_gift_record` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `sender_id` bigint unsigned NOT NULL COMMENT '送礼人用户ID',
  `receiver_id` bigint unsigned NOT NULL COMMENT '收礼人用户ID',
  `gift_id` bigint unsigned NOT NULL COMMENT '礼物ID',
  `gift_name` varchar(50) NOT NULL COMMENT '礼物名称快照',
  `gift_icon_url` varchar(500) DEFAULT NULL COMMENT '礼物图标快照',
  `unit_price` int unsigned NOT NULL COMMENT '送礼时单价快照(星光)',
  `count` int unsigned NOT NULL COMMENT '数量(1-99)',
  `total_price` int unsigned NOT NULL COMMENT '总消耗(星光)=unit_price*count',
  `target_type` enum('WISH','POST','LIVE_ROOM') NOT NULL COMMENT '送礼场景:心愿/帖子/直播间',
  `target_id` bigint unsigned NOT NULL COMMENT '场景对象ID(心愿ID/帖子ID/直播间ID)',
  `message` varchar(100) DEFAULT NULL COMMENT '送礼留言(可选)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '送礼时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  `deleted_at` datetime DEFAULT NULL COMMENT '软删时间(管理端下架记录用)',
  PRIMARY KEY (`id`),
  KEY `idx_gift_record_sender` (`sender_id`,`id`),
  KEY `idx_gift_record_receiver` (`receiver_id`,`id`),
  KEY `idx_gift_record_target` (`target_type`,`target_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='礼物赠送记录(星光消费凭证)';

CREATE TABLE `wish_grayscale_config` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `feature_key` varchar(64) NOT NULL COMMENT '功能键(代码枚举白名单)',
  `gray_ratio` int NOT NULL DEFAULT '0' COMMENT '灰度比例(0-100,回滚=置0)',
  `description` varchar(200) DEFAULT NULL COMMENT '功能说明',
  `updated_by` bigint unsigned DEFAULT NULL COMMENT '最后修改人(管理后台用户ID)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_grayscale_feature` (`feature_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='灰度比例配置(哈希分流,可回滚)';

CREATE TABLE `wish_growth_record` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `wish_id` bigint unsigned NOT NULL COMMENT '心愿ID',
  `user_id` bigint unsigned NOT NULL COMMENT '作者用户ID',
  `type` enum('TEXT','IMAGE','VIDEO','DIARY') NOT NULL COMMENT '记录类型: TEXT纯文字/IMAGE图文/VIDEO视频/DIARY日记',
  `content` text NOT NULL COMMENT '记录内容(DIARY类型max 5000)',
  `media_urls` json DEFAULT NULL COMMENT '媒体资源URL列表(OSS Key)',
  `progress_delta` smallint NOT NULL DEFAULT '0' COMMENT '本次进度增量(支持负值, -32768~32767)',
  `audit_status` enum('PENDING','APPROVED','REJECTED','AUTO_HIDDEN') NOT NULL DEFAULT 'PENDING' COMMENT '审核状态',
  `is_visible` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否可见',
  `deleted_at` datetime DEFAULT NULL COMMENT '软删除时间(作者删除成长记录)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  KEY `idx_growth_wish` (`wish_id`),
  KEY `idx_growth_user` (`user_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='心愿成长记录';

CREATE TABLE `wish_interaction` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `wish_id` bigint unsigned NOT NULL COMMENT '心愿ID',
  `user_id` bigint unsigned NOT NULL COMMENT '互动用户ID',
  `type` enum('LIGHT','SAME_WISH','BLESS','ANON_STAR') NOT NULL COMMENT '互动类型: LIGHT点亮/SAME_WISH同求/BLESS祝福/ANON_STAR匿名星光',
  `content` varchar(500) DEFAULT NULL COMMENT '互动内容(仅BLESS有文字)',
  `starlight_cost` int NOT NULL DEFAULT '0' COMMENT '本次消耗星光数',
  `deleted_at` datetime DEFAULT NULL COMMENT '软删除时间(取消互动时置位, 保留审计轨迹)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_interaction_unique` (`wish_id`,`user_id`,`type`,(if(((`deleted_at` is null) and (`type` <> _utf8mb4'LIGHT')),1,NULL))),
  KEY `idx_interaction_query` (`wish_id`,`type`,`created_at`),
  KEY `idx_interaction_user` (`user_id`,`type`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='心愿互动记录';

CREATE TABLE `wish_lbs_freeze` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `user_id` bigint unsigned NOT NULL COMMENT '冻结用户',
  `reason` varchar(100) DEFAULT NULL COMMENT '冻结原因',
  `frozen_until` datetime NOT NULL COMMENT '冻结截止(UTC)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lbs_freeze_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='LBS 功能冻结(24h,可解冻)';

CREATE TABLE `wish_lbs_suspicious` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `speed_kmh` int NOT NULL COMMENT '推断速度(km/h)',
  `from_cell` char(7) NOT NULL COMMENT '起点 geohash7(无原始坐标)',
  `to_cell` char(7) NOT NULL COMMENT '终点 geohash7(无原始坐标)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  PRIMARY KEY (`id`),
  KEY `idx_suspicious_user` (`user_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='位置伪造可疑记录(异常跳跃)';

CREATE TABLE `wish_leaderboard_config` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `config_key` varchar(64) NOT NULL COMMENT '配置键',
  `config_value` varchar(200) NOT NULL COMMENT '配置值(字符串,业务层解析)',
  `description` varchar(200) DEFAULT NULL COMMENT '配置说明',
  `updated_by` bigint unsigned DEFAULT NULL COMMENT '最后修改人(管理后台用户ID)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_leaderboard_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='排行榜配置(计算周期/Top N/同分处理)';

CREATE TABLE `wish_live_widget_config` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `streamer_id` bigint unsigned NOT NULL COMMENT '主播用户ID(唯一)',
  `position` enum('TOP_LEFT','TOP_RIGHT','BOTTOM_LEFT','BOTTOM_RIGHT') NOT NULL DEFAULT 'BOTTOM_RIGHT' COMMENT '挂件位置',
  `style_config` json DEFAULT NULL COMMENT '样式配置 JSON(如 {"transparent":true,"accent":"#ffd700"})',
  `is_visible` tinyint(1) NOT NULL DEFAULT '1' COMMENT '该主播挂件是否展示',
  `updated_by` bigint unsigned DEFAULT NULL COMMENT '最后修改人(管理后台用户ID)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_widget_streamer` (`streamer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='直播心愿挂件配置(主播维度)';

CREATE TABLE `wish_match_config` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `config_key` varchar(64) NOT NULL COMMENT '配置键',
  `config_value` varchar(500) NOT NULL COMMENT '配置值(字符串,业务层解析)',
  `description` varchar(200) DEFAULT NULL COMMENT '配置说明',
  `updated_by` bigint unsigned DEFAULT NULL COMMENT '最后修改人(管理后台用户ID)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_match_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='匹配算法配置(权重/阈值/限频,实时生效)';

CREATE TABLE `wish_match_group` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `keyword` varchar(60) NOT NULL COMMENT '组主题关键词(来自心愿标签/目标)',
  `max_members` tinyint NOT NULL DEFAULT '4' COMMENT '小组容量(2-4人)',
  `wish_id` bigint unsigned DEFAULT NULL COMMENT '关联心愿ID(可空,自由建组)',
  `leader_id` bigint unsigned NOT NULL COMMENT '组长用户ID(创建者,退出自动转让)',
  `member_count` tinyint NOT NULL DEFAULT '1' COMMENT '当前 ACTIVE 成员数(事务内维护)',
  `city_code` varchar(8) DEFAULT NULL COMMENT '同城代理(创建人活跃公开心愿 geohash 前缀4,可空)',
  `status` enum('OPEN','FULL','CLOSED') NOT NULL DEFAULT 'OPEN' COMMENT '状态: 可加入/已满员/已关闭(解散或无人)',
  `closed_at` datetime DEFAULT NULL COMMENT '关闭时间(UTC,解散或无人时写入)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  KEY `idx_group_scan` (`status`,`keyword`,`id`),
  KEY `idx_group_leader` (`leader_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='同愿小组(2-4人打卡监督)';

CREATE TABLE `wish_match_member` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `group_id` bigint unsigned NOT NULL COMMENT '小组ID',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `role` enum('LEADER','MEMBER') NOT NULL DEFAULT 'MEMBER' COMMENT '角色(LEADER创建者可踢人/转让)',
  `status` enum('ACTIVE','LEFT','KICKED') NOT NULL DEFAULT 'ACTIVE' COMMENT '成员状态(退出/被踢后保留历史仅标记)',
  `join_message` varchar(200) DEFAULT NULL COMMENT '入组留言(可空)',
  `joined_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间(UTC)',
  `left_at` datetime DEFAULT NULL COMMENT '退出/被踢时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_active` (`group_id`,`user_id`,(if((`status` = _utf8mb4'ACTIVE'),1,NULL))),
  KEY `idx_member_user` (`user_id`,`status`),
  KEY `idx_member_group` (`group_id`,`status`,`joined_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='同愿小组成员(退出/被踢保留历史)';

CREATE TABLE `wish_moderation_case` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花)',
  `target_type` varchar(32) NOT NULL COMMENT '目标类型:WISH/WISH_COMMENT/GROWTH_RECORD/FULFILLMENT/DRIFT_BOTTLE/BOTTLE_COMMENT',
  `target_id` bigint unsigned NOT NULL COMMENT '目标ID',
  `target_revision` bigint NOT NULL DEFAULT '0' COMMENT '目标版本(受理时快照)',
  `status` enum('OPEN','IN_REVIEW','RESOLVED') NOT NULL DEFAULT 'OPEN' COMMENT '工单状态',
  `assignee_id` bigint unsigned DEFAULT NULL COMMENT '当前处理人',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁(CAS 处理)',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `resolved_at` datetime(3) DEFAULT NULL COMMENT '结案时间',
  `active_flag` tinyint GENERATED ALWAYS AS (if((`status` in (_utf8mb4'OPEN',_utf8mb4'IN_REVIEW')),1,NULL)) VIRTUAL COMMENT '活动工单标志(结案为NULL)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_case_active` (`target_type`,`target_id`,`active_flag`),
  KEY `idx_case_status` (`status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='统一治理工单(同一内容同一版本仅一个活动case)';

CREATE TABLE `wish_moderation_decision` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花)',
  `case_id` bigint unsigned NOT NULL COMMENT '工单ID',
  `actor_id` bigint unsigned NOT NULL COMMENT '处理人ID',
  `decision` enum('NO_ACTION','HIDE','RESTORE') NOT NULL COMMENT '决定:无动作/隐藏/恢复',
  `reason_code` varchar(32) DEFAULT NULL COMMENT '原因码',
  `reason_text` varchar(500) DEFAULT NULL COMMENT '原因说明(不含敏感正文)',
  `before_state` varchar(40) DEFAULT NULL COMMENT '处理前状态快照',
  `after_state` varchar(40) DEFAULT NULL COMMENT '处理后状态快照',
  `target_revision` bigint NOT NULL DEFAULT '0' COMMENT '目标版本(处理时快照)',
  `request_id` varchar(64) DEFAULT NULL COMMENT '请求ID(审计)',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '追加写,禁止UPDATE历史决定',
  PRIMARY KEY (`id`),
  KEY `idx_decision_case` (`case_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='治理决定(追加写审计)';

CREATE TABLE `wish_notification_preference` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `notification_type` varchar(30) NOT NULL COMMENT '通知类型: WISH_COMMENT/WISH_LIGHT/WISH_FULFILL/CAPSULE_OPEN/AI_REMINDER/CHECKIN_REMINDER/MATCH_RECOMMEND/BRAND_REWARD/ENCOUNTER_LETTER/DEVICE_OFFLINE/LEVEL_UP/BADGE_EARNED/SYSTEM',
  `channel` enum('PUSH','SMS','EMAIL','IN_APP') NOT NULL COMMENT '通知渠道',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否开启',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_preference_unique` (`user_id`,`notification_type`,`channel`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户通知偏好矩阵(无记录=默认开启)';

CREATE TABLE `wish_operation` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `actor_type` varchar(16) NOT NULL COMMENT '操作者类型:USER/ADMIN/JOB/SERVICE',
  `actor_id` bigint unsigned NOT NULL COMMENT '操作者ID(数值型主体;服务主体填0)',
  `actor_ref` varchar(64) DEFAULT NULL COMMENT '非数值主体标识(serviceId,如 mall-job)',
  `operation_type` varchar(48) NOT NULL COMMENT '操作类型:ASSET_EXCHANGE/GIFT_SEND/STARLIGHT_DECAY等',
  `request_key` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '请求键(客户端幂等键或业务唯一键)',
  `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '请求摘要SHA-256(操作类型+目标+业务参数,同键异内容冲突判定)',
  `target_type` varchar(40) DEFAULT NULL COMMENT '目标类型(资产/心愿/礼物)',
  `target_id` bigint unsigned DEFAULT NULL COMMENT '目标ID',
  `status` enum('PROCESSING','COMPLETED') NOT NULL COMMENT '状态(仅 COMPLETED 对外可见:失败整体回滚)',
  `response_json` json DEFAULT NULL COMMENT '已完成结果(重放依据,不落敏感正文)',
  `error_code` varchar(80) DEFAULT NULL COMMENT '终态错误码(预留;当前失败即回滚不留凭证)',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `completed_at` datetime(3) DEFAULT NULL COMMENT '完成时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wish_operation` (`actor_type`,`actor_id`,`operation_type`,`request_key`),
  KEY `idx_wish_operation_actor_time` (`actor_type`,`actor_id`,`created_at`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通用写操作持久幂等记录(同键同摘要重放,同键异摘要409)';

CREATE TABLE `wish_outbox` (
  `event_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '全局事件ID(UUID)',
  `aggregate_type` varchar(40) NOT NULL COMMENT '聚合类型:WISH/FULFILLMENT/WALLET',
  `aggregate_id` bigint unsigned NOT NULL COMMENT '聚合ID',
  `aggregate_version` bigint NOT NULL DEFAULT '0' COMMENT '聚合版本(事件排序与幂等)',
  `event_type` varchar(64) NOT NULL COMMENT '事件类型:WishFulfilled/WishVisibilityChanged等',
  `payload` json DEFAULT NULL COMMENT '最小载荷(ID/版本/展示字段;私密正文不进总线)',
  `status` enum('PENDING','PUBLISHED','DEAD') NOT NULL DEFAULT 'PENDING' COMMENT '投递状态',
  `attempts` int NOT NULL DEFAULT '0' COMMENT '已尝试次数',
  `next_attempt_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '下次可投递时间',
  `lease_owner` varchar(64) DEFAULT NULL COMMENT '租约持有者(实例标识)',
  `lease_until` datetime(3) DEFAULT NULL COMMENT '租约到期时间',
  `lease_version` int NOT NULL DEFAULT '0' COMMENT '租约版本（T16 fencing：每次认领+1，回写校验）',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `published_at` datetime(3) DEFAULT NULL COMMENT '投递成功时间(UTC)',
  PRIMARY KEY (`event_id`),
  KEY `idx_wish_outbox_status` (`status`,`next_attempt_at`,`event_id`),
  KEY `idx_wish_outbox_aggregate` (`aggregate_type`,`aggregate_id`,`aggregate_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='事务性发件箱(业务事实与事件原子提交)';

CREATE TABLE `wish_progress` (
  `wish_id` bigint unsigned NOT NULL COMMENT '心愿ID(与wish一对一, 主键)',
  `current_value` int NOT NULL DEFAULT '0' COMMENT '当前进度值(支持任意单位: 步数/页数/天数)',
  `target_value` int NOT NULL DEFAULT '100' COMMENT '目标值(percentage = current_value / target_value * 100)',
  `current_streak` int NOT NULL DEFAULT '0' COMMENT '当前连续打卡天数(补卡中断, 重置为1)',
  `max_streak` int NOT NULL DEFAULT '0' COMMENT '历史最长连续天数(只增不减)',
  `last_checkin_date` date DEFAULT NULL COMMENT '最近打卡日(用户业务日;连续天数判定依据,B09)',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号(防并发覆盖: 离线打卡/BLE数据重放)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`wish_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='心愿进度(1:1 with wish)';

CREATE TABLE `wish_report` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花)',
  `reporter_id` bigint unsigned NOT NULL COMMENT '举报人',
  `target_type` varchar(32) NOT NULL COMMENT '目标类型',
  `target_id` bigint unsigned NOT NULL COMMENT '目标ID',
  `reason_code` enum('SPAM','ABUSE','FRAUD','PRIVACY','OTHER') NOT NULL COMMENT '举报原因',
  `description` varchar(500) DEFAULT NULL COMMENT '补充说明(OTHER必填)',
  `evidence_refs` varchar(1000) DEFAULT NULL COMMENT '证据附件(JSON数组,≤3)',
  `case_id` bigint unsigned DEFAULT NULL COMMENT '关联工单',
  `status` enum('PENDING','RESOLVED') NOT NULL DEFAULT 'PENDING' COMMENT '处理进度',
  `dedup_key` varchar(80) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '活动举报键(type:id:reason;结案置空释放唯一)',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_report_dedup` (`dedup_key`),
  KEY `idx_report_reporter` (`reporter_id`,`created_at`),
  KEY `idx_report_case` (`case_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='内容举报(未结举报同内容同理由合并)';

CREATE TABLE `wish_resource_log` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `delta` int NOT NULL COMMENT '星光变化量(正=获取, 负=消耗)',
  `type` enum('EARN','SPEND') NOT NULL COMMENT '流水类型: EARN获取/SPEND消耗',
  `source` varchar(30) NOT NULL COMMENT '来源: SIGNIN/LIGHTED/CHECKIN/FULFILL/LIGHT_OTHER/ANON_STAR/EXCHANGE',
  `ref_id` bigint DEFAULT NULL COMMENT '关联业务ID(互动/打卡/还愿ID)',
  `balance_after` int NOT NULL COMMENT '操作后余额快照',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  KEY `idx_resource_user` (`user_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='星光流水(最终事实来源)';

CREATE TABLE `wish_reward_obligation` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `biz_key` varchar(96) NOT NULL COMMENT '业务幂等键（来源事实+受益人，uk）',
  `source` varchar(24) NOT NULL COMMENT '来源：WISH_SIGNIN/WISH_SIGNIN_MILESTONE',
  `source_biz_id` varchar(64) NOT NULL COMMENT '来源事实 ID',
  `beneficiary` bigint unsigned NOT NULL COMMENT '受益人用户 ID',
  `reward_type` varchar(24) NOT NULL COMMENT 'EXP（community 成长经验）',
  `amount` int NOT NULL COMMENT '数量',
  `description` varchar(200) DEFAULT NULL COMMENT '发放描述（传 community）',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SETTLED/REJECTED',
  `attempts` int NOT NULL DEFAULT '0' COMMENT '已尝试次数',
  `last_error` varchar(300) DEFAULT NULL COMMENT '最近错误（REJECTED 记拒绝原因）',
  `next_retry_at` datetime DEFAULT NULL COMMENT '下次重试时间（退避）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `settled_at` datetime DEFAULT NULL COMMENT '交付完成时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz_key` (`biz_key`),
  KEY `idx_status_retry` (`status`,`next_retry_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='跨服务奖励交付义务（R07）';

CREATE TABLE `wish_signin_milestone_claim` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `milestone_days` int NOT NULL COMMENT '连续签到里程碑天数(7/14/30)',
  `starlight_reward` int NOT NULL COMMENT '本次领取发放星光数(快照)',
  `exp_reward` int NOT NULL COMMENT '本次领取发放经验数(快照)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '领取时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_signin_milestone` (`user_id`,`milestone_days`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='连续签到里程碑领取记录';

CREATE TABLE `wish_special_event` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花ID)',
  `event_code` varchar(48) NOT NULL COMMENT '事件代码(关联wish_env_config.env_code,如METEOR_SHOWER/AURORA/STAR_NIGHT)',
  `title` varchar(64) NOT NULL COMMENT '事件标题(管理端展示)',
  `description` varchar(255) DEFAULT NULL COMMENT '事件描述(管理端展示)',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态(ACTIVE活跃/ENDED已结束;过期未标记读取方惰性判定)',
  `triggered_by` bigint unsigned NOT NULL COMMENT '触发管理员用户ID(审计)',
  `triggered_at` datetime NOT NULL COMMENT '触发时间(UTC,全站同步展示起点)',
  `expires_at` datetime DEFAULT NULL COMMENT '过期时间(UTC;NULL=持续至手动结束)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  KEY `idx_special_event_status` (`status`,`triggered_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='世界生命树全站特殊事件(Sprint2.2,流星雨/极光/星辰夜)';

CREATE TABLE `wish_starlight_operation` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `operation_id` varchar(160) NOT NULL COMMENT '业务操作唯一键(与pet_operation.operation_id同宽160，调用方生成，禁止截断)',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `operation_type` enum('EARN','SPEND') NOT NULL COMMENT '操作类型:发放/扣减',
  `amount` int NOT NULL COMMENT '请求数量(正整数,实际入账可能因封顶截断)',
  `credited_amount` int NOT NULL DEFAULT '0' COMMENT '实际入账量(EARN封顶截断后;SPEND等于amount)',
  `balance_after` int NOT NULL COMMENT '操作后余额快照',
  `source` varchar(30) NOT NULL COMMENT '流水来源(ResourceLogSource)',
  `ref_id` bigint unsigned DEFAULT NULL COMMENT '关联业务ID(活动/对战/背包记录,审计用)',
  `request_digest` char(64) DEFAULT NULL COMMENT '请求摘要SHA-256(用户+类型+金额+来源+refId,冲突判定依据)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '完成时间(UTC,即原事务提交点)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  `refund_of_operation_id` varchar(160) DEFAULT NULL COMMENT '退款原单操作键(P02/TX-04，仅退款流水填写；累计退款校验按原单聚合) AFTER request_digest',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wish_starlight_operation` (`operation_id`),
  KEY `idx_wish_starlight_operation_user` (`user_id`,`created_at`),
  KEY `idx_wish_starlight_operation_refund_of` (`refund_of_operation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物星光交易幂等记录(仅成功结果,重复请求返回原结果)';

CREATE TABLE `wish_user_asset` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `asset_id` bigint unsigned NOT NULL COMMENT '资产配置ID',
  `source` enum('EXCHANGE','ACTIVITY','COLLECT') NOT NULL COMMENT '获取来源(星光兑换/活动/星火收藏)',
  `status` enum('OWNED','REFUNDED') NOT NULL DEFAULT 'OWNED' COMMENT '资产状态',
  `is_active_skin` tinyint(1) NOT NULL DEFAULT '0' COMMENT '当前激活皮肤(同类型互斥)',
  `is_active_bgm` tinyint(1) NOT NULL DEFAULT '0' COMMENT '当前激活 BGM',
  `ref_wish_id` bigint unsigned DEFAULT NULL COMMENT '关联心愿(星火收藏品=被收藏的 SPARK 心愿)',
  `acquired_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '获取时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_asset` (`user_id`,`asset_id`),
  KEY `idx_user_asset_user` (`user_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户虚拟资产(皮肤/BGM/星火收藏品)';

CREATE TABLE `wish_user_badge` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `badge_id` bigint unsigned NOT NULL COMMENT '徽章ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_badge` (`user_id`,`badge_id`),
  KEY `idx_user_badge_badge` (`badge_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户已获得徽章';

CREATE TABLE `wish_user_stat` (
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID(与mall_user.sys_user一对一, 主键)',
  `timezone` varchar(32) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '用户时区(IANA, 用于AI提醒/环境切换/胶囊到期)',
  `level` tinyint NOT NULL DEFAULT '1' COMMENT '当前等级(1-5)',
  `level_title` varchar(30) NOT NULL DEFAULT '追梦新人' COMMENT '等级标题',
  `highest_level` tinyint NOT NULL DEFAULT '1' COMMENT '历史最高等级(等级只升不降的判定依据)',
  `starlight_balance` int NOT NULL DEFAULT '0' COMMENT '星光余额(冗余, 以wish_resource_log流水为最终事实来源)',
  `total_wishes` int NOT NULL DEFAULT '0' COMMENT '累计创建心愿数(含已软删, 只增不减)',
  `active_wishes` int NOT NULL DEFAULT '0' COMMENT '当前有效心愿数(软删-1)',
  `total_fulfilled` int NOT NULL DEFAULT '0' COMMENT '累计还愿数(历史事实, 不回退)',
  `total_helped` int NOT NULL DEFAULT '0' COMMENT '累计帮助他人次数(点亮+匿名星光)',
  `total_checkin_days` int NOT NULL DEFAULT '0' COMMENT '累计打卡天数(含补卡, 历史事实记录)',
  `last_active_at` datetime DEFAULT NULL COMMENT '最后活跃时间(UTC, 衰减判定依据)',
  `risk_score` int NOT NULL DEFAULT '0' COMMENT '风控分(驳回累计)',
  `is_restricted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否被限制发布',
  `restricted_until` datetime DEFAULT NULL COMMENT '限制解除时间(UTC)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`user_id`),
  KEY `idx_stat_active` (`last_active_at`),
  KEY `idx_stat_timezone` (`timezone`),
  KEY `idx_stat_level` (`level`),
  KEY `idx_stat_helped` (`total_helped`),
  KEY `idx_stat_checkin` (`total_checkin_days`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户心愿统计(单表聚合避免实时count)';

CREATE TABLE `wish_virtual_asset` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `asset_type` enum('SKIN','BGM','SPECIAL_FRUIT') NOT NULL COMMENT '资产类型(徽章走 wish_badge 体系)',
  `name` varchar(60) NOT NULL COMMENT '资产名称',
  `description` varchar(300) DEFAULT NULL COMMENT '资产描述',
  `icon` varchar(500) DEFAULT NULL COMMENT '图标/预览 URL',
  `resource_url` varchar(500) DEFAULT NULL COMMENT '资源 URL(皮肤 CSS/BGM mp3)',
  `price_starlight` int NOT NULL DEFAULT '0' COMMENT '星光价格(0=不可星光兑换)',
  `price_rmb` int NOT NULL DEFAULT '0' COMMENT 'RMB 价格(分,0=不可内购)',
  `pay_method` enum('STARLIGHT','RMB','BOTH') NOT NULL DEFAULT 'STARLIGHT' COMMENT '支付方式',
  `stock` int NOT NULL DEFAULT '0' COMMENT '限量库存(0=无限)',
  `stock_mode` varchar(16) NOT NULL DEFAULT 'UNLIMITED' COMMENT '库存模式:UNLIMITED/LIMITED(B05;DB 为事实,Redis 仅展示缓存)',
  `stock_remaining` int DEFAULT NULL COMMENT '剩余库存(LIMITED 非负;条件扣减 stock_remaining>0)',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本(B05)',
  `valid_from` datetime DEFAULT NULL COMMENT '上架开始(UTC,NULL=不限)',
  `valid_to` datetime DEFAULT NULL COMMENT '下架时间(UTC,过期自动下架)',
  `is_active` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否上架',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  KEY `idx_asset_type` (`asset_type`,`is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='虚拟资产配置(皮肤/BGM/星火收藏品)';

CREATE TABLE `wish_warm_event` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `user_id` bigint unsigned NOT NULL COMMENT '发布者用户ID',
  `title` varchar(60) NOT NULL COMMENT '事件标题(≤60字)',
  `content` varchar(500) NOT NULL COMMENT '事件内容(≤500字)',
  `geohash` char(7) DEFAULT NULL COMMENT 'geohash7 模糊化坐标(约153m网格,无原始坐标)',
  `city_code` char(4) DEFAULT NULL COMMENT '城市代理(geohash4前缀)',
  `audit_status` enum('PENDING','APPROVED','REJECTED','AUTO_HIDDEN') NOT NULL DEFAULT 'PENDING' COMMENT '审核状态(DFA命中→AUTO_HIDDEN)',
  `is_visible` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否可见(先发后审)',
  `deleted_at` datetime DEFAULT NULL COMMENT '软删除时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  KEY `idx_warm_geo` (`is_visible`,`audit_status`,`geohash`),
  KEY `idx_warm_user` (`user_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='温暖事件(城市幸福地图UGC)';

CREATE TABLE `wish_world_tree_state` (
  `id` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '固定单行主键(恒为1)',
  `environment` varchar(32) NOT NULL DEFAULT 'SUNNY' COMMENT '当前环境(SUNNY/RAIN/RAINBOW;预留季节/天气/特殊事件扩展值)',
  `season` varchar(16) DEFAULT NULL COMMENT '当前季节(SPRING/SUMMER/AUTUMN/WINTER,mall-job每日00:00按UTC日期扫描写入;NULL=未扫描,读取方实时计算兜底)',
  `environment_source` varchar(32) NOT NULL DEFAULT 'INIT' COMMENT '最近一次环境变更来源(INIT/MOOD_RAIN/MOOD_RAIN_RENEW/MOOD_RAINBOW/BLESS_BURST_RAINBOW/RAINBOW_EXPIRED/MOOD_RECOVER)',
  `triggered_at` datetime DEFAULT NULL COMMENT '当前环境触发时间(UTC;RAIN续雨时保持首次触发时间作为最短持续基准)',
  `expires_at` datetime DEFAULT NULL COMMENT '当前环境过期时间(UTC;NULL=无固定过期,RAIN持续至扫描复评)',
  `last_scan_at` datetime DEFAULT NULL COMMENT '最近一次情绪扫描时间(UTC)',
  `sample_count` int NOT NULL DEFAULT '0' COMMENT '最近一次扫描聚合样本数(仅计数,无情绪明细)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='世界生命树全局环境状态(单行,情绪联动文档2.2/Sprint2.2)';

-- Required catalog/configuration: wish_ai_config
INSERT INTO `wish_ai_config` (`id`, `config_key`, `config_value`, `description`, `updated_by`, `created_at`, `updated_at`) VALUES (1, 'reminder.daily_limit', '1', '陪伴提醒单用户每日上限(条)', NULL, '2026-10-14 10:26:00', '2026-10-14 10:26:00');
INSERT INTO `wish_ai_config` (`id`, `config_key`, `config_value`, `description`, `updated_by`, `created_at`, `updated_at`) VALUES (2, 'reminder.quiet_start', '22:00', '免打扰时段开始(用户时区, HH:mm)', NULL, '2026-10-14 10:26:00', '2026-10-14 10:26:00');
INSERT INTO `wish_ai_config` (`id`, `config_key`, `config_value`, `description`, `updated_by`, `created_at`, `updated_at`) VALUES (3, 'reminder.quiet_end', '08:00', '免打扰时段结束(用户时区, HH:mm)', NULL, '2026-10-14 10:26:00', '2026-10-14 10:26:00');
INSERT INTO `wish_ai_config` (`id`, `config_key`, `config_value`, `description`, `updated_by`, `created_at`, `updated_at`) VALUES (4, 'expected.daily_limit', '3', '预期管理通知单用户每日上限(条)', NULL, '2026-10-14 10:26:00', '2026-10-14 10:26:00');
INSERT INTO `wish_ai_config` (`id`, `config_key`, `config_value`, `description`, `updated_by`, `created_at`, `updated_at`) VALUES (5, 'annual_report.ttl_hours', '168', '年度报告结果缓存时长(小时)', NULL, '2026-10-14 10:26:00', '2026-10-14 10:26:00');

-- Required catalog/configuration: wish_badge
INSERT INTO `wish_badge` (`id`, `code`, `name`, `icon`, `rarity`, `is_active`, `condition`, `created_at`, `updated_at`) VALUES (2001, 'FIRST_WISH', '第一次许愿', '', 'COMMON', 1, '{"type": "WISH_CREATED", "threshold": 1, "description": "发布第一个心愿"}', '2026-10-14 10:25:59', '2026-10-14 10:25:59');
INSERT INTO `wish_badge` (`id`, `code`, `name`, `icon`, `rarity`, `is_active`, `condition`, `created_at`, `updated_at`) VALUES (2002, 'FIRST_FULFILL', '第一次还愿', '', 'COMMON', 1, '{"type": "WISH_FULFILLED", "threshold": 1, "description": "完成第一个还愿"}', '2026-10-14 10:25:59', '2026-10-14 10:25:59');
INSERT INTO `wish_badge` (`id`, `code`, `name`, `icon`, `rarity`, `is_active`, `condition`, `created_at`, `updated_at`) VALUES (2003, 'HELP_100', '帮助100人', '', 'EPIC', 1, '{"type": "TOTAL_HELPED", "threshold": 100, "description": "累计帮助100人(点亮+匿名星光)"}', '2026-10-14 10:25:59', '2026-10-14 10:25:59');
INSERT INTO `wish_badge` (`id`, `code`, `name`, `icon`, `rarity`, `is_active`, `condition`, `created_at`, `updated_at`) VALUES (2004, 'PERSIST_365', '坚持365天', '', 'LEGENDARY', 1, '{"type": "TOTAL_CHECKIN_DAYS", "threshold": 365, "description": "累计打卡365天"}', '2026-10-14 10:25:59', '2026-10-14 10:25:59');

-- Required catalog/configuration: wish_category
INSERT INTO `wish_category` (`id`, `code`, `name`, `sort`, `icon`, `created_at`, `updated_at`) VALUES (1001, 'CAREER', '学业事业', 1, NULL, '2026-10-14 10:25:59', '2026-10-14 10:25:59');
INSERT INTO `wish_category` (`id`, `code`, `name`, `sort`, `icon`, `created_at`, `updated_at`) VALUES (1002, 'GROWTH', '成长改变', 2, NULL, '2026-10-14 10:25:59', '2026-10-14 10:25:59');
INSERT INTO `wish_category` (`id`, `code`, `name`, `sort`, `icon`, `created_at`, `updated_at`) VALUES (1003, 'RELATION', '情感关系', 3, NULL, '2026-10-14 10:25:59', '2026-10-14 10:25:59');
INSERT INTO `wish_category` (`id`, `code`, `name`, `sort`, `icon`, `created_at`, `updated_at`) VALUES (1004, 'TRAVEL', '旅行梦想', 4, NULL, '2026-10-14 10:25:59', '2026-10-14 10:25:59');
INSERT INTO `wish_category` (`id`, `code`, `name`, `sort`, `icon`, `created_at`, `updated_at`) VALUES (1005, 'WEALTH', '财富目标', 5, NULL, '2026-10-14 10:25:59', '2026-10-14 10:25:59');
INSERT INTO `wish_category` (`id`, `code`, `name`, `sort`, `icon`, `created_at`, `updated_at`) VALUES (1006, 'HOBBY', '兴趣生活', 6, NULL, '2026-10-14 10:25:59', '2026-10-14 10:25:59');

-- Required catalog/configuration: wish_env_config
INSERT INTO `wish_env_config` (`id`, `env_code`, `category`, `name`, `description`, `priority`, `visual`, `is_active`, `created_at`, `updated_at`) VALUES (3101, 'SUNNY', 'WEATHER', '晴天', '晴空万里，树心暖金光晕', 50, '{"particle": "NONE", "skyColor": "#87ceeb", "lightCoreColor": "#ffd700"}', 1, '2026-10-14 10:25:59', '2026-10-14 10:25:59');
INSERT INTO `wish_env_config` (`id`, `env_code`, `category`, `name`, `description`, `priority`, `visual`, `is_active`, `created_at`, `updated_at`) VALUES (3102, 'CLOUDY', 'WEATHER', '多云', '云层柔和，树影朦胧', 50, '{"particle": "NONE", "skyColor": "#9aa5b1", "lightCoreColor": "#cfd8dc"}', 1, '2026-10-14 10:25:59', '2026-10-14 10:25:59');
INSERT INTO `wish_env_config` (`id`, `env_code`, `category`, `name`, `description`, `priority`, `visual`, `is_active`, `created_at`, `updated_at`) VALUES (3103, 'RAIN', 'WEATHER', '下雨', '细雨润泽，果实微光涟漪', 50, '{"particle": "RAIN", "skyColor": "#5d737e", "lightCoreColor": "#4facfe"}', 1, '2026-10-14 10:25:59', '2026-10-14 10:25:59');
INSERT INTO `wish_env_config` (`id`, `env_code`, `category`, `name`, `description`, `priority`, `visual`, `is_active`, `created_at`, `updated_at`) VALUES (3104, 'SNOW', 'WEATHER', '下雪', '落雪覆枝，冬夜静谧', 50, '{"particle": "SNOWFLAKE", "skyColor": "#7a8ba3", "lightCoreColor": "#bfe8ff"}', 1, '2026-10-14 10:25:59', '2026-10-14 10:25:59');
INSERT INTO `wish_env_config` (`id`, `env_code`, `category`, `name`, `description`, `priority`, `visual`, `is_active`, `created_at`, `updated_at`) VALUES (3105, 'RAINBOW', 'WEATHER', '彩虹', '雨后初霁，七彩拱桥横跨树冠（情绪联动触发）', 80, '{"particle": "NONE", "skyColor": "#6c5ce7", "lightCoreColor": "#ff9ff3"}', 1, '2026-10-14 10:25:59', '2026-10-14 10:25:59');
INSERT INTO `wish_env_config` (`id`, `env_code`, `category`, `name`, `description`, `priority`, `visual`, `is_active`, `created_at`, `updated_at`) VALUES (3106, 'SPRING', 'SEASON', '春季', '嫩绿花瓣飘落', 30, '{"particle": "PETAL", "crownColor": "#7ef0c0"}', 1, '2026-10-14 10:25:59', '2026-10-14 10:25:59');
INSERT INTO `wish_env_config` (`id`, `env_code`, `category`, `name`, `description`, `priority`, `visual`, `is_active`, `created_at`, `updated_at`) VALUES (3107, 'SUMMER', 'SEASON', '夏季', '绿叶阳光斑驳', 30, '{"particle": "SUNBURST", "crownColor": "#3ddc97"}', 1, '2026-10-14 10:25:59', '2026-10-14 10:25:59');
INSERT INTO `wish_env_config` (`id`, `env_code`, `category`, `name`, `description`, `priority`, `visual`, `is_active`, `created_at`, `updated_at`) VALUES (3108, 'AUTUMN', 'SEASON', '秋季', '金黄落叶纷飞', 30, '{"particle": "LEAF", "crownColor": "#ffb347"}', 1, '2026-10-14 10:25:59', '2026-10-14 10:25:59');
INSERT INTO `wish_env_config` (`id`, `env_code`, `category`, `name`, `description`, `priority`, `visual`, `is_active`, `created_at`, `updated_at`) VALUES (3109, 'WINTER', 'SEASON', '冬季', '枯枝雪花点缀', 30, '{"particle": "SNOWFLAKE", "crownColor": "#bfe8ff"}', 1, '2026-10-14 10:25:59', '2026-10-14 10:25:59');
INSERT INTO `wish_env_config` (`id`, `env_code`, `category`, `name`, `description`, `priority`, `visual`, `is_active`, `created_at`, `updated_at`) VALUES (3110, 'DAY', 'TIME', '白天', '晨光清朗（06-12 时）', 10, '{"skyColor": "#87ceeb"}', 1, '2026-10-14 10:25:59', '2026-10-14 10:25:59');
INSERT INTO `wish_env_config` (`id`, `env_code`, `category`, `name`, `description`, `priority`, `visual`, `is_active`, `created_at`, `updated_at`) VALUES (3111, 'DUSK', 'TIME', '黄昏', '暮色橙霞（12-18 时）', 10, '{"skyColor": "#ff9a76"}', 1, '2026-10-14 10:25:59', '2026-10-14 10:25:59');
INSERT INTO `wish_env_config` (`id`, `env_code`, `category`, `name`, `description`, `priority`, `visual`, `is_active`, `created_at`, `updated_at`) VALUES (3112, 'NIGHT', 'TIME', '夜晚', '星幕初垂（18-24 时）', 10, '{"skyColor": "#0c1b3a"}', 1, '2026-10-14 10:25:59', '2026-10-14 10:25:59');
INSERT INTO `wish_env_config` (`id`, `env_code`, `category`, `name`, `description`, `priority`, `visual`, `is_active`, `created_at`, `updated_at`) VALUES (3113, 'LATE_NIGHT', 'TIME', '深夜', '万籁俱寂（00-06 时）', 10, '{"skyColor": "#060b18"}', 1, '2026-10-14 10:25:59', '2026-10-14 10:25:59');
INSERT INTO `wish_env_config` (`id`, `env_code`, `category`, `name`, `description`, `priority`, `visual`, `is_active`, `created_at`, `updated_at`) VALUES (3114, 'METEOR_SHOWER', 'SPECIAL_EVENT', '流星雨', '全站流星划过树冠，愿望随星而落', 100, '{"particle": "METEOR", "skyColor": "#0c1b3a", "lightCoreColor": "#ffd700"}', 1, '2026-10-14 10:25:59', '2026-10-14 10:25:59');
INSERT INTO `wish_env_config` (`id`, `env_code`, `category`, `name`, `description`, `priority`, `visual`, `is_active`, `created_at`, `updated_at`) VALUES (3115, 'AURORA', 'SPECIAL_EVENT', '极光', '极光绸缎萦绕世界树', 100, '{"particle": "AURORA", "skyColor": "#0a1f2e", "lightCoreColor": "#7ef0c0"}', 1, '2026-10-14 10:25:59', '2026-10-14 10:25:59');
INSERT INTO `wish_env_config` (`id`, `env_code`, `category`, `name`, `description`, `priority`, `visual`, `is_active`, `created_at`, `updated_at`) VALUES (3116, 'STAR_NIGHT', 'SPECIAL_EVENT', '星辰夜', '满天星辰为愿望加冕', 100, '{"particle": "STAR", "skyColor": "#0c1b3a", "lightCoreColor": "#ffd700"}', 1, '2026-10-14 10:25:59', '2026-10-14 10:25:59');

-- Required catalog/configuration: wish_gift
INSERT INTO `wish_gift` (`id`, `name`, `icon_url`, `animation_url`, `price_starlight`, `status`, `sort`, `description`, `created_at`, `updated_at`, `deleted_at`) VALUES (1949000000000000001, '小星星', NULL, NULL, 1, 'ON_SHELF', 1, '轻轻点亮一份心意', '2026-10-14 10:26:01', '2026-10-14 10:26:01', NULL);
INSERT INTO `wish_gift` (`id`, `name`, `icon_url`, `animation_url`, `price_starlight`, `status`, `sort`, `description`, `created_at`, `updated_at`, `deleted_at`) VALUES (1949000000000000002, '爱心', NULL, NULL, 5, 'ON_SHELF', 2, '为TA送上一个爱心', '2026-10-14 10:26:01', '2026-10-14 10:26:01', NULL);
INSERT INTO `wish_gift` (`id`, `name`, `icon_url`, `animation_url`, `price_starlight`, `status`, `sort`, `description`, `created_at`, `updated_at`, `deleted_at`) VALUES (1949000000000000003, '咖啡', NULL, NULL, 10, 'ON_SHELF', 3, '请TA喝一杯热咖啡', '2026-10-14 10:26:01', '2026-10-14 10:26:01', NULL);
INSERT INTO `wish_gift` (`id`, `name`, `icon_url`, `animation_url`, `price_starlight`, `status`, `sort`, `description`, `created_at`, `updated_at`, `deleted_at`) VALUES (1949000000000000004, '香槟', NULL, NULL, 30, 'ON_SHELF', 4, '值得庆祝的时刻', '2026-10-14 10:26:01', '2026-10-14 10:26:01', NULL);
INSERT INTO `wish_gift` (`id`, `name`, `icon_url`, `animation_url`, `price_starlight`, `status`, `sort`, `description`, `created_at`, `updated_at`, `deleted_at`) VALUES (1949000000000000005, '皇冠', NULL, NULL, 88, 'ON_SHELF', 5, '尊贵的心意', '2026-10-14 10:26:01', '2026-10-14 10:26:01', NULL);
INSERT INTO `wish_gift` (`id`, `name`, `icon_url`, `animation_url`, `price_starlight`, `status`, `sort`, `description`, `created_at`, `updated_at`, `deleted_at`) VALUES (1949000000000000006, '火箭', NULL, NULL, 199, 'ON_SHELF', 6, '一飞冲天的应援', '2026-10-14 10:26:01', '2026-10-14 10:26:01', NULL);

-- Required catalog/configuration: wish_leaderboard_config
INSERT INTO `wish_leaderboard_config` (`id`, `config_key`, `config_value`, `description`, `updated_by`, `created_at`, `updated_at`) VALUES (1, 'lb.refresh_minutes', '10', '榜单刷新周期(分钟,ZSet 全量重建)', NULL, '2026-10-14 10:26:00', '2026-10-14 10:26:00');
INSERT INTO `wish_leaderboard_config` (`id`, `config_key`, `config_value`, `description`, `updated_by`, `created_at`, `updated_at`) VALUES (2, 'lb.top_size', '100', '每榜单保留 Top N', NULL, '2026-10-14 10:26:00', '2026-10-14 10:26:00');
INSERT INTO `wish_leaderboard_config` (`id`, `config_key`, `config_value`, `description`, `updated_by`, `created_at`, `updated_at`) VALUES (3, 'lb.tiebreak', 'CREATED_AT_ASC', '同分处理: CREATED_AT_ASC(早在前)/CREATED_AT_DESC', NULL, '2026-10-14 10:26:00', '2026-10-14 10:26:00');
INSERT INTO `wish_leaderboard_config` (`id`, `config_key`, `config_value`, `description`, `updated_by`, `created_at`, `updated_at`) VALUES (4, 'lb.exclude_restricted', '1', '是否排除风控受限用户(1=排除,安全验收:不展示被封禁用户)', NULL, '2026-10-14 10:26:00', '2026-10-14 10:26:00');

-- Required catalog/configuration: wish_match_config
INSERT INTO `wish_match_config` (`id`, `config_key`, `config_value`, `description`, `updated_by`, `created_at`, `updated_at`) VALUES (1, 'match.weight_keyword', '0.4', '匹配权重-关键词(0-1,与城市/活跃度权重和建议为1,超和时按比例归一)', NULL, '2026-10-14 10:26:00', '2026-10-14 10:26:00');
INSERT INTO `wish_match_config` (`id`, `config_key`, `config_value`, `description`, `updated_by`, `created_at`, `updated_at`) VALUES (2, 'match.weight_city', '0.3', '匹配权重-城市/geohash同城(0-1)', NULL, '2026-10-14 10:26:00', '2026-10-14 10:26:00');
INSERT INTO `wish_match_config` (`id`, `config_key`, `config_value`, `description`, `updated_by`, `created_at`, `updated_at`) VALUES (3, 'match.weight_activity', '0.3', '匹配权重-小组成员活跃度(0-1)', NULL, '2026-10-14 10:26:00', '2026-10-14 10:26:00');
INSERT INTO `wish_match_config` (`id`, `config_key`, `config_value`, `description`, `updated_by`, `created_at`, `updated_at`) VALUES (4, 'match.score_threshold', '0.15', '推荐相似度阈值(0-1,低于阈值不推荐;精确关键词命中不受限)', NULL, '2026-10-14 10:26:00', '2026-10-14 10:26:00');
INSERT INTO `wish_match_config` (`id`, `config_key`, `config_value`, `description`, `updated_by`, `created_at`, `updated_at`) VALUES (5, 'match.remind_idle_days', '3', '互相提醒-组员多少天未活跃视为需提醒(天)', NULL, '2026-10-14 10:26:00', '2026-10-14 10:26:00');
INSERT INTO `wish_match_config` (`id`, `config_key`, `config_value`, `description`, `updated_by`, `created_at`, `updated_at`) VALUES (6, 'match.remind_daily_limit', '3', '互相提醒-每用户每日提醒上限(条)', NULL, '2026-10-14 10:26:00', '2026-10-14 10:26:00');
INSERT INTO `wish_match_config` (`id`, `config_key`, `config_value`, `description`, `updated_by`, `created_at`, `updated_at`) VALUES (7, 'match.create_daily_limit', '3', '建组-每用户每日建组上限(个)', NULL, '2026-10-14 10:26:00', '2026-10-14 10:26:00');
