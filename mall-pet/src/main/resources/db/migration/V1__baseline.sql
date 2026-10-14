-- C26 clean development/test baseline. Recreated EMPTY databases only.
-- Materialized from verified MySQL schema; never repair an old history to adopt this file.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE `pet` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `user_id` bigint unsigned NOT NULL COMMENT '主人用户ID(网关X-User-Id)',
  `name` varchar(60) NOT NULL COMMENT '宠物名(1-12字符,30天可改一次)',
  `species` enum('STRAWBERRY','ORANGE','WATERMELON','BLUEBERRY','DRAGONFRUIT') NOT NULL COMMENT '种类:草莓/橘子/西瓜/蓝莓/火龙果',
  `gender` enum('MALE','FEMALE') NOT NULL DEFAULT 'MALE' COMMENT '性别:MALE男/FEMALE女(领养时选择,存量默认男)',
  `appearance` varchar(255) NOT NULL DEFAULT '{}' COMMENT '外观JSON:{"color":"orange","accessory":"bell"}',
  `base_appearance` varchar(255) DEFAULT NULL COMMENT '原始自定义外观JSON(穿戴皮肤前保存;卸皮肤恢复;NULL=从无自定义外观,采用物种默认)',
  `skin_code` varchar(60) DEFAULT NULL COMMENT '当前穿戴皮肤编码(pet_skin_config.code, NULL=原生外观)',
  `personality` enum('LIVELY','GENTLE','TSUNDERE','SIMPLE','COOL','CHATTERBOX') NOT NULL COMMENT '性格(决定AI说话风格)',
  `owner_title` varchar(16) NOT NULL DEFAULT '主人' COMMENT '主人称呼(宠物对主人的叫法,1~12字,默认主人)',
  `career_code` varchar(60) DEFAULT NULL COMMENT '当前职业编码(pet_career_config.code, NULL=未入职)',
  `level` int NOT NULL DEFAULT '1' COMMENT '等级(1起)',
  `exp` int NOT NULL DEFAULT '0' COMMENT '当前等级内经验',
  `growth_stage` enum('BABY','YOUNG','ADULT') NOT NULL DEFAULT 'BABY' COMMENT '成长阶段:幼年/成长/成年',
  `evolution_stage` tinyint NOT NULL DEFAULT '0' COMMENT '进化阶段(0未进化/1一阶/2二阶; 由pet_evolution_config驱动, 影响属性上限与皮肤解锁)',
  `hp` int NOT NULL DEFAULT '100' COMMENT '生命值',
  `max_hp` int NOT NULL DEFAULT '100' COMMENT '生命上限(升级+5)',
  `hunger` int NOT NULL DEFAULT '80' COMMENT '饥饿度0-100(越高越饱)',
  `happiness` int NOT NULL DEFAULT '80' COMMENT '心情0-100',
  `energy` int NOT NULL DEFAULT '100' COMMENT '精力0-100',
  `cleanliness` int NOT NULL DEFAULT '90' COMMENT '清洁度0-100',
  `hunger_frac` decimal(10,4) NOT NULL DEFAULT '0.0000' COMMENT '饱食变化小数余量(独立累计,不吞步长)',
  `hunger_zero_since` datetime DEFAULT NULL COMMENT '饥饿归零起点(UTC)：hunger>0 置 NULL，=0 首次记录，用于 WEAK 判定',
  `happiness_zero_since` datetime DEFAULT NULL COMMENT '心情归零起点(UTC)：happiness>0 置 NULL，=0 首次记录，用于 SICK 判定',
  `happiness_frac` decimal(10,4) NOT NULL DEFAULT '0.0000' COMMENT '心情变化小数余量(独立累计)',
  `energy_frac` decimal(10,4) NOT NULL DEFAULT '0.0000' COMMENT '精力变化小数余量(独立累计)',
  `cleanliness_frac` decimal(10,4) NOT NULL DEFAULT '0.0000' COMMENT '清洁变化小数余量(独立累计)',
  `memory_extract_enabled` tinyint NOT NULL DEFAULT '1' COMMENT '自动记忆提取开关(N03)',
  `strength` int NOT NULL DEFAULT '5' COMMENT '力量(战斗伤害)',
  `intelligence` int NOT NULL DEFAULT '5' COMMENT '智力(读书/学习收益)',
  `agility` int NOT NULL DEFAULT '5' COMMENT '敏捷(战斗先手/捞瓶成功率)',
  `charm` int NOT NULL DEFAULT '5' COMMENT '魅力(暴击率加成)',
  `intimacy` int NOT NULL DEFAULT '0' COMMENT '与主人的亲密度(0起,阈值见 PetProperties.Intimacy)',
  `companion_seconds` bigint NOT NULL DEFAULT '0' COMMENT '累计陪伴时长(秒,心跳累加)',
  `companion_days` int NOT NULL DEFAULT '0' COMMENT '累计陪伴天数(去重日期数)',
  `companion_streak` int NOT NULL DEFAULT '0' COMMENT '连续陪伴天数(断签重置)',
  `last_companion_date` date DEFAULT NULL COMMENT '最近一次陪伴日期(UTC,连续天数判定)',
  `today_companion_seconds` int NOT NULL DEFAULT '0' COMMENT '今日陪伴秒数(跨天惰性重置,日上限见配置)',
  `status` enum('IDLE','WORKING','STUDYING','FISHING','RESTING') NOT NULL DEFAULT 'IDLE' COMMENT '状态快照(权威状态由pet_activity合成)',
  `is_public` tinyint NOT NULL DEFAULT '1' COMMENT '是否在个人主页公开(1公开/0隐私)',
  `is_active` tinyint NOT NULL DEFAULT '1' COMMENT '是否当前主宠(1主宠/0备选; 每用户至多一只主宠)',
  `last_state_update_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '懒更新游标:上次状态自然变化结算时间(UTC)',
  `last_renamed_at` datetime DEFAULT NULL COMMENT '最近一次改名时间(30天冷却,UTC)',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  `deleted_at` datetime DEFAULT NULL COMMENT '软删除时间(放生/注销清理)',
  `memory_use_enabled` tinyint NOT NULL DEFAULT '1' COMMENT '记忆注入上下文开关(N03/B18)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pet_user_active` (`user_id`,(if(((`is_active` = 1) and (`deleted_at` is null)),1,NULL))),
  KEY `idx_pet_level` (`level`),
  KEY `idx_pet_public` (`is_public`,`level`),
  KEY `idx_pet_user` (`user_id`),
  KEY `idx_pet_career` (`career_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物主表(一期一用户一宠)';

CREATE TABLE `pet_achievement` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `code` varchar(60) NOT NULL COMMENT '唯一编码(如FIRST_BOTTLE)',
  `name` varchar(60) NOT NULL COMMENT '成就名',
  `description` varchar(255) NOT NULL DEFAULT '' COMMENT '达成条件描述',
  `icon` varchar(30) NOT NULL DEFAULT 0xF09F8F86 COMMENT '图标(emoji或URL)',
  `condition_type` enum('BOTTLE_COUNT','BATTLE_WIN','LEVEL','CHAT_COUNT','STATS_FULL','ACTIVITY_COUNT','VISIT_COUNT','EVOLUTION','INTIMACY','RELATION_COUNT','FRIEND_COUNT','WALL_MESSAGE_COUNT','ROOM_COMFORT','QUEST_COUNT','COMPANION_HOURS') NOT NULL COMMENT '判定类型:捞瓶数/胜场/等级/聊天句数/满属性/行为计数/串门数/进化阶数/亲密度/关系数/好友数/留言数/房间舒适度/每日任务数/陪伴小时数',
  `condition_subtype` varchar(30) DEFAULT NULL COMMENT '计数子类型(ACTIVITY_COUNT时: WORK/STUDY/FEED/CLEAN)',
  `condition_value` int NOT NULL COMMENT '判定阈值',
  `exp_reward` int NOT NULL DEFAULT '0' COMMENT '达成奖励经验',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用(1启用/0停用)',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序(升序)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_achievement_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物成就定义';

CREATE TABLE `pet_achievement_record` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `pet_id` bigint unsigned NOT NULL COMMENT '宠物ID',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `achievement_id` bigint unsigned NOT NULL COMMENT '成就ID',
  `achieved_at` datetime NOT NULL COMMENT '达成时间(UTC)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pet_ach_record` (`pet_id`,`achievement_id`),
  KEY `idx_ach_record_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物成就达成记录(uk幂等,同一成就一只宠物只发一次)';

CREATE TABLE `pet_activity` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `pet_id` bigint unsigned NOT NULL COMMENT '宠物ID',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `activity_type` enum('WORK','STUDY','BOTTLE_FISHING','REST','FEED','PLAY','CLEAN','VISIT','EVOLVE','CAREER_WORK') NOT NULL COMMENT '活动类型:打工/读书/捞瓶/休息/喂食/玩耍/清洁/串门/进化/职业打工(即时行为直接CLAIMED)',
  `config_id` bigint unsigned DEFAULT NULL COMMENT '关联配置ID(WORK→pet_job_config/STUDY→pet_study_config,捞瓶休息为空)',
  `status` enum('IN_PROGRESS','COMPLETED','CLAIMED','EXPIRED') NOT NULL COMMENT '状态机:进行中/已完成/已领取/已过期',
  `started_at` datetime NOT NULL COMMENT '开始时间(UTC)',
  `finished_at` datetime NOT NULL COMMENT '预计完成时间(UTC,started_at+duration)',
  `claimed_at` datetime DEFAULT NULL COMMENT '领取时间(幂等标记)',
  `result` json DEFAULT NULL COMMENT '结果JSON(奖励明细/捞瓶outcome与bottleId)',
  `snapshot` json DEFAULT NULL COMMENT '规则快照(开始时冻结:名称/消耗/时长/基础奖励/加成依据/配置版本)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_activity_user_active_v2` (`user_id`,(if((`status` = _utf8mb4'IN_PROGRESS'),1,NULL))),
  KEY `idx_activity_pet` (`pet_id`),
  KEY `idx_activity_finished` (`status`,`finished_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物统一活动表(打工/读书/捞瓶/休息共用状态机)';

CREATE TABLE `pet_album_asset` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `user_id` bigint unsigned NOT NULL COMMENT '上传者(=宠物主人,归属校验)',
  `pet_id` bigint unsigned NOT NULL COMMENT '关联宠物ID',
  `diary_entry_id` bigint unsigned DEFAULT NULL COMMENT '关联日记ID(可空)',
  `file_id` varchar(64) NOT NULL COMMENT 'mall-file 资源 ID(授权可访问引用)',
  `audit_status` enum('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'APPROVED' COMMENT '审核状态',
  `bind_status` varchar(20) DEFAULT NULL COMMENT '文件引用绑定状态: BINDING/BOUND/FAILED（存量行迁移后回填 BOUND）',
  `bind_attempts` int NOT NULL DEFAULT '0' COMMENT '绑定已尝试次数（上传 1 次 + 自动/手动重试）',
  `next_bind_retry_at` datetime DEFAULT NULL COMMENT '下次自动重试时间(UTC)；NULL 表示无待重试',
  `last_bind_error` varchar(255) DEFAULT NULL COMMENT '最近一次绑定失败原因（截断 255 字符）',
  `caption` varchar(200) DEFAULT NULL COMMENT '照片说明（PATCH 可改）',
  `visibility` varchar(20) NOT NULL DEFAULT 'OWNER_ONLY' COMMENT '可见性：OWNER_ONLY/PUBLIC（访客仅见 PUBLIC+APPROVED+BOUND）',
  `version` int NOT NULL DEFAULT '1' COMMENT '乐观版本（PATCH CAS）',
  `reviewer_id` bigint unsigned DEFAULT NULL COMMENT '审核处理人（adminUserId）',
  `review_reason` varchar(255) DEFAULT NULL COMMENT '审核理由（驳回必填）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_album_file` (`file_id`),
  KEY `idx_album_pet` (`pet_id`,`created_at`),
  KEY `idx_album_bind_retry` (`bind_status`,`next_bind_retry_at`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物相册(仅主人可见,静态图片,5MB/100张限额集中配置)';

CREATE TABLE `pet_asset_grant` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `pet_id` bigint unsigned DEFAULT NULL COMMENT '宠物ID(可空)',
  `source_type` varchar(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '来源类型:ORDER(购买)/CLAIM(领奖)/ONBOARDING等',
  `source_id` bigint unsigned NOT NULL COMMENT '来源单ID(订单ID/claimID)',
  `reward_slot` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '奖励槽位(同来源同槽唯一,防重复发放)',
  `item_type` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '物品类型',
  `item_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '物品编码',
  `quantity` int unsigned NOT NULL DEFAULT '1' COMMENT '数量',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC)',
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pet_asset_grant_fact` (`source_type`,`source_id`,`user_id`,`reward_slot`),
  KEY `idx_pet_asset_grant_user` (`user_id`,`item_type`,`item_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资产发放记录(哪一单发了哪个资产,不以库存存在替代)';

CREATE TABLE `pet_battle` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `mode` enum('PVE','PVP') NOT NULL COMMENT '模式:PvE野生宠物立即结算/PvP异步快照应战',
  `attacker_pet_id` bigint unsigned NOT NULL COMMENT '挑战方宠物ID',
  `attacker_user_id` bigint unsigned NOT NULL COMMENT '挑战方用户ID',
  `defender_pet_id` bigint unsigned NOT NULL COMMENT '防守方宠物ID(PvE为模板生成ID=0)',
  `defender_user_id` bigint unsigned DEFAULT NULL COMMENT '防守方用户ID(PvE为空)',
  `status` enum('PENDING','FINISHED','DECLINED','EXPIRED') NOT NULL COMMENT '状态:待应战/已结束/已拒绝/已过期',
  `seed` bigint NOT NULL COMMENT '战斗随机种子(结算前生成,有符号与Java long一致,保证离线复现)',
  `winner_pet_id` bigint unsigned DEFAULT NULL COMMENT '胜者宠物ID(平局/未结算为空)',
  `attacker_snapshot` json NOT NULL COMMENT '挑战方宠物属性快照',
  `defender_snapshot` json NOT NULL COMMENT '防守方宠物属性快照(PvE为野生宠物模板)',
  `rounds` json DEFAULT NULL COMMENT '回合流水JSON:[{round,actor,action,damage,critical,dodge,remainingHp}]',
  `exp_reward` int NOT NULL DEFAULT '0' COMMENT '经验奖励(结算后双方各自发放)',
  `currency_reward` int NOT NULL DEFAULT '0' COMMENT '星光奖励(仅胜者,经mall-wish发放)',
  `started_at` datetime NOT NULL COMMENT '发起时间(UTC)',
  `finished_at` datetime DEFAULT NULL COMMENT '结算时间(UTC)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  KEY `idx_battle_attacker` (`attacker_user_id`,`created_at`),
  KEY `idx_battle_defender` (`defender_user_id`,`status`),
  KEY `idx_battle_attacker_pet` (`attacker_pet_id`),
  KEY `idx_pet_battle_status_winner` (`status`,`winner_pet_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物对战记录(异步回合制,服务端全权计算)';

CREATE TABLE `pet_bottle_record` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `pet_id` bigint unsigned NOT NULL COMMENT '宠物ID',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `activity_id` bigint unsigned NOT NULL COMMENT '关联pet_activity.id(BOTTLE_FISHING任务)',
  `bottle_id` bigint unsigned DEFAULT NULL COMMENT '捞到的漂流瓶ID(wish_drift_bottle.id,EMPTY/FAILED为空)',
  `outcome` enum('CAUGHT','EMPTY','FAILED') NOT NULL COMMENT '结果:捞到/空手而归/服务降级可重试',
  `rarity` enum('NORMAL','RARE','PET','EASTER_EGG') NOT NULL DEFAULT 'NORMAL' COMMENT '瓶子稀有度:普通/稀有/宠物瓶/彩蛋瓶(特殊瓶不消耗mall-wish瓶子池)',
  `special_content` varchar(500) DEFAULT NULL COMMENT '特殊瓶子内容文本(稀有瓶/宠物瓶/彩蛋瓶专属,普通瓶为空)',
  `success_rate` decimal(5,4) DEFAULT NULL COMMENT '本次成功率快照0-1(便于回放与调参)',
  `started_at` datetime NOT NULL COMMENT '开始时间(UTC)',
  `finished_at` datetime NOT NULL COMMENT '完成时间(UTC)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bottle_record_activity` (`activity_id`),
  KEY `idx_bottle_record_user` (`user_id`,`created_at`),
  KEY `idx_bottle_record_rarity` (`pet_id`,`rarity`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物捞漂流瓶流水(bottle_id指向mall-wish,不建第二套漂流瓶)';

CREATE TABLE `pet_career_config` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `code` varchar(60) NOT NULL COMMENT '唯一编码',
  `name` varchar(60) NOT NULL COMMENT '职业名',
  `description` varchar(255) NOT NULL DEFAULT '' COMMENT '职业描述',
  `career_line` varchar(40) NOT NULL COMMENT '职业路线（同线内逐阶晋升）',
  `tier` tinyint NOT NULL DEFAULT '1' COMMENT '阶段(1初级/2中级/3高级)',
  `icon` varchar(30) NOT NULL DEFAULT 0xF09F92BC COMMENT '图标(emoji或URL)',
  `required_level` int NOT NULL DEFAULT '1' COMMENT '入职最低等级',
  `required_intelligence` int NOT NULL DEFAULT '0' COMMENT '入职最低智力',
  `duration_seconds` int NOT NULL COMMENT '单次工作耗时(秒)',
  `energy_cost` int NOT NULL DEFAULT '0' COMMENT '精力消耗',
  `hunger_cost` int NOT NULL DEFAULT '0' COMMENT '饥饿消耗',
  `exp_reward` int NOT NULL DEFAULT '0' COMMENT '基础经验奖励',
  `currency_reward` int NOT NULL DEFAULT '0' COMMENT '基础星光奖励',
  `promote_to_code` varchar(60) DEFAULT NULL COMMENT '晋升目标职业(NULL=已是最高阶)',
  `promote_required_count` int NOT NULL DEFAULT '0' COMMENT '晋升所需本职业工作次数',
  `promote_star_cost` int NOT NULL DEFAULT '0' COMMENT '晋升消耗星光',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否开放(1开放/0停招)',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序(升序)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pet_career_code` (`code`),
  KEY `idx_pet_career_line` (`career_line`,`tier`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物职业配置(路线 × 阶段)';

CREATE TABLE `pet_career_progress` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `pet_id` bigint unsigned NOT NULL COMMENT '宠物ID',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID(查询冗余)',
  `career_code` varchar(60) NOT NULL COMMENT '职业编码(pet_career_config.code)',
  `work_count` int NOT NULL DEFAULT '0' COMMENT '本职业累计工作次数',
  `total_currency` int NOT NULL DEFAULT '0' COMMENT '本职业累计星光收入',
  `started_at` datetime NOT NULL COMMENT '入职时间(UTC)',
  `promoted_at` datetime DEFAULT NULL COMMENT '晋升离开本职业时间(UTC,NULL=在职)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pet_career_progress` (`pet_id`,`career_code`),
  KEY `idx_pet_career_progress_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物职业进度(uk幂等,重复入职不覆盖历史)';

CREATE TABLE `pet_career_stint` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `pet_id` bigint unsigned NOT NULL COMMENT '宠物ID',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `career_code` varchar(60) NOT NULL COMMENT '职业编码(pet_career_config.code)',
  `started_at` datetime NOT NULL COMMENT '入职时间(UTC)',
  `ended_at` datetime DEFAULT NULL COMMENT '结束时间(UTC,开放段为空)',
  `end_reason` enum('PROMOTED','LEFT') DEFAULT NULL COMMENT '结束原因:晋升离开/主动转职',
  `work_count` int NOT NULL DEFAULT '0' COMMENT '该段累计工作次数(晋升判断依据)',
  `total_currency` int NOT NULL DEFAULT '0' COMMENT '该段累计星光收入',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_career_stint_open` (`pet_id`,`career_code`,(if((`ended_at` is null),1,NULL))),
  KEY `idx_career_stint_pet` (`pet_id`,`started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='职业任职记录(保留入职/离职时间与该段收益,晋升历史不清除)';

CREATE TABLE `pet_chat_message` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `session_id` bigint unsigned NOT NULL COMMENT '会话ID',
  `role` enum('USER','PET','SYSTEM') NOT NULL COMMENT '角色:用户/宠物/系统',
  `content` varchar(2000) NOT NULL COMMENT '消息内容',
  `token_count` int NOT NULL DEFAULT '0' COMMENT '估算token数(成本观测)',
  `is_ai_reply` tinyint NOT NULL DEFAULT '0' COMMENT '是否AI生成(0=模板降级/固定行为)',
  `request_id` varchar(80) DEFAULT NULL COMMENT '客户端请求幂等键(Idempotency-Key,同键重试返回既有回复)',
  `lease_owner` varchar(64) DEFAULT NULL COMMENT '重执行租约持有者（执行者唯一标识）',
  `lease_until` datetime DEFAULT NULL COMMENT '租约到期时间(UTC)；到期可被其他执行者 CAS 抢占',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chat_request_role` (`session_id`,`request_id`,`role`),
  KEY `idx_chat_message_session` (`session_id`,`created_at`),
  KEY `idx_chat_message_lease` (`request_id`,`lease_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物聊天消息(上下文窗口只取最近N条,长期信息沉淀pet_memory)';

CREATE TABLE `pet_chat_session` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `pet_id` bigint unsigned NOT NULL DEFAULT '0' COMMENT '会话归属宠物ID(0=legacy shared 历史共享会话,仅主人可见)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pet_chat_session_user_pet` (`user_id`,`pet_id`),
  KEY `idx_chat_session_pet` (`pet_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物聊天会话(一人一宠一会话)';

CREATE TABLE `pet_collection_entry` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `category` enum('SPECIES','SKIN','EQUIPMENT','FURNITURE','BOTTLE','DECOR') NOT NULL COMMENT '类别',
  `item_code` varchar(60) NOT NULL COMMENT '条目编码',
  `resource_key` varchar(120) DEFAULT NULL COMMENT '资源标识(未制作返回占位)',
  `rarity` enum('COMMON','RARE','EPIC','LEGENDARY') NOT NULL DEFAULT 'COMMON' COMMENT '稀有度',
  `unlock_condition` varchar(200) NOT NULL COMMENT '解锁条件描述(未解锁返回线索)',
  `hidden` tinyint NOT NULL DEFAULT '0' COMMENT '隐藏彩蛋(未解锁不泄漏完整正文)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_collection_entry` (`category`,`item_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收藏图鉴配置(运营维护)';

CREATE TABLE `pet_collection_record` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID(图鉴按用户累计,多宠共享)',
  `entry_code` varchar(120) NOT NULL COMMENT '条目(category:item_code)',
  `first_pet_id` bigint unsigned NOT NULL COMMENT '首次获得的宠物',
  `first_event_id` varchar(120) DEFAULT NULL COMMENT '首次获得事件',
  `acquired_at` datetime NOT NULL COMMENT '首次获得时间(UTC)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_collection_record` (`user_id`,`entry_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收藏记录(重复获得仅解锁一次)';

CREATE TABLE `pet_companion_daily` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID(多端/多宠共享一份时间)',
  `business_date` date NOT NULL COMMENT '业务日(businessZone=Asia/Shanghai)',
  `accepted_seconds` int NOT NULL DEFAULT '0' COMMENT '当日有效陪伴秒数(服务端会话校验后累计,计入上限7200)',
  `granted_points` int NOT NULL DEFAULT '0' COMMENT '当日已发亲密度积分(上限8)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_companion_daily` (`user_id`,`business_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='陪伴业务日累计(同事务保存时长/积分/已发计数,跨日拆分)';

CREATE TABLE `pet_companion_daily_pet` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `user_id` bigint unsigned NOT NULL COMMENT '用户 ID',
  `pet_id` bigint unsigned NOT NULL COMMENT '宠物 ID（会话归属宠）',
  `business_date` date NOT NULL COMMENT '业务日(Asia/Shanghai 00:00 重置)',
  `accepted_seconds` int NOT NULL DEFAULT '0' COMMENT '本宠当日实际计入有效秒数（受账号日上限截断）',
  `granted_minutes` int NOT NULL DEFAULT '0' COMMENT '本宠当日已产生的陪伴任务分钟数(floor 差值)',
  `qualified_day` tinyint NOT NULL DEFAULT '0' COMMENT '是否有效陪伴日（达到 qualifiedDayThresholdSeconds 才计连续天数）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_companion_daily_pet` (`pet_id`,`business_date`),
  KEY `idx_companion_daily_pet_user` (`user_id`,`business_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='陪伴按宠物日分账(R36)';

CREATE TABLE `pet_companion_session` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `pet_id` bigint unsigned NOT NULL COMMENT '陪伴的宠物ID',
  `status` enum('ACTIVE','ENDED') NOT NULL DEFAULT 'ACTIVE' COMMENT '状态:有效/已结束(失效/停止/被新会话接管)',
  `started_at` datetime NOT NULL COMMENT '会话开始时间(UTC,服务端基准)',
  `last_heartbeat_at` datetime NOT NULL COMMENT '最近有效心跳时间(UTC,计时游标)',
  `last_seq` bigint NOT NULL DEFAULT '0' COMMENT '最近已接受的心跳序号(客户端单调递增,重放去重)',
  `ended_at` datetime DEFAULT NULL COMMENT '结束时间(UTC)',
  `end_reason` enum('STOPPED','EXPIRED','SUPERSEDED') DEFAULT NULL COMMENT '结束原因:正常停止/心跳超时失效/被新会话替换',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_companion_session_active` (`user_id`,(if((`status` = _utf8mb4'ACTIVE'),1,NULL))),
  KEY `idx_companion_session_pet` (`pet_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='陪伴会话(服务端计时权威,前端秒数仅参考)';

CREATE TABLE `pet_config_version` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `config_type` varchar(40) NOT NULL COMMENT '配置类型: job/study/career/furniture/equipment/skin/skill/evolution/event/daily_quest',
  `config_id` bigint unsigned NOT NULL COMMENT '配置行ID',
  `version` int NOT NULL COMMENT '版本号(同类型同配置递增)',
  `snapshot` json NOT NULL COMMENT '整行快照(回退依据)',
  `operation` enum('PUBLISH','ROLLBACK') NOT NULL COMMENT '操作类型',
  `operator` varchar(60) DEFAULT NULL COMMENT '操作管理员',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pet_config_version` (`config_type`,`config_id`,`version`),
  KEY `idx_config_version` (`config_type`,`config_id`,`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='配置历史版本(发布快照/回退/审计)';

CREATE TABLE `pet_content_sensitive_word` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `word` varchar(64) NOT NULL COMMENT '敏感词（匹配时忽略大小写）',
  `category` enum('POLITICS','ABUSE','AD','CRISIS') NOT NULL COMMENT '类别：POLITICS政治/ABUSE辱骂/AD广告导流/CRISIS危机干预',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1启用 0停用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,毫秒)：词库热更新指纹依据，禁止业务写入',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_word` (`word`),
  KEY `idx_pet_sensitive_word_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物内容安全敏感词库(内存自动机热加载)';

CREATE TABLE `pet_context_counter` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `new_comments` int NOT NULL DEFAULT '0' COMMENT '未播报新评论数(聚合清零制)',
  `new_likes` int NOT NULL DEFAULT '0' COMMENT '未播报新点赞数',
  `new_follows` int NOT NULL DEFAULT '0' COMMENT '未播报新关注数',
  `new_collects` int NOT NULL DEFAULT '0' COMMENT '未播报新收藏数',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pet_context_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='社区事件计数器(宠物AI上下文白名单数据,播报后清零)';

CREATE TABLE `pet_cooperation` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `week_start` date NOT NULL COMMENT '自然周起始(周一,北京时间)',
  `inviter_user_id` bigint unsigned NOT NULL COMMENT '邀请人',
  `invitee_user_id` bigint unsigned DEFAULT NULL COMMENT '受邀人(待接受为空)',
  `status` enum('INVITED','ACTIVE','COMPLETED','ENDED','REJECTED','EXPIRED') NOT NULL DEFAULT 'INVITED' COMMENT '状态机:INVITED待接受/ACTIVE进行中/COMPLETED已达成/ENDED已退出/REJECTED已拒绝/EXPIRED已过期',
  `inviter_pet_id` bigint unsigned NOT NULL COMMENT '邀请人绑定宠物(当周)',
  `invitee_pet_id` bigint unsigned DEFAULT NULL COMMENT '受邀人绑定宠物(接受时)',
  `invite_expires_at` datetime NOT NULL COMMENT '邀请过期时间(24h且不超本周结束)',
  `accepted_at` datetime DEFAULT NULL COMMENT '接受时间(UTC)',
  `contributions` json NOT NULL COMMENT '贡献: {inviter:n, invitee:n}(各3次有效照顾,每人每天1次)',
  `ended_at` datetime DEFAULT NULL COMMENT '结束时间(UTC)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cooperation_inviter` (`inviter_user_id`,`week_start`),
  UNIQUE KEY `uk_cooperation_invitee` (`invitee_user_id`,`week_start`),
  KEY `idx_cooperation_week` (`week_start`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='好友合作周任务(双方贡献/周名额/个人奖励)';

CREATE TABLE `pet_cooperation_contribution` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `cooperation_id` bigint unsigned NOT NULL COMMENT '合作实例ID',
  `user_id` bigint unsigned NOT NULL COMMENT '贡献用户',
  `event_id` varchar(120) NOT NULL COMMENT '照顾事件唯一键(去重,重复事件不增贡献)',
  `business_date` date NOT NULL COMMENT '业务日(每人每天最多贡献1次)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_coop_contribution` (`event_id`),
  UNIQUE KEY `uk_coop_daily` (`cooperation_id`,`user_id`,`business_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='合作贡献去重(每事件一次/每日一次)';

CREATE TABLE `pet_cooperation_member` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `user_id` bigint unsigned NOT NULL COMMENT '成员用户 ID',
  `week_start` date NOT NULL COMMENT '自然周起始(周一,北京时间)',
  `cooperation_id` bigint unsigned NOT NULL COMMENT '所属合作实例 ID',
  `role` enum('INVITER','INVITEE') NOT NULL COMMENT '加入角色',
  `pet_id` bigint unsigned NOT NULL COMMENT '参与时绑定宠物',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cooperation_member_user_week` (`user_id`,`week_start`),
  KEY `idx_cooperation_member_cooperation` (`cooperation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='合作周成员名额(R35)';

CREATE TABLE `pet_custody_record` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `pet_id` bigint unsigned NOT NULL COMMENT '托管宠物ID(每次一只)',
  `week_start` date NOT NULL COMMENT '自然周起始(周一,北京时间)',
  `status` enum('ACTIVE','ENDED') NOT NULL DEFAULT 'ACTIVE' COMMENT '状态(不可叠加:每用户一条 ACTIVE)',
  `started_at` datetime NOT NULL COMMENT '开始时间(UTC)',
  `ends_at` datetime NOT NULL COMMENT '计划结束时间(UTC,最长24h)',
  `rule_snapshot` json NOT NULL COMMENT '规则快照(饱食<30→50 最多2次;清洁<30→50 最多1次)',
  `care_feed_used` int NOT NULL DEFAULT '0' COMMENT '已用喂食照顾次数',
  `care_clean_used` int NOT NULL DEFAULT '0' COMMENT '已用清洁照顾次数',
  `ended_at` datetime DEFAULT NULL COMMENT '实际结束时间(UTC)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_custody_week` (`user_id`,`week_start`),
  UNIQUE KEY `uk_custody_active` (`user_id`,(if((`status` = _utf8mb4'ACTIVE'),1,NULL)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='离线托管(有限照顾,不产出养成收益)';

CREATE TABLE `pet_daily_quest` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `pet_id` bigint unsigned NOT NULL COMMENT '宠物ID',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID(查询冗余)',
  `quest_date` date NOT NULL COMMENT '任务日期(UTC,自然日)',
  `set_id` bigint unsigned DEFAULT NULL COMMENT '所属任务集（PET-09：领取/展示按集绑定与归属校验）',
  `quest_code` varchar(60) NOT NULL COMMENT '任务编码(pet_daily_quest_config.code)',
  `progress` int NOT NULL DEFAULT '0' COMMENT '当前进度',
  `target_value` int NOT NULL COMMENT '目标值(生成时快照,配置改动不影响当日)',
  `reward_snapshot` varchar(1000) DEFAULT NULL COMMENT '生成时奖励快照 JSON（普通任务: name/questType/expReward/currencyReward/actionTarget；宝箱行: chestExp/chestCurrency）。NULL=存量行回退当前配置',
  `status` enum('IN_PROGRESS','COMPLETE','CLAIMED','EXPIRED','CANCELLED') NOT NULL COMMENT '状态机:进行中/已完成/已领取/已过期/已取消(停用配置显式取消,不阻挡宝箱)',
  `cancel_reason` varchar(200) DEFAULT NULL COMMENT '取消原因（管理端受审计命令必填）',
  `cancelled_by` varchar(64) DEFAULT NULL COMMENT '取消操作管理员（服务令牌 admin_username，P0-3）',
  `completed_at` datetime DEFAULT NULL COMMENT '完成时间(UTC)',
  `claimed_at` datetime DEFAULT NULL COMMENT '领奖时间(UTC,幂等标记)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pet_daily_quest` (`pet_id`,`quest_date`,`quest_code`),
  KEY `idx_pet_daily_quest_user` (`user_id`,`quest_date`),
  KEY `idx_pet_daily_quest_set` (`set_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物每日任务进度(进度由业务埋点累加,CAS领奖幂等)';

CREATE TABLE `pet_daily_quest_config` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `code` varchar(60) NOT NULL COMMENT '唯一编码',
  `name` varchar(60) NOT NULL COMMENT '任务名',
  `description` varchar(255) NOT NULL DEFAULT '' COMMENT '任务描述',
  `icon` varchar(30) NOT NULL DEFAULT 0xF09F938C COMMENT '图标(emoji或URL)',
  `quest_type` enum('FEED','PLAY','CLEAN','REST','WORK','STUDY','BOTTLE','BATTLE','VISIT','CHAT','CAREER_WORK','FRIEND_VISIT','WALL_MESSAGE','COMPANION','DECORATE') NOT NULL COMMENT '统计口径(与业务埋点一一对应)',
  `target_value` int NOT NULL COMMENT '目标次数',
  `exp_reward` int NOT NULL DEFAULT '0' COMMENT '奖励经验',
  `currency_reward` int NOT NULL DEFAULT '0' COMMENT '奖励星光',
  `required_level` int NOT NULL DEFAULT '1' COMMENT '宠物等级门槛',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用(1启用/0停用)',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序(升序)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pet_daily_quest_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物每日任务配置(每日按启用配置生成进度行)';

CREATE TABLE `pet_daily_quest_set` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned NOT NULL COMMENT '归属用户',
  `pet_id` bigint unsigned NOT NULL COMMENT '绑定宠物（集内任务归属固定，切换主宠不改变集归属）',
  `business_date` date NOT NULL COMMENT '业务日（Asia/Shanghai）',
  `timezone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '业务日时区（集生成时固定）',
  `level_snapshot` int NOT NULL DEFAULT '1' COMMENT '生成时宠物等级（当日升级不扩大既有集合，新配置下一集生效）',
  `generated_at` datetime NOT NULL COMMENT '集生成时间（UTC）',
  `claim_deadline` datetime NOT NULL COMMENT '领取截止（下一业务日结束，24h 宽限）',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/EXPIRED',
  `chest_snapshot` varchar(512) NOT NULL DEFAULT '{}' COMMENT '宝箱奖励快照（生成时冻结）',
  `chest_claimed_at` datetime DEFAULT NULL COMMENT '宝箱领取时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pet_quest_set_date` (`pet_id`,`business_date`),
  KEY `idx_pet_quest_set_user_date` (`user_id`,`business_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物每日任务集（PET-09：每宠每业务日生成一次的冻结任务集合）';

CREATE TABLE `pet_daily_quota` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `user_id` bigint unsigned NOT NULL COMMENT '主体用户ID(额度按用户共享,与主宠无关)',
  `quota_type` varchar(40) NOT NULL COMMENT '额度类型:FEED/PLAY_REWARD/REST_INTIMACY/BATTLE_REWARD/PVP_DAILY/WALL_POST/VISIT_REWARD/FRIEND_VISIT_REWARD/LIKE_REWARD/MINIGAME/DECORATE/DEFEAT_TARGET',
  `target_id` bigint unsigned NOT NULL DEFAULT '0' COMMENT '目标维度(0=用户级全局;非0=按目标细分的额度,如对战对手用户ID)',
  `business_date` date NOT NULL COMMENT '业务日(businessZone)',
  `used` int NOT NULL DEFAULT '0' COMMENT '已用次数(原子条件递增,不超过limit由服务层保证)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pet_daily_quota` (`user_id`,`quota_type`,`target_id`,`business_date`),
  KEY `idx_pet_daily_quota_user` (`user_id`,`business_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户每日收益额度(数据库权威,Redis故障不发奖)';

CREATE TABLE `pet_dashboard_daily_snapshot` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `stat_date` date NOT NULL COMMENT '统计日(UTC 自然日)',
  `metric_key` varchar(32) NOT NULL COMMENT '指标键：new_pets/active_pets/activities/wall_messages/visits/battles',
  `metric_value` bigint NOT NULL DEFAULT '0' COMMENT '指标值',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间(UTC)',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_snapshot_date_metric` (`stat_date`,`metric_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物看板每日快照(调度器小时级增量写入)';

CREATE TABLE `pet_diary_entry` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `pet_id` bigint unsigned NOT NULL COMMENT '宠物ID',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `event_id` varchar(120) NOT NULL COMMENT '业务事件唯一键(去重,禁止伪造)',
  `event_type` varchar(40) NOT NULL COMMENT '事件类型: ADOPTED/LEVEL_UP/FIRST_WORK/FIRST_WIN/ACHIEVEMENT/EVOLVED/RELATION',
  `occurred_at` datetime NOT NULL COMMENT '发生时间(UTC,按事件实际时间归属)',
  `snapshot` json NOT NULL COMMENT '不可变快照(名字/等级/外观资源键/参数)',
  `visibility` enum('OWNER_ONLY','PUBLIC') NOT NULL DEFAULT 'OWNER_ONLY' COMMENT '可见性(公开单条须明确请求)',
  `version` int NOT NULL DEFAULT '1' COMMENT '乐观版本（可见性 PATCH CAS）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_diary_event` (`pet_id`,`event_id`),
  KEY `idx_diary_pet_time` (`pet_id`,`occurred_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='成长日记(领域事件生成,不可变快照)';

CREATE TABLE `pet_equipment_config` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `code` varchar(60) NOT NULL COMMENT '唯一编码',
  `name` varchar(60) NOT NULL COMMENT '装备名',
  `description` varchar(255) NOT NULL DEFAULT '' COMMENT '装备描述',
  `slot` enum('HAT','NECKLACE','SCARF','BACKPACK') NOT NULL COMMENT '部位:帽子/项圈/围巾/背包',
  `icon` varchar(30) NOT NULL DEFAULT 0xF09F8E80 COMMENT '图标(emoji或URL)',
  `rarity` enum('COMMON','RARE','EPIC') NOT NULL DEFAULT 'COMMON' COMMENT '稀有度',
  `price_starlight` int NOT NULL DEFAULT '0' COMMENT '售价(星光)',
  `bonus_strength` int NOT NULL DEFAULT '0' COMMENT '力量加成',
  `bonus_intelligence` int NOT NULL DEFAULT '0' COMMENT '智力加成',
  `bonus_agility` int NOT NULL DEFAULT '0' COMMENT '敏捷加成',
  `bonus_charm` int NOT NULL DEFAULT '0' COMMENT '魅力加成',
  `bonus_max_hp` int NOT NULL DEFAULT '0' COMMENT '生命上限加成',
  `required_level` int NOT NULL DEFAULT '1' COMMENT '购买/装备最低等级',
  `required_evolution_stage` tinyint NOT NULL DEFAULT '0' COMMENT '购买/装备最低进化阶段',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否上架(1上架/0下架)',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序(升序)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pet_equipment_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物装备配置(商城在售)';

CREATE TABLE `pet_event_config` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `code` varchar(60) NOT NULL COMMENT '唯一编码',
  `name` varchar(60) NOT NULL COMMENT '活动名',
  `description` varchar(255) NOT NULL DEFAULT '' COMMENT '活动描述',
  `event_type` enum('BOTTLE','BATTLE','WORK','STUDY','FEED','PLAY','VISIT') NOT NULL COMMENT '统计口径:捞瓶/对战/打工/读书/喂食/玩耍/串门次数',
  `target_value` int NOT NULL COMMENT '目标次数',
  `reward_starlight` int NOT NULL DEFAULT '0' COMMENT '奖励星光',
  `reward_exp` int NOT NULL DEFAULT '0' COMMENT '奖励宠物经验',
  `reward_item_code` varchar(60) DEFAULT NULL COMMENT '额外奖励物品编码(pet_equipment_config.code, 可空)',
  `reward_alt_starlight` int NOT NULL DEFAULT '20' COMMENT '唯一物品已拥有时的固定替代星光(0=不发)',
  `starts_at` datetime DEFAULT NULL COMMENT '开始时间(NULL=常驻,UTC)',
  `ends_at` datetime DEFAULT NULL COMMENT '结束时间(NULL=常驻,UTC)',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否上架(1上架/0下架)',
  `event_mode` enum('LIFETIME','WINDOW') NOT NULL DEFAULT 'LIFETIME' COMMENT '活动模式:LIFETIME累计(原无时间活动)/WINDOW限时(按[startsAt,endsAt)统计,结束后24h可领)',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序(升序)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pet_event_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='社区宠物活动配置(常驻/限时)';

CREATE TABLE `pet_event_occurrence` (
  `id` bigint unsigned NOT NULL COMMENT '期次 ID（occurrenceId，雪花算法）',
  `event_code` varchar(64) NOT NULL COMMENT '活动 code（关联 pet_event_config）',
  `occurrence_index` int NOT NULL COMMENT '第几期（同 code 递增，从 1 起）',
  `start_at` datetime NOT NULL COMMENT '统计窗口开始(UTC)',
  `end_at` datetime NOT NULL COMMENT '统计窗口结束(UTC)',
  `claim_deadline_at` datetime NOT NULL COMMENT '领奖截止(UTC，结束+宽限期)',
  `reward_snapshot` json DEFAULT NULL COMMENT '奖励快照（发布期次时冻结，配置后改不影响本期）',
  `status` enum('ACTIVE','CLOSED') NOT NULL DEFAULT 'ACTIVE' COMMENT '期次状态',
  `counting_stopped_at` datetime DEFAULT NULL COMMENT '计数停止时刻(UTC)；此前事实仍计入，此后不计',
  `claim_stopped_at` datetime DEFAULT NULL COMMENT '领奖停止时刻(UTC)；到点即拒绝领取',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_occurrence` (`event_code`,`occurrence_index`),
  KEY `idx_event_occurrence_window` (`event_code`,`start_at`,`end_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='限时活动期次(R33)';

CREATE TABLE `pet_event_occurrence_claim` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `occurrence_id` bigint unsigned NOT NULL COMMENT '期次 ID',
  `event_code` varchar(64) NOT NULL COMMENT '活动 code（冗余，便于查询）',
  `pet_id` bigint unsigned NOT NULL COMMENT '领奖宠物（参与/达成事实归属）',
  `user_id` bigint unsigned NOT NULL COMMENT '用户 ID',
  `reward_snapshot` json DEFAULT NULL COMMENT '领奖时的奖励快照',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_occurrence_claim` (`occurrence_id`,`pet_id`),
  KEY `idx_occurrence_claim_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='限时活动期次领奖事实(R33)';

CREATE TABLE `pet_event_progress` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `pet_id` bigint unsigned NOT NULL COMMENT '宠物ID',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `event_code` varchar(60) NOT NULL COMMENT '活动编码(pet_event_config.code)',
  `progress` int NOT NULL DEFAULT '0' COMMENT '已完成次数(惰性统计落库)',
  `claimed_at` datetime DEFAULT NULL COMMENT '领奖时间(非空=已领取,幂等标记)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pet_event_progress` (`pet_id`,`event_code`),
  KEY `idx_pet_event_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物活动进度(进度由业务表惰性统计,不在写入路径累加)';

CREATE TABLE `pet_evolution_config` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `code` varchar(60) NOT NULL COMMENT '唯一编码',
  `name` varchar(60) NOT NULL COMMENT '进化名',
  `description` varchar(255) NOT NULL DEFAULT '' COMMENT '进化描述',
  `stage_from` tinyint NOT NULL COMMENT '起始进化阶段',
  `stage_to` tinyint NOT NULL COMMENT '目标进化阶段',
  `required_level` int NOT NULL COMMENT '所需等级',
  `cost_starlight` int NOT NULL DEFAULT '0' COMMENT '消耗星光',
  `bonus_max_hp` int NOT NULL DEFAULT '0' COMMENT '生命上限提升',
  `bonus_strength` int NOT NULL DEFAULT '0' COMMENT '力量提升',
  `bonus_intelligence` int NOT NULL DEFAULT '0' COMMENT '智力提升',
  `bonus_agility` int NOT NULL DEFAULT '0' COMMENT '敏捷提升',
  `bonus_charm` int NOT NULL DEFAULT '0' COMMENT '魅力提升',
  `unlock_skin_code` varchar(60) DEFAULT NULL COMMENT '解锁皮肤编码(pet_skin_config.code, 可空)',
  `icon` varchar(30) NOT NULL DEFAULT 0xF09F8CA0 COMMENT '图标(emoji或URL)',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用(1启用/0停用)',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序(升序)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pet_evolution_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物进化配置';

CREATE TABLE `pet_feed_entitlement` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint unsigned NOT NULL COMMENT '用户 ID',
  `redemption_code` varchar(64) NOT NULL COMMENT '兑换码（来自 mall-coupon exchange，一码一次）',
  `user_coupon_id` bigint unsigned DEFAULT NULL COMMENT 'coupon 侧发放的用户券 ID（审计回链）',
  `source_fact_id` bigint unsigned DEFAULT NULL COMMENT 'coupon exchange_codes.id（核验事实）',
  `unit_sequence` int NOT NULL DEFAULT '1' COMMENT '同一核验事实的第几份权益（一份一行）',
  `source_order_item_id` bigint unsigned DEFAULT NULL COMMENT '来源订单行（订单绑定码扩展，当前 NULL）',
  `item_code` varchar(64) NOT NULL COMMENT '关联宠物食物 itemCode',
  `expires_at` datetime DEFAULT NULL COMMENT '失效时刻（NULL=不失效，服务端判定）',
  `status` varchar(20) NOT NULL DEFAULT 'AVAILABLE' COMMENT 'AVAILABLE/CONSUMED/EXPIRED/REVOKED',
  `consumed_by_operation_id` varchar(64) DEFAULT NULL COMMENT '占用该份的喂食操作键（唯一，防重）',
  `consumed_pet_id` bigint unsigned DEFAULT NULL COMMENT '消耗时宠物 ID',
  `consumed_at` datetime DEFAULT NULL COMMENT '消耗时间',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_redemption_code` (`redemption_code`),
  UNIQUE KEY `uk_source_fact_unit` (`source_fact_id`,`unit_sequence`),
  UNIQUE KEY `uk_feed_operation` (`consumed_by_operation_id`),
  KEY `idx_user_used` (`user_id`),
  KEY `idx_user_status_exp` (`user_id`,`status`,`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='实物商品联动·双倍喂食权益（§6）';

CREATE TABLE `pet_feed_operation` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint unsigned NOT NULL COMMENT '喂食用户 ID',
  `operation_id` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '喂食操作键（客户端 requestKey，服务端兜底 feed-UUID）',
  `pet_id` bigint unsigned NOT NULL COMMENT '结算时的主宠 ID（重放锚定，不随切宠改投）',
  `item_code` varchar(32) NOT NULL COMMENT '食物编码（同键异餐冲突校验）',
  `entitlement_id` bigint unsigned DEFAULT NULL COMMENT '本次占用的双倍权益 ID（普通喂食为 NULL）',
  `doubled` tinyint(1) NOT NULL DEFAULT '0' COMMENT '本次是否触发双倍效果',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '结算时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_feed_op_user_operation` (`user_id`,`operation_id`),
  KEY `idx_feed_op_pet` (`pet_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='物品喂食已结算操作台账（R02 幂等）';

CREATE TABLE `pet_food_config` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `code` varchar(32) NOT NULL COMMENT '食物编码(唯一,喂食接口入参)',
  `name` varchar(32) NOT NULL COMMENT '名称',
  `icon` varchar(8) NOT NULL DEFAULT 0xF09F8D8E COMMENT '图标emoji',
  `description` varchar(64) NOT NULL DEFAULT '' COMMENT '描述',
  `price_starlight` int NOT NULL DEFAULT '0' COMMENT '售价(星光,≥0)',
  `hunger` int NOT NULL DEFAULT '0' COMMENT '饱食恢复(0~100)',
  `happiness` int NOT NULL DEFAULT '0' COMMENT '心情恢复(0~100)',
  `hp` int NOT NULL DEFAULT '0' COMMENT '生命恢复(0~10000,受max_hp封顶)',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '上架: 1上架 0下架',
  `sort` int NOT NULL DEFAULT '0' COMMENT '商城排序',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_food_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物食物道具配置(商城+喂养效果)';

CREATE TABLE `pet_friend` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `friend_user_id` bigint unsigned NOT NULL COMMENT '好友用户ID',
  `status` enum('PENDING','ACTIVE','REJECTED') NOT NULL DEFAULT 'PENDING' COMMENT '状态:待确认/好友/已拒绝',
  `source` varchar(20) NOT NULL DEFAULT 'VISIT' COMMENT '来源:VISIT访问/SEARCH搜索/RELATION关系',
  `visit_count` int NOT NULL DEFAULT '0' COMMENT '我访问好友次数',
  `last_visit_at` datetime DEFAULT NULL COMMENT '最近一次互访时间(UTC)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pet_friend` (`user_id`,`friend_user_id`),
  KEY `idx_pet_friend_reverse` (`friend_user_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物好友(申请单向行,确认时双向落库)';

CREATE TABLE `pet_friend_feed` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法,兼游标)',
  `user_id` bigint unsigned NOT NULL COMMENT '收件人用户ID(好友)',
  `actor_user_id` bigint unsigned NOT NULL COMMENT '动态主体用户ID',
  `actor_pet_id` bigint unsigned DEFAULT NULL COMMENT '动态主体宠物ID',
  `event_type` varchar(32) NOT NULL COMMENT '事件类型: LEVEL_UP/WORK_COMPLETED/STUDY_COMPLETED/BATTLE_WIN',
  `payload_json` varchar(500) NOT NULL COMMENT '展示载荷: {"text":"...","petName":"..."}',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  PRIMARY KEY (`id`),
  KEY `idx_friend_feed_user_id` (`user_id`,`id`),
  KEY `idx_friend_feed_actor` (`actor_user_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='好友动态收件箱(扇出写,游标分页)';

CREATE TABLE `pet_friend_feed_cursor` (
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `last_read_id` bigint unsigned NOT NULL DEFAULT '0' COMMENT '已读水位(最后读到的 feed id)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='好友动态已读水位';

CREATE TABLE `pet_furniture_config` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `code` varchar(60) NOT NULL COMMENT '唯一编码',
  `name` varchar(60) NOT NULL COMMENT '家具名',
  `description` varchar(255) NOT NULL DEFAULT '' COMMENT '描述',
  `category` enum('WALL','FLOOR','FURNITURE','PLANT','TOY','BED') NOT NULL COMMENT '分类:墙纸/地板/家具/绿植/玩具/床',
  `icon` varchar(30) NOT NULL DEFAULT 0xF09FA7B8 COMMENT '图标(emoji或URL)',
  `rarity` enum('COMMON','RARE','EPIC') NOT NULL DEFAULT 'COMMON' COMMENT '稀有度',
  `price_starlight` int NOT NULL DEFAULT '0' COMMENT '售价(星光)',
  `required_level` int NOT NULL DEFAULT '1' COMMENT '购买最低等级',
  `comfort` int NOT NULL DEFAULT '0' COMMENT '舒适度分值(房间舒适度=已摆放之和)',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否上架(1上架/0下架)',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序(升序)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pet_furniture_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物家具配置(家园商城在售)';

CREATE TABLE `pet_inventory` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `pet_id` bigint unsigned NOT NULL COMMENT '宠物ID',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID(审计/查询冗余)',
  `item_type` enum('EQUIPMENT','SKIN','SKILL_BOOK','FURNITURE','FOOD') NOT NULL COMMENT '物品类型:装备/皮肤/技能书/家具/食物',
  `item_code` varchar(60) NOT NULL COMMENT '物品编码(对应各配置表code)',
  `quantity` int NOT NULL DEFAULT '1' COMMENT '持有数量',
  `equipped` tinyint NOT NULL DEFAULT '0' COMMENT '是否处于装备/穿戴状态(装备与皮肤有效)',
  `slot` varchar(20) DEFAULT NULL COMMENT '装备部位(装备类型有效: HAT/NECKLACE/SCARF/BACKPACK)',
  `acquired_at` datetime NOT NULL COMMENT '获得时间(UTC)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pet_inventory_item` (`pet_id`,`item_type`,`item_code`),
  UNIQUE KEY `uk_inventory_slot_equipped` (`pet_id`,`slot`,(if(((`item_type` = _utf8mb4'EQUIPMENT') and (`equipped` = 1)),1,NULL))),
  UNIQUE KEY `uk_inventory_skin_equipped` (`pet_id`,(if(((`item_type` = _utf8mb4'SKIN') and (`equipped` = 1)),1,NULL))),
  KEY `idx_pet_inventory_user` (`user_id`),
  KEY `idx_pet_inventory_equipped` (`pet_id`,`equipped`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物背包(装备/皮肤/技能书)';

CREATE TABLE `pet_job_config` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `name` varchar(60) NOT NULL COMMENT '岗位名(如:咖啡店兼职)',
  `description` varchar(255) NOT NULL DEFAULT '' COMMENT '岗位描述',
  `duration_seconds` int NOT NULL COMMENT '工作时长(秒)',
  `energy_cost` int NOT NULL DEFAULT '0' COMMENT '消耗精力',
  `hunger_cost` int NOT NULL DEFAULT '0' COMMENT '消耗饥饿(越工作越饿)',
  `exp_reward` int NOT NULL DEFAULT '0' COMMENT '宠物经验奖励',
  `currency_reward` int NOT NULL DEFAULT '0' COMMENT '星光奖励(经mall-wish内部端点发放,流水来源PET_REWARD)',
  `required_level` int NOT NULL DEFAULT '1' COMMENT '接单最低等级',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用(1启用/0停用)',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序(升序)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物打工岗位配置';

CREATE TABLE `pet_linked_product_operation` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `operation_id` varchar(64) NOT NULL COMMENT '核销操作 ID（对外不透明字符串）',
  `request_key` varchar(64) DEFAULT NULL COMMENT '客户端请求键（重放判重）',
  `user_id` bigint unsigned NOT NULL COMMENT '发起核销的用户',
  `redemption_code` varchar(64) NOT NULL COMMENT '兑换码',
  `coupon_fact_id` bigint unsigned DEFAULT NULL COMMENT 'coupon exchange_codes.id（核验通过后回填）',
  `coupon_template_id` bigint unsigned DEFAULT NULL COMMENT '兑换码关联模板（权益配置推导用）',
  `entitlement_id` bigint unsigned DEFAULT NULL COMMENT '核销成功后发放的权益 ID',
  `status` varchar(20) NOT NULL DEFAULT 'VERIFYING' COMMENT 'VERIFYING/DELIVERED/REJECTED/PENDING',
  `reject_reason_code` varchar(64) DEFAULT NULL COMMENT '拒绝原因码（PET_CODE_*）',
  `last_error` varchar(500) DEFAULT NULL COMMENT '最近一次核验/发放错误（PENDING 恢复依据）',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_operation_id` (`operation_id`),
  UNIQUE KEY `uk_user_request` (`user_id`,`request_key`),
  KEY `idx_user_status` (`user_id`,`status`),
  KEY `idx_code` (`redemption_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品权益核销操作台账（R01）';

CREATE TABLE `pet_memory` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `pet_id` bigint unsigned NOT NULL COMMENT '宠物ID',
  `memory_type` enum('FAVORITE','HABIT','FACT') NOT NULL COMMENT '记忆类型:喜好/习惯/事实',
  `memory_key` varchar(60) NOT NULL COMMENT '记忆键(如favorite_food/owner_nickname)',
  `memory_value` varchar(255) NOT NULL COMMENT '记忆值(如"鱼")',
  `source` enum('AUTO','USER') NOT NULL DEFAULT 'AUTO' COMMENT '来源(用户编辑优先于自动抽取)',
  `importance` int NOT NULL DEFAULT '3' COMMENT '重要度1-5(注入prompt排序权重)',
  `confidence` decimal(4,3) NOT NULL DEFAULT '0.900' COMMENT '置信度0-1(规则抽取默认0.9)',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '启用状态(删除=0,删除标记防复活)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pet_memory_key` (`pet_id`,`memory_key`),
  KEY `idx_memory_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物结构化记忆(聊天关键信息抽取,非全量历史回放)';

CREATE TABLE `pet_minigame_operation` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `round_id` bigint unsigned NOT NULL COMMENT '对局ID',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID(归属校验/扫描)',
  `window_index` int NOT NULL COMMENT '时间窗序号(1基，由 floor((serverNow-startedAt)/windowMs) 决定)',
  `slot` varchar(16) NOT NULL COMMENT '命中的目标槽位(LEFT/CENTER/RIGHT)',
  `accepted` tinyint NOT NULL DEFAULT '1' COMMENT '是否有效接受(1=是)',
  `server_time` datetime NOT NULL COMMENT '服务端接收时间(UTC，唯一权威)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_minigame_operation_window` (`round_id`,`window_index`),
  KEY `idx_minigame_operation_round` (`round_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='小游戏窗口操作事实(每窗至多一次)';

CREATE TABLE `pet_minigame_round` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `pet_id` bigint unsigned NOT NULL COMMENT '宠物ID',
  `game_type` enum('CATCH') NOT NULL DEFAULT 'CATCH' COMMENT '玩法:接球(第一版仅此一种)',
  `status` enum('ACTIVE','SETTLED','EXPIRED') NOT NULL DEFAULT 'ACTIVE' COMMENT '状态机',
  `rule_version` varchar(20) NOT NULL DEFAULT 'V1' COMMENT '规则版本(写入局快照,配置变更不改已开局)',
  `started_at` datetime NOT NULL COMMENT '服务端开始时间(UTC)',
  `deadline_at` datetime NOT NULL COMMENT '服务端截止时间(UTC,30秒+2秒宽限)',
  `sequence` json NOT NULL COMMENT '随机挑战序列: 10 个窗口目标(左/中/右)',
  `ops` json DEFAULT NULL COMMENT '已接受操作: [{seq,windowIndex,slot,serverTime}]',
  `success_count` int NOT NULL DEFAULT '0' COMMENT '成功接球次数',
  `reward_eligible` tinyint NOT NULL DEFAULT '1' COMMENT '是否有收益(训练局 false)',
  `reward_operation_id` varchar(160) DEFAULT NULL COMMENT '奖励操作键(同一局只发一次,与pet_operation同宽)',
  `quota_date` date NOT NULL COMMENT '开局业务日(额度按开局归属,跨午夜不重复扣)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_minigame_active` (`user_id`,(if((`status` = _utf8mb4'ACTIVE'),1,NULL))),
  KEY `idx_minigame_user` (`user_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='轻量互动小游戏局(服务端验证操作,不信客户端分数)';

CREATE TABLE `pet_notify_pref` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `mute_daily_greeting` tinyint NOT NULL DEFAULT '0' COMMENT '日常问候免打扰(1=静默)',
  `daily_greeting_enabled` tinyint NOT NULL DEFAULT '1' COMMENT '日常问候类型开关(0=关闭该类通知)',
  `version` int NOT NULL DEFAULT '1' COMMENT '乐观版本（PUT CAS）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notify_pref_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物通知偏好(仅作用于日常问候,重要业务通知不受影响)';

CREATE TABLE `pet_offline_cursor` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `last_confirmed_at` datetime NOT NULL COMMENT '上次确认时间(UTC,摘要查询起点)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_offline_cursor_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='离线摘要确认游标(查询不重发奖励,确认只推进游标)';

CREATE TABLE `pet_onboarding_progress` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `guide_version` varchar(20) NOT NULL DEFAULT 'V1' COMMENT '引导版本',
  `steps` json NOT NULL COMMENT '步骤状态JSON:{FEED/PLAY/WORK/DECORATE: LOCKED|OPEN|DONE}',
  `skipped_at` datetime DEFAULT NULL COMMENT '跳过时间(UTC)',
  `completed_at` datetime DEFAULT NULL COMMENT '完成时间(UTC)',
  `furniture_grant_op_id` varchar(80) DEFAULT NULL COMMENT '基础家具赠送操作键(每用户一次,幂等)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_onboarding_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='新手引导进度(可恢复,领域事件驱动)';

CREATE TABLE `pet_outbox_event` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `event_id` varchar(120) NOT NULL COMMENT '业务事件唯一键(TYPE:业务实例,确定性生成,消费者据此去重)',
  `event_type` varchar(60) NOT NULL COMMENT '事件类型(=MQ tag: WORK_COMPLETED/LEVEL_UP/BATTLE_FINISHED/...)',
  `user_id` bigint unsigned NOT NULL COMMENT '目标用户ID',
  `pet_id` bigint unsigned DEFAULT NULL COMMENT '关联宠物ID',
  `payload` json NOT NULL COMMENT '消息体JSON(reminderType/title/content/bizId/bizType/eventId,Long一律字符串)',
  `status` enum('NEW','SENT','FAILED') NOT NULL DEFAULT 'NEW' COMMENT '发送状态:待发/已发/失败待重试',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '重试次数',
  `next_retry_at` datetime DEFAULT NULL COMMENT '下次重试时间(指数退避)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pet_outbox_event` (`event_id`),
  KEY `idx_pet_outbox_send` (`status`,`next_retry_at`),
  KEY `idx_pet_outbox_user` (`user_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物事务性事件发件箱(提交后可靠投递,幂等事件ID)';

CREATE TABLE `pet_persona_phrase` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `personality` varchar(16) NOT NULL COMMENT '性格编码(PetPersonality 枚举)',
  `phrase` varchar(64) NOT NULL COMMENT '口头禅模板({name} 占位宠物名)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_persona_personality` (`personality`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物AI人设口头禅(按性格)';

CREATE TABLE `pet_purchase_order` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `pet_id` bigint unsigned DEFAULT NULL COMMENT '宠物ID(可空:账号级物品)',
  `item_type` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '物品类型(EQUIPMENT/SKIN/SKILL/FOOD/FURNITURE等)',
  `item_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '物品编码(稳定code)',
  `quantity` int unsigned NOT NULL DEFAULT '1' COMMENT '数量(首版固定1)',
  `unit_price` bigint unsigned NOT NULL COMMENT '单价快照(服务端权威,禁止客户端传价)',
  `total_amount` bigint unsigned NOT NULL COMMENT '实扣总额(=unit_price*quantity)',
  `currency` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PET_COIN' COMMENT '币种',
  `wallet_domain` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PET' COMMENT '钱包域(PET=宠物币;历史社区单在 pet_operation)',
  `config_version` varchar(40) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '商品配置版本(成立订单按快照结算,T22)',
  `item_snapshot` json DEFAULT NULL COMMENT '物品快照(名称/资源key/价格)',
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '状态:PROCESSING/COMPLETED/REJECTED',
  `wallet_transaction_id` bigint unsigned DEFAULT NULL COMMENT '扣款流水ID',
  `request_key` varchar(128) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '客户端请求键(16..128 ASCII,同一次购买意图唯一;uk 兜底防重复建单)',
  `payload_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '规范请求摘要SHA-256(与 dedup 行一致,用于事实核对)',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC)',
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC)',
  `completed_at` datetime(6) DEFAULT NULL COMMENT '完成时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pet_purchase_order_request` (`user_id`,`request_key`),
  KEY `idx_pet_purchase_order_user` (`user_id`,`created_at`),
  KEY `idx_pet_purchase_order_item` (`user_id`,`item_type`,`item_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物币购买订单(价格快照/实扣/资产交付来源)';

CREATE TABLE `pet_pve_participant` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint unsigned NOT NULL COMMENT '用户 ID（一个用户同时仅一个活动副本）',
  `run_id` bigint unsigned NOT NULL COMMENT '副本 ID',
  `pet_id` bigint unsigned NOT NULL COMMENT '参战宠物 ID（占用时刻冻结，切宠不迁移）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '占用时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_active` (`user_id`),
  KEY `idx_run` (`run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='PVE 参与占用（R03：发起/加入共用）';

CREATE TABLE `pet_pve_round_log` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `run_id` bigint unsigned NOT NULL COMMENT '副本 ID',
  `turn_no` int NOT NULL COMMENT '回合号（1 起）',
  `seq` int NOT NULL COMMENT '回合内动作序号（1=宠物攻击，2=Boss 反击）',
  `actor_user_id` bigint unsigned DEFAULT NULL COMMENT '动作方用户 ID（Boss 行为为 NULL）',
  `actor_pet_id` bigint unsigned DEFAULT NULL COMMENT '动作方宠物 ID',
  `actor_name` varchar(64) NOT NULL COMMENT '动作方名称',
  `action` varchar(16) NOT NULL COMMENT 'ATTACK/COUNTER',
  `damage` int NOT NULL DEFAULT '0' COMMENT '伤害（0=被闪避）',
  `critical` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否暴击',
  `dodged` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否被闪避',
  `target_name` varchar(64) NOT NULL COMMENT '受击方名称',
  `target_hp_after` int NOT NULL DEFAULT '0' COMMENT '受击方剩余 HP',
  `request_key` varchar(64) DEFAULT NULL COMMENT '攻击请求键（仅 seq=1 行携带，重放判重）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_run_turn_seq` (`run_id`,`turn_no`,`seq`),
  UNIQUE KEY `uk_run_request` (`run_id`,`request_key`),
  KEY `idx_run` (`run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='PVE 回合明细（R03：独立追加，替代 VARCHAR(8000) JSON）';

CREATE TABLE `pet_pve_run` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `boss_code` varchar(32) NOT NULL COMMENT 'Boss 编码（forest_ogre/sand_worm/void_knight）',
  `boss_name` varchar(64) NOT NULL COMMENT 'Boss 名称',
  `boss_max_hp` int NOT NULL COMMENT 'Boss 最大 HP',
  `boss_hp` int NOT NULL COMMENT 'Boss 当前 HP',
  `initiator_user_id` bigint unsigned NOT NULL COMMENT '发起人用户 ID',
  `initiator_pet_id` bigint unsigned NOT NULL COMMENT '发起人宠物 ID',
  `initiator_snapshot` varchar(600) NOT NULL COMMENT '发起人宠物战斗快照 JSON（Fighter）',
  `partner_user_id` bigint unsigned DEFAULT NULL COMMENT '队友用户 ID（加入后回填）',
  `partner_pet_id` bigint unsigned DEFAULT NULL COMMENT '队友宠物 ID',
  `partner_snapshot` varchar(600) DEFAULT NULL COMMENT '队友宠物战斗快照 JSON',
  `initiator_pet_hp` int NOT NULL DEFAULT '0' COMMENT '发起人宠物当前 HP',
  `partner_pet_hp` int DEFAULT NULL COMMENT '队友宠物当前 HP',
  `status` varchar(16) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/FIGHTING/WON/FAILED/EXPIRED',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本（CAS 推进）',
  `turn_no` int NOT NULL DEFAULT '0' COMMENT '已完成回合数（下一回合=turn_no+1）',
  `next_actor_user_id` bigint unsigned DEFAULT NULL COMMENT '当前行动方用户 ID（第 1 回合=发起人）',
  `turn_deadline_at` datetime DEFAULT NULL COMMENT '本回合行动截止（超时托管/失败收口）',
  `run_expires_at` datetime DEFAULT NULL COMMENT '副本总时限（FIGHTING 超时批量收口）',
  `rule_version` varchar(16) NOT NULL DEFAULT 'V1' COMMENT '战斗规则版本（伤害口径/种子回放）',
  `seed` bigint NOT NULL DEFAULT '0' COMMENT '固定种子（开局快照+回合号决定性重放）',
  `reward_status` varchar(16) NOT NULL DEFAULT 'NONE' COMMENT 'NONE/PENDING/SETTLED（聚合展示，权威在 pet_reward_claim）',
  `initiator_request_key` varchar(64) DEFAULT NULL COMMENT '发起请求键（重放幂等）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `finished_at` datetime DEFAULT NULL COMMENT '结束时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_initiator_request` (`initiator_request_key`),
  KEY `idx_status_boss` (`status`,`boss_code`),
  KEY `idx_initiator` (`initiator_user_id`,`created_at`),
  KEY `idx_partner` (`partner_user_id`),
  KEY `idx_status_expire` (`status`,`run_expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='协作 PVE 副本（§6）';

CREATE TABLE `pet_quest_event_receipt` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `user_id` bigint unsigned NOT NULL COMMENT '用户 ID',
  `pet_id` bigint unsigned NOT NULL COMMENT '宠物 ID（进度归属）',
  `quest_code` varchar(64) NOT NULL COMMENT '任务类型（PetQuestType.name，如 WORK/CHAT/COMPANION）',
  `event_id` varchar(128) NOT NULL COMMENT '业务事实唯一键（如 ACT_CLAIM:{activityId}/CHAT:{messageId}/COMPANION:{petId}:{date}:{minutes}）',
  `source_time` datetime NOT NULL COMMENT '事实发生时间(UTC)',
  `business_date` date NOT NULL COMMENT '实际计入的业务日（事实日或补偿的历史日）',
  `amount` int NOT NULL COMMENT '本次计入进度',
  `status` varchar(20) NOT NULL DEFAULT 'APPLIED' COMMENT 'APPLIED 已计入 / SKIPPED_STALE 事实过期（当日任务行不存在）',
  `attempts` int NOT NULL DEFAULT '0' COMMENT '投影已尝试次数（重放/调度重试累加）',
  `next_retry_at` datetime DEFAULT NULL COMMENT '下次自动重试时间(UTC)；NULL 表示无待重试',
  `last_error` varchar(500) DEFAULT NULL COMMENT '最近一次投影失败原因（截断 500 字符）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_quest_event_receipt` (`quest_code`,`event_id`),
  KEY `idx_quest_receipt_user` (`user_id`,`business_date`),
  KEY `idx_quest_receipt_status` (`status`),
  KEY `idx_quest_receipt_retry` (`status`,`next_retry_at`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='任务事件回执(R32)';

CREATE TABLE `pet_relation` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `from_pet_id` bigint unsigned NOT NULL COMMENT '发起方宠物ID',
  `to_pet_id` bigint unsigned NOT NULL COMMENT '接收方宠物ID',
  `from_user_id` bigint unsigned NOT NULL COMMENT '发起方主人用户ID',
  `to_user_id` bigint unsigned NOT NULL COMMENT '接收方主人用户ID',
  `rel_type` enum('COUPLE','BESTIE','BROTHER','CONFIDANT') NOT NULL COMMENT '关系类型:情侣/闺蜜/兄弟/死党',
  `status` enum('PENDING','ACTIVE','REJECTED','DISSOLVED') NOT NULL DEFAULT 'PENDING' COMMENT '状态:待确认/已建立/已拒绝/已解除',
  `intimacy` int NOT NULL DEFAULT '0' COMMENT '关系亲密度(互访/留言/对战累积)',
  `message` varchar(60) DEFAULT NULL COMMENT '申请留言',
  `accepted_at` datetime DEFAULT NULL COMMENT '确认时间(UTC)',
  `last_intimacy_at` datetime DEFAULT NULL COMMENT '最近一次亲密度增长时间(UTC)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  `pet_a_id` bigint unsigned NOT NULL COMMENT '规范化较小宠物ID',
  `pet_b_id` bigint unsigned NOT NULL COMMENT '规范化较大宠物ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pet_relation` (`from_pet_id`,`to_pet_id`,`rel_type`),
  UNIQUE KEY `uk_pet_relation_pair` (`pet_a_id`,`pet_b_id`,`rel_type`,(if((`status` = _utf8mb4'ACTIVE'),1,NULL))),
  KEY `idx_pet_relation_to` (`to_pet_id`,`status`),
  KEY `idx_pet_relation_from_user` (`from_user_id`,`status`),
  KEY `idx_pet_relation_to_user` (`to_user_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物关系(uk幂等,同一对宠物同一类型仅一条)';

CREATE TABLE `pet_report` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `reporter_user_id` bigint unsigned NOT NULL COMMENT '举报人用户ID',
  `target_type` enum('WALL_MESSAGE','BOTTLE_CONTENT','NICKNAME','CHAT_MESSAGE') NOT NULL COMMENT '举报对象类型',
  `target_id` bigint unsigned NOT NULL COMMENT '举报对象ID',
  `reason` varchar(200) NOT NULL COMMENT '举报原因(用户提交)',
  `description` varchar(1000) DEFAULT NULL COMMENT '补充说明（1~1000 字符）',
  `status` enum('PENDING','HANDLED','REJECTED') NOT NULL DEFAULT 'PENDING' COMMENT '处理状态',
  `handled_by` bigint unsigned DEFAULT NULL COMMENT '处理管理员ID',
  `handled_at` datetime DEFAULT NULL COMMENT '处理时间(UTC)',
  `handle_action` varchar(32) DEFAULT NULL COMMENT '处理动作：CONTENT_REMOVED/USER_WARNED/USER_PET_BANNED/DISMISSED',
  `handle_reason` varchar(200) DEFAULT NULL COMMENT '处理说明（通知举报人的依据）',
  `is_auto` tinyint NOT NULL DEFAULT '0' COMMENT '是否系统自动举报：1自动(如危机词命中) 0用户提交',
  `report_date` date DEFAULT NULL COMMENT '举报业务日（每日配额统计/审计）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '举报时间(UTC)',
  `open_key` varchar(80) GENERATED ALWAYS AS ((case when (`status` = _utf8mb4'PENDING') then concat_ws(_utf8mb4':',`reporter_user_id`,`target_type`,`target_id`) else NULL end)) STORED COMMENT '未结案幂等键：同人同对象至多一张待审，结案自动置 NULL',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pet_report_open` (`open_key`),
  KEY `idx_pet_report_status` (`status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物内容举报(管理员处理审计)';

CREATE TABLE `pet_request_dedup` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `endpoint_key` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '规范化逻辑操作码(如 PURCHASE/CLAIM)',
  `request_key` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '客户端请求键(16..128 ASCII,同键重试复用)',
  `payload_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '规范请求摘要SHA-256(同键不同内容409)',
  `bound_pet_id` bigint unsigned DEFAULT NULL COMMENT '首次执行绑定的目标宠物ID（旧请求无显式petId时冻结，重放按原归属）',
  `biz_order_id` bigint unsigned DEFAULT NULL COMMENT '业务单ID(订单/claim)',
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '状态:PROCESSING/COMPLETED/FAILED(FAILED 可同键重试)',
  `lease_owner` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '租约持有者(实例+线程标识,接管时轮换)',
  `lease_until` datetime(6) DEFAULT NULL COMMENT '租约到期时间(UTC,到期前同键返回处理中,到期后可 CAS 接管)',
  `version` bigint unsigned NOT NULL DEFAULT '0' COMMENT '乐观版本(接管/终态 CAS 推进)',
  `response_json` json DEFAULT NULL COMMENT '终态响应快照(成功与业务拒绝均保存,同键返回原结果)',
  `terminal_error_code` varchar(64) DEFAULT NULL COMMENT '业务拒绝终态码（COMPLETED 且为拒绝结果时记录，重放返回同一拒绝）',
  `finished_at` datetime DEFAULT NULL COMMENT '终态完成时间（UTC）',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC)',
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pet_request_dedup` (`user_id`,`endpoint_key`,`request_key`),
  KEY `idx_pet_request_dedup_recovery` (`status`,`lease_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='请求级幂等去重(网络重试/刷新/重启复用原键)';

CREATE TABLE `pet_reward_claim` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `pet_id` bigint unsigned DEFAULT NULL COMMENT '绑定宠物ID(领奖冻结归属,切主宠不改变)',
  `biz_type` varchar(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务类型:ACTIVITY_REWARD/COOP_REWARD/CHEST_REWARD等',
  `biz_id` varchar(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务实例ID(activityId/cooperationId+userId等)',
  `reward_slot` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'MAIN' COMMENT '奖励槽位(同事实多奖励分离)',
  `reward_snapshot` json DEFAULT NULL COMMENT '冻结奖励快照(物品/币/成长,随机结果只生成一次)',
  `wallet_domain` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PET' COMMENT '结算钱包域',
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '状态:PROCESSING/COMPLETED/REJECTED',
  `result_json` json DEFAULT NULL COMMENT '最终结果快照(重放返回同一结果,不重抽)',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC)',
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC)',
  `completed_at` datetime(6) DEFAULT NULL COMMENT '完成时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pet_reward_claim` (`user_id`,`biz_type`,`biz_id`,`reward_slot`),
  KEY `idx_pet_reward_claim_user` (`user_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='奖励领取事实(同一业务事实至多领取一次,合作/活动/宝箱复用)';

CREATE TABLE `pet_room` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `pet_id` bigint unsigned NOT NULL COMMENT '宠物ID',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID(访问查询冗余)',
  `wall_code` varchar(60) DEFAULT NULL COMMENT '墙纸编码(pet_furniture_config.code, NULL=默认)',
  `floor_code` varchar(60) DEFAULT NULL COMMENT '地板编码(pet_furniture_config.code, NULL=默认)',
  `welcome_message` varchar(80) NOT NULL DEFAULT '' COMMENT '欢迎语(来访者可见)',
  `is_public` tinyint NOT NULL DEFAULT '1' COMMENT '是否允许来访(1公开/0仅自己)',
  `comfort` int NOT NULL DEFAULT '0' COMMENT '当前舒适度(已摆放家具之和)',
  `visit_count` int NOT NULL DEFAULT '0' COMMENT '累计来访次数(去重按日,服务端维护)',
  `like_count` int NOT NULL DEFAULT '0' COMMENT '累计点赞数',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号(摆放/主题并发写保护)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pet_room` (`pet_id`),
  KEY `idx_pet_room_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物房间(懒创建:首次进入家园时生成)';

CREATE TABLE `pet_room_item` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `pet_id` bigint unsigned NOT NULL COMMENT '宠物ID',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID(查询冗余)',
  `furniture_code` varchar(60) NOT NULL COMMENT '家具编码(pet_furniture_config.code)',
  `pos_x` tinyint NOT NULL COMMENT '网格 X(0-3)',
  `pos_y` tinyint NOT NULL COMMENT '网格 Y(0-2)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `archived_at` datetime DEFAULT NULL COMMENT '归档时间(重复摆放修复,UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pet_room_item_pos` (`pet_id`,`pos_x`,`pos_y`),
  KEY `idx_pet_room_item_pet` (`pet_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物房间摆放(格子唯一,换位=先卸下再摆)';

CREATE TABLE `pet_room_like` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `user_id` bigint unsigned NOT NULL COMMENT '点赞用户ID',
  `room_id` bigint unsigned NOT NULL COMMENT '房间ID',
  `active` tinyint NOT NULL DEFAULT '1' COMMENT '当前是否点赞(取消置0;曾获奖标记保留防重复发奖)',
  `rewarded` tinyint NOT NULL DEFAULT '0' COMMENT '是否已发过点赞经验(取消再点不再发奖)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次点赞时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_room_like` (`user_id`,`room_id`),
  KEY `idx_room_like_room` (`room_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='房间点赞持久化(唯一点赞,显式取消)';

CREATE TABLE `pet_season` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `name` varchar(64) NOT NULL COMMENT '赛季名称',
  `starts_at` datetime NOT NULL COMMENT '开始时间(UTC)',
  `ends_at` datetime NOT NULL COMMENT '结束时间(UTC)',
  `claim_until` datetime DEFAULT NULL COMMENT '领奖截止（NULL=随赛季结束）',
  `status` enum('ACTIVE','FREEZING','SETTLING','SETTLED') NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE进行中/FREEZING冻榜中/SETTLING发奖中/SETTLED已结算',
  `settled_at` datetime DEFAULT NULL COMMENT '结算时间(UTC)',
  `freeze_at` datetime DEFAULT NULL COMMENT '冻榜完成时间(UTC，延迟冻榜时≠endsAt)',
  `snapshot_complete` tinyint NOT NULL DEFAULT '0' COMMENT '排名快照是否完整(1=完整)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  PRIMARY KEY (`id`),
  KEY `idx_pet_season_status_ends` (`status`,`ends_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物排行榜赛季';

CREATE TABLE `pet_season_guard` (
  `id` tinyint unsigned NOT NULL COMMENT '守卫行(恒为1)',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='赛季创建串行守卫';

CREATE TABLE `pet_season_pass` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `season_id` bigint unsigned NOT NULL COMMENT '赛季 ID（pet_season.id）',
  `user_id` bigint unsigned NOT NULL COMMENT '用户 ID',
  `pass_exp` int NOT NULL DEFAULT '0' COMMENT '通行证经验（任务领取累积）',
  `claimed_tiers` varchar(200) NOT NULL DEFAULT '[]' COMMENT '已领取档位（JSON 数组，如 [1,2]）',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本（投影同步 CAS）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_season_user` (`season_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='赛季通行证进度（§6）';

CREATE TABLE `pet_season_pass_claim` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `season_id` bigint unsigned NOT NULL COMMENT '赛季 ID',
  `user_id` bigint unsigned NOT NULL COMMENT '用户 ID',
  `tier` int NOT NULL COMMENT '档位（1..N）',
  `status` varchar(20) NOT NULL DEFAULT 'CLAIM_PENDING' COMMENT 'CLAIM_PENDING/SETTLED',
  `reward_snapshot` varchar(500) DEFAULT NULL COMMENT '奖励快照（币/皮肤码/规则版本）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '领取发起时间',
  `settled_at` datetime DEFAULT NULL COMMENT '交付完成时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_season_user_tier` (`season_id`,`user_id`,`tier`),
  KEY `idx_pending` (`status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通行证逐档领取事实（R05 权威）';

CREATE TABLE `pet_season_pass_exp_fact` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `season_id` bigint unsigned NOT NULL COMMENT '赛季 ID（事实归属赛季，补发不重复计入）',
  `user_id` bigint unsigned NOT NULL COMMENT '用户 ID',
  `source` varchar(20) NOT NULL COMMENT '来源：QUEST/CHEST',
  `source_biz_id` varchar(64) NOT NULL COMMENT '来源事实 ID（任务实例/宝箱领取）',
  `exp` int NOT NULL COMMENT '经验值',
  `business_date` date NOT NULL COMMENT '事实业务日（Asia/Shanghai）',
  `rule_version` varchar(16) NOT NULL DEFAULT 'V1' COMMENT '规则版本',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fact` (`season_id`,`user_id`,`source`,`source_biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通行证经验事实（R05 去重）';

CREATE TABLE `pet_season_ranking` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键(自增;快照INSERT...SELECT生成)',
  `season_id` bigint unsigned NOT NULL COMMENT '赛季ID',
  `pet_id` bigint unsigned NOT NULL COMMENT '宠物ID',
  `user_id` bigint unsigned NOT NULL COMMENT '主人用户ID',
  `rank_no` int NOT NULL COMMENT '最终名次(1基)',
  `level` int NOT NULL COMMENT '结算时等级',
  `exp` int NOT NULL COMMENT '结算时经验',
  `reward_status` varchar(20) DEFAULT NULL COMMENT '奖励状态: NULL未发/SUCCEEDED已发',
  `rewarded_at` datetime DEFAULT NULL COMMENT '发奖时间(UTC)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_season_ranking_pet` (`season_id`,`pet_id`),
  KEY `idx_season_ranking_user` (`season_id`,`user_id`),
  KEY `idx_season_ranking_cursor` (`season_id`,`rank_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='赛季最终榜快照(历史名次依据)';

CREATE TABLE `pet_season_reward` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `season_id` bigint unsigned NOT NULL COMMENT '赛季ID',
  `rank_min` int NOT NULL COMMENT '名次下界(含,1基)',
  `rank_max` int NOT NULL COMMENT '名次上界(含)',
  `reward_starlight` int NOT NULL DEFAULT '0' COMMENT '星光奖励',
  `reward_exp` int NOT NULL DEFAULT '0' COMMENT '经验奖励',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_season_reward_range` (`season_id`,`rank_min`),
  KEY `idx_season_reward_season` (`season_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='赛季奖励梯度(rank_min<=名次<=rank_max)';

CREATE TABLE `pet_season_settlement_job` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '作业ID',
  `season_id` bigint unsigned NOT NULL COMMENT '赛季ID(唯一)',
  `status` varchar(20) NOT NULL DEFAULT 'RUNNING' COMMENT '状态: RUNNING/COMPLETED',
  `cursor_rank` int NOT NULL DEFAULT '0' COMMENT '已处理到的名次游标(按rank_no升序)',
  `total_count` int NOT NULL DEFAULT '0' COMMENT '快照总人数',
  `success_count` int NOT NULL DEFAULT '0' COMMENT '累计发奖成功数',
  `failure_count` int NOT NULL DEFAULT '0' COMMENT '累计发奖失败数',
  `lease_owner` varchar(64) DEFAULT NULL COMMENT '当前执行者租约标识',
  `lease_version` bigint NOT NULL DEFAULT '0' COMMENT '租约版本(CAS推进)',
  `lease_until` datetime DEFAULT NULL COMMENT '租约到期时间(UTC)',
  `last_error` varchar(500) DEFAULT NULL COMMENT '最近一次错误摘要',
  `next_retry_at` datetime DEFAULT NULL COMMENT '下次重试时间(UTC)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_season_settlement_season` (`season_id`),
  KEY `idx_season_settlement_lease` (`status`,`lease_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='赛季结算作业(checkpoint+租约)';

CREATE TABLE `pet_skill` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `pet_id` bigint unsigned NOT NULL COMMENT '宠物ID',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `skill_code` varchar(60) NOT NULL COMMENT '技能编码(pet_skill_config.code)',
  `equipped` tinyint NOT NULL DEFAULT '1' COMMENT '是否已装配(一期学习即装配,保留字段供多技能切换)',
  `learned_at` datetime NOT NULL COMMENT '学习时间(UTC)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pet_skill` (`pet_id`,`skill_code`),
  KEY `idx_pet_skill_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物已学技能(uk幂等,同一技能只学一次)';

CREATE TABLE `pet_skill_config` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `code` varchar(60) NOT NULL COMMENT '唯一编码',
  `name` varchar(60) NOT NULL COMMENT '技能名',
  `description` varchar(255) NOT NULL DEFAULT '' COMMENT '技能描述',
  `skill_type` enum('ACTIVE','PASSIVE') NOT NULL COMMENT '类型:主动/被动',
  `effect` enum('POWER_STRIKE','LUCKY_FISH','QUICK_STEP','BOOKWORM','CHARM_AURA','TOUGH_BODY') NOT NULL COMMENT '效果标识(服务端公式唯一开关)',
  `effect_value` decimal(6,3) NOT NULL DEFAULT '0.000' COMMENT '效果数值(百分比为0-1小数/点数为整数)',
  `icon` varchar(30) NOT NULL DEFAULT 0xF09F8C9F COMMENT '图标(emoji或URL)',
  `price_starlight` int NOT NULL DEFAULT '0' COMMENT '售价(星光,购买后落背包技能书)',
  `required_level` int NOT NULL DEFAULT '1' COMMENT '学习最低等级',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否上架(1上架/0下架)',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序(升序)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pet_skill_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物技能配置';

CREATE TABLE `pet_skin_config` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `code` varchar(60) NOT NULL COMMENT '唯一编码',
  `name` varchar(60) NOT NULL COMMENT '皮肤名',
  `description` varchar(255) NOT NULL DEFAULT '' COMMENT '皮肤描述',
  `species` varchar(20) DEFAULT NULL COMMENT '限定种类(STRAWBERRY/ORANGE/WATERMELON/BLUEBERRY/DRAGONFRUIT, NULL=通用)',
  `color` varchar(20) NOT NULL COMMENT '主色(前端/Cocos 调色板键)',
  `accessory` varchar(20) NOT NULL DEFAULT 'none' COMMENT '配饰键',
  `icon` varchar(30) NOT NULL DEFAULT '✨' COMMENT '图标(emoji或URL)',
  `rarity` enum('COMMON','RARE','EPIC') NOT NULL DEFAULT 'COMMON' COMMENT '稀有度',
  `price_starlight` int NOT NULL DEFAULT '0' COMMENT '售价(星光)',
  `required_level` int NOT NULL DEFAULT '1' COMMENT '购买/穿戴最低等级',
  `required_evolution_stage` tinyint NOT NULL DEFAULT '0' COMMENT '购买/穿戴最低进化阶段',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否上架(1上架/0下架)',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序(升序)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pet_skin_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物皮肤配置(商城在售)';

CREATE TABLE `pet_study_config` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `name` varchar(60) NOT NULL COMMENT '课程名(如:编程入门)',
  `description` varchar(255) NOT NULL DEFAULT '' COMMENT '课程描述',
  `category` varchar(30) NOT NULL DEFAULT 'GENERAL' COMMENT '分类:文学/历史/科学/艺术/编程/社交/心理/冒险',
  `duration_seconds` int NOT NULL COMMENT '学习时长(秒)',
  `energy_cost` int NOT NULL DEFAULT '0' COMMENT '消耗精力',
  `exp_reward` int NOT NULL DEFAULT '0' COMMENT '宠物经验奖励',
  `intelligence_reward` int NOT NULL DEFAULT '0' COMMENT '智力提升',
  `required_level` int NOT NULL DEFAULT '1' COMMENT '接课最低等级',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用(1启用/0停用)',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序(升序)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物读书课程配置';

CREATE TABLE `pet_user_block` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `user_id` bigint unsigned NOT NULL COMMENT '发起屏蔽的用户ID',
  `blocked_user_id` bigint unsigned NOT NULL COMMENT '被屏蔽的用户ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '屏蔽时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_block` (`user_id`,`blocked_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户屏蔽名单(双向禁止互动收益)';

CREATE TABLE `pet_user_guard` (
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID(主键即锁粒度)',
  `version` bigint unsigned NOT NULL DEFAULT '0' COMMENT '守卫版本(保留列,当前仅作行锁载体)',
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC)',
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户级写锁(并发写路径先 SELECT FOR UPDATE 本行)';

CREATE TABLE `pet_user_sanction` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `user_id` bigint unsigned NOT NULL COMMENT '被处置用户ID',
  `scope` varchar(30) NOT NULL COMMENT '处罚范围: SOCIAL_MUTE(社交写入)/PUBLIC_CONTENT_DISABLED(公开内容)',
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/EXPIRED/REVOKED',
  `starts_at` datetime NOT NULL COMMENT '生效时间(UTC)',
  `expires_at` datetime DEFAULT NULL COMMENT '到期时间(UTC; NULL=需人工解除)',
  `source_report_id` bigint unsigned DEFAULT NULL COMMENT '来源举报ID(可空:运营主动处置)',
  `action` varchar(30) NOT NULL COMMENT '触发动作(WARN/PET_BAN/...)',
  `reason` varchar(255) NOT NULL COMMENT '处罚理由(必填,审计可见)',
  `operator_id` bigint unsigned NOT NULL COMMENT '操作管理员(认证上下文)',
  `revoked_by` bigint unsigned DEFAULT NULL COMMENT '撤销管理员',
  `revoked_reason` varchar(255) DEFAULT NULL COMMENT '撤销理由(撤销必填)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pet_user_sanction_source` (`source_report_id`,`action`),
  KEY `idx_pet_user_sanction_user` (`user_id`,`scope`,`status`),
  KEY `idx_pet_user_sanction_status` (`status`,`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物域用户处罚(封禁只限宠物模块新写入;查看/领奖不受影响)';

CREATE TABLE `pet_visit_fact` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `visitor_user_id` bigint unsigned NOT NULL COMMENT '拜访者用户ID',
  `owner_user_id` bigint unsigned NOT NULL COMMENT '被访主人用户ID',
  `visitor_pet_id` bigint unsigned NOT NULL COMMENT '拜访宠物ID(事实冻结归属)',
  `owner_pet_id` bigint unsigned NOT NULL COMMENT '被访宠物ID',
  `source` enum('NEIGHBOR','ROOM','FRIEND') NOT NULL COMMENT '入口:串门/家园/好友互访',
  `business_date` date NOT NULL COMMENT '业务日(Asia/Shanghai,PetClock 口径)',
  `reward_granted` tinyint NOT NULL DEFAULT '0' COMMENT '本次是否有收益(额度裁决;无收益拜访仍成立)',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC)',
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pet_visit_fact` (`visitor_user_id`,`owner_user_id`,`business_date`),
  KEY `idx_pet_visit_fact_visitor_date` (`visitor_user_id`,`business_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='拜访事实(三入口统一;同主人同业务日至多一次;收益资格由数据库额度裁决)';

CREATE TABLE `pet_wall_like` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `message_id` bigint unsigned NOT NULL COMMENT '留言ID',
  `user_id` bigint unsigned NOT NULL COMMENT '点赞用户ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pet_wall_like` (`message_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物留言点赞(uk幂等,重复点击不重复计数)';

CREATE TABLE `pet_wall_message` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `pet_id` bigint unsigned NOT NULL COMMENT '被留言宠物ID(房间主人)',
  `user_id` bigint unsigned NOT NULL COMMENT '房间主人用户ID',
  `author_user_id` bigint unsigned NOT NULL COMMENT '留言者用户ID',
  `author_pet_id` bigint unsigned DEFAULT NULL COMMENT '留言者主宠ID(展示宠物口吻,可空)',
  `parent_id` bigint unsigned DEFAULT NULL COMMENT '父留言ID(NULL=一级留言,非空=主人回复)',
  `content` varchar(120) NOT NULL COMMENT '留言内容(1-120字)',
  `mood` varchar(20) DEFAULT NULL COMMENT '心情标签(可选,展示用)',
  `status` enum('NORMAL','HIDDEN','DELETED') NOT NULL DEFAULT 'NORMAL' COMMENT '状态:正常/管理员隐藏/已删除',
  `like_count` int NOT NULL DEFAULT '0' COMMENT '点赞数',
  `reply_count` int NOT NULL DEFAULT '0' COMMENT '回复数',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  KEY `idx_pet_wall_owner` (`pet_id`,`status`,`id`),
  KEY `idx_pet_wall_author` (`author_user_id`,`created_at`),
  KEY `idx_pet_wall_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物留言墙留言(管理员可隐藏,幂等点赞)';

CREATE TABLE `pet_wallet_account` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID(钱包按用户建账,多宠共享)',
  `currency` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PET_COIN' COMMENT '币种代码(第一版仅 PET_COIN)',
  `balance` bigint unsigned NOT NULL DEFAULT '0' COMMENT '余额(整数宠物币,禁止浮点)',
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE' COMMENT '账户状态:ACTIVE/FROZEN(冻结允许退款,禁止消费)',
  `version` bigint unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁版本(每笔成功收支+1,与账本 account_version 对齐)',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC)',
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pet_wallet_user_currency` (`user_id`,`currency`),
  CONSTRAINT `ck_pet_wallet_balance` CHECK ((`balance` >= 0)),
  CONSTRAINT `ck_pet_wallet_status` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'FROZEN')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物币账户(按用户,期初0,无自动迁移历史余额)';

CREATE TABLE `pet_wallet_adjustment` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `user_id` bigint unsigned NOT NULL COMMENT '目标用户ID',
  `delta` bigint NOT NULL COMMENT '调账金额(带符号:正=补发,负=扣回;禁止0)',
  `reason` varchar(255) NOT NULL COMMENT '调账原因(必填,审计可见)',
  `ticket_no` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '关联工单号',
  `requested_by` bigint unsigned NOT NULL COMMENT '申请人(管理员ID,取认证上下文)',
  `approved_by` bigint unsigned DEFAULT NULL COMMENT '审批人(管理员ID;必须与申请人不同)',
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING' COMMENT '状态:PENDING/APPROVED/REJECTED',
  `version` bigint unsigned NOT NULL DEFAULT '0' COMMENT '乐观锁(CAS 审批防并发双审)',
  `transaction_id` bigint unsigned DEFAULT NULL COMMENT '入账流水ID(审批成功后回填;重复审批返回原结果)',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '申请时间(UTC)',
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC)',
  `reviewed_at` datetime(6) DEFAULT NULL COMMENT '审批时间(UTC)',
  `review_reason` varchar(255) DEFAULT NULL COMMENT '审批意见（拒绝必填/通过可填；审计可见）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pet_wallet_adjustment_ticket` (`ticket_no`),
  KEY `idx_pet_wallet_adjustment_user` (`user_id`,`created_at`),
  KEY `idx_pet_wallet_adjustment_status` (`status`,`created_at`),
  CONSTRAINT `ck_pet_wallet_adjustment_delta` CHECK ((`delta` <> 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物币调账(申请审批制;不直接改 balance)';

CREATE TABLE `pet_wallet_ledger` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `transaction_id` bigint unsigned NOT NULL COMMENT '流水ID(一一对应)',
  `account_id` bigint unsigned NOT NULL COMMENT '账户ID',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `pet_id` bigint unsigned DEFAULT NULL COMMENT '宠物ID(可空)',
  `delta` bigint NOT NULL COMMENT '带符号变动(EARN/REFUND+,SPEND-)',
  `balance_before` bigint unsigned NOT NULL COMMENT '变动前余额',
  `balance_after` bigint unsigned NOT NULL COMMENT '变动后余额',
  `account_version` bigint unsigned NOT NULL COMMENT '账户版本(=变动后 version)',
  `occurred_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '发生时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pet_wallet_ledger_tx` (`transaction_id`),
  UNIQUE KEY `uk_pet_wallet_ledger_version` (`account_id`,`account_version`),
  KEY `idx_pet_wallet_ledger_user` (`user_id`,`id`),
  CONSTRAINT `ck_pet_wallet_ledger_delta` CHECK ((`delta` <> 0)),
  CONSTRAINT `ck_pet_wallet_ledger_math` CHECK ((`balance_after` = (`balance_before` + `delta`))),
  CONSTRAINT `ck_pet_wallet_ledger_nonnegative` CHECK (((`balance_before` >= 0) and (`balance_after` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物币账本(余额=期初0+SUM(delta),对账基准)';

CREATE TABLE `pet_wallet_ledger_archive` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `account_id` bigint unsigned NOT NULL COMMENT '钱包账户ID',
  `stat_month` char(7) NOT NULL COMMENT '归档月份(UTC, YYYY-MM)',
  `entry_count` bigint NOT NULL DEFAULT '0' COMMENT '流水条数',
  `sum_delta` bigint NOT NULL DEFAULT '0' COMMENT '净变动额(delta 合计)',
  `ending_version` bigint unsigned NOT NULL COMMENT '该月末(截至归档时)最大账本版本',
  `ending_balance_after` bigint NOT NULL COMMENT '该月末(截至归档时)最后一条 balance_after',
  `archived_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '归档时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ledger_archive_account_month` (`account_id`,`stat_month`),
  KEY `idx_ledger_archive_account` (`account_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='钱包账本月度归档汇总(物理清理策略待产品确认)';

CREATE TABLE `pet_wallet_reconcile_item` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `run_id` bigint unsigned NOT NULL COMMENT '对账批次ID',
  `account_id` bigint unsigned NOT NULL COMMENT '账户ID',
  `expected_balance` bigint NOT NULL COMMENT '期望余额(=SUM(delta) 上界内)',
  `actual_balance` bigint NOT NULL COMMENT '实际余额',
  `last_version` bigint unsigned NOT NULL COMMENT '对账时的账户版本上界',
  `diff` bigint NOT NULL COMMENT '差异(actual-expected,<>0)',
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'OPEN' COMMENT '状态:OPEN/RESOLVED(人工处置,不自动改平)',
  `resolution_note` varchar(500) DEFAULT NULL COMMENT '处置结论（必填，调查说明或关联补偿单号）',
  `resolved_by` bigint unsigned DEFAULT NULL COMMENT '处置管理员（mall-admin 认证主体）',
  `resolved_at` datetime DEFAULT NULL COMMENT '处置时间(UTC)',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '发现时间(UTC)',
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pet_wallet_reconcile_item` (`run_id`,`account_id`),
  KEY `idx_pet_wallet_reconcile_item_status` (`status`,`created_at`),
  KEY `idx_reconcile_item_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='对账差异明细(禁止用当前余额覆盖流水;人工处置)';

CREATE TABLE `pet_wallet_reconcile_run` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `cutoff` datetime(6) NOT NULL COMMENT '对账截止时间(UTC)',
  `max_account_id` bigint unsigned DEFAULT NULL COMMENT '本轮账户上界(创建时快照 MAX(id)；该轮只扫 <= 此值)',
  `cursor_account_id` bigint unsigned NOT NULL DEFAULT '0' COMMENT '已扫描到的账户ID游标(keyset 推进，中断后续跑)',
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '状态:RUNNING/COMPLETED/FAILED',
  `account_count` int unsigned NOT NULL DEFAULT '0' COMMENT '扫描账户数',
  `diff_count` int unsigned NOT NULL DEFAULT '0' COMMENT '差异账户数(>0 触发告警)',
  `started_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '开始时间(UTC)',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC)',
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC)',
  `finished_at` datetime(6) DEFAULT NULL COMMENT '结束时间(UTC)',
  PRIMARY KEY (`id`),
  KEY `idx_pet_wallet_reconcile_run_time` (`started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='钱包对账批次(每日全量分片+分钟级抽查基线,§5.5)';

CREATE TABLE `pet_wallet_transaction` (
  `id` bigint unsigned NOT NULL COMMENT '主键(雪花算法)',
  `operation_id` varchar(160) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '操作键(服务器固定格式 pw_:摘要,幂等入口;禁止客户端控制任意超长键)',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `pet_id` bigint unsigned DEFAULT NULL COMMENT '宠物ID(流水可关联,钱包不清空)',
  `currency` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PET_COIN' COMMENT '币种',
  `biz_type` varchar(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务类型:PURCHASE/ACTIVITY_REWARD/BATTLE_REWARD/QUEST_REWARD/REFUND/ADJUSTMENT等(§5.3 业务键表)',
  `biz_key` varchar(160) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务唯一键(§5.3 固定次序摘要,客户端请求键不进入该事实)',
  `direction` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '方向:EARN/REFUND 正向,SPEND 负向,ADJUSTMENT 按审批',
  `amount` bigint unsigned NOT NULL COMMENT '金额(正整数;带符号变化见 ledger.delta)',
  `status` varchar(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '事务状态:COMMITTED/REJECTED(处理中放请求/订单状态,不留半成品)',
  `request_hash` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '请求规范摘要SHA-256(同键不同内容409判定)',
  `original_transaction_id` bigint unsigned DEFAULT NULL COMMENT '原交易ID(退款/冲正关联,累计退款≤原实扣)',
  `rule_version` varchar(40) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '规则/配置版本快照(结算口径)',
  `result_json` json DEFAULT NULL COMMENT '不可变结果快照(实际入账/币种/物品;不存临时签名链接)',
  `error_code` varchar(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT 'REJECTED 时的领域错误码',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC)',
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC)',
  `completed_at` datetime(6) DEFAULT NULL COMMENT '完成时间(UTC)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pet_wallet_operation` (`operation_id`),
  UNIQUE KEY `uk_pet_wallet_fact` (`user_id`,`biz_type`,`biz_key`),
  KEY `idx_pet_wallet_tx_user_time` (`user_id`,`created_at`,`id`),
  KEY `idx_pet_wallet_tx_original` (`original_transaction_id`),
  CONSTRAINT `ck_pet_wallet_tx_amount` CHECK ((`amount` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物币流水(一单一流水,不可变;成功收支唯一事实)';

-- Required catalog/configuration: pet_achievement
INSERT INTO `pet_achievement` (`id`, `code`, `name`, `description`, `icon`, `condition_type`, `condition_subtype`, `condition_value`, `exp_reward`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9003001, 'FIRST_BOTTLE', '第一次捞到漂流瓶', '帮主人捞起了第一只漂流瓶。', '🍾', 'BOTTLE_COUNT', NULL, 1, 20, 1, 1, '2026-10-14 10:25:48', '2026-10-14 10:25:48');
INSERT INTO `pet_achievement` (`id`, `code`, `name`, `description`, `icon`, `condition_type`, `condition_subtype`, `condition_value`, `exp_reward`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9003002, 'BOTTLE_10', '打捞小能手', '累计捞起 10 只漂流瓶。', '🧺', 'BOTTLE_COUNT', NULL, 10, 60, 1, 2, '2026-10-14 10:25:48', '2026-10-14 10:25:48');
INSERT INTO `pet_achievement` (`id`, `code`, `name`, `description`, `icon`, `condition_type`, `condition_subtype`, `condition_value`, `exp_reward`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9003003, 'FIRST_WIN', '初试锋芒', '赢得第一场对战。', '⚔️', 'BATTLE_WIN', NULL, 1, 20, 1, 3, '2026-10-14 10:25:48', '2026-10-14 10:25:48');
INSERT INTO `pet_achievement` (`id`, `code`, `name`, `description`, `icon`, `condition_type`, `condition_subtype`, `condition_value`, `exp_reward`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9003004, 'WIN_10', '擂台常客', '累计赢下 10 场对战。', '🏆', 'BATTLE_WIN', NULL, 10, 80, 1, 4, '2026-10-14 10:25:48', '2026-10-14 10:25:48');
INSERT INTO `pet_achievement` (`id`, `code`, `name`, `description`, `icon`, `condition_type`, `condition_subtype`, `condition_value`, `exp_reward`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9003005, 'LEVEL_10', '成长期', '等级达到 Lv.10。', '🌱', 'LEVEL', NULL, 10, 50, 1, 5, '2026-10-14 10:25:48', '2026-10-14 10:25:48');
INSERT INTO `pet_achievement` (`id`, `code`, `name`, `description`, `icon`, `condition_type`, `condition_subtype`, `condition_value`, `exp_reward`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9003006, 'LEVEL_20', '成年礼', '等级达到 Lv.20。', '🌳', 'LEVEL', NULL, 20, 120, 1, 6, '2026-10-14 10:25:48', '2026-10-14 10:25:48');
INSERT INTO `pet_achievement` (`id`, `code`, `name`, `description`, `icon`, `condition_type`, `condition_subtype`, `condition_value`, `exp_reward`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9003007, 'WORK_10', '打工达人', '累计完成 10 次打工。', '💼', 'ACTIVITY_COUNT', 'WORK', 10, 50, 1, 7, '2026-10-14 10:25:48', '2026-10-14 10:25:48');
INSERT INTO `pet_achievement` (`id`, `code`, `name`, `description`, `icon`, `condition_type`, `condition_subtype`, `condition_value`, `exp_reward`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9003008, 'STUDY_10', '学霸宠物', '累计读完 10 门课程。', '📚', 'ACTIVITY_COUNT', 'STUDY', 10, 60, 1, 8, '2026-10-14 10:25:48', '2026-10-14 10:25:48');
INSERT INTO `pet_achievement` (`id`, `code`, `name`, `description`, `icon`, `condition_type`, `condition_subtype`, `condition_value`, `exp_reward`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9003009, 'FEED_50', '干饭小能手', '被喂食 50 次。', '🍖', 'ACTIVITY_COUNT', 'FEED', 50, 40, 1, 9, '2026-10-14 10:25:48', '2026-10-14 10:25:48');
INSERT INTO `pet_achievement` (`id`, `code`, `name`, `description`, `icon`, `condition_type`, `condition_subtype`, `condition_value`, `exp_reward`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9003010, 'CHAT_100', '话匣子', '和主人聊满 100 句。', '💬', 'CHAT_COUNT', NULL, 100, 60, 1, 10, '2026-10-14 10:25:48', '2026-10-14 10:25:48');
INSERT INTO `pet_achievement` (`id`, `code`, `name`, `description`, `icon`, `condition_type`, `condition_subtype`, `condition_value`, `exp_reward`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9003011, 'STATS_FULL', '全面发展', '四项成长属性全部达到 50。', '🌟', 'STATS_FULL', NULL, 50, 100, 1, 11, '2026-10-14 10:25:48', '2026-10-14 10:25:48');
INSERT INTO `pet_achievement` (`id`, `code`, `name`, `description`, `icon`, `condition_type`, `condition_subtype`, `condition_value`, `exp_reward`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9003012, 'CLEAN_50', '香喷喷', '被清洁 50 次。', '🫧', 'ACTIVITY_COUNT', 'CLEAN', 50, 40, 1, 12, '2026-10-14 10:25:48', '2026-10-14 10:25:48');
INSERT INTO `pet_achievement` (`id`, `code`, `name`, `description`, `icon`, `condition_type`, `condition_subtype`, `condition_value`, `exp_reward`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9003013, 'FIRST_VISIT', '串门新客', '第一次去邻居家串门。', '🚪', 'VISIT_COUNT', NULL, 1, 20, 1, 13, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_achievement` (`id`, `code`, `name`, `description`, `icon`, `condition_type`, `condition_subtype`, `condition_value`, `exp_reward`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9003014, 'VISIT_20', '社区常客', '累计串门 20 次。', '🏘️', 'VISIT_COUNT', NULL, 20, 60, 1, 14, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_achievement` (`id`, `code`, `name`, `description`, `icon`, `condition_type`, `condition_subtype`, `condition_value`, `exp_reward`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9003015, 'EVOLVE_1', '进化之光', '完成第一次进化。', '🌠', 'EVOLUTION', NULL, 1, 80, 1, 15, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_achievement` (`id`, `code`, `name`, `description`, `icon`, `condition_type`, `condition_subtype`, `condition_value`, `exp_reward`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9003016, 'INTIMACY_500', '心有灵犀', '与主人的亲密度达到 500。', '💗', 'INTIMACY', NULL, 500, 80, 1, 16, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_achievement` (`id`, `code`, `name`, `description`, `icon`, `condition_type`, `condition_subtype`, `condition_value`, `exp_reward`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9003017, 'INTIMACY_2000', '形影不离', '与主人的亲密度达到 2000。', '💞', 'INTIMACY', NULL, 2000, 160, 1, 17, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_achievement` (`id`, `code`, `name`, `description`, `icon`, `condition_type`, `condition_subtype`, `condition_value`, `exp_reward`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9003018, 'FIRST_RELATION', '结伴同行', '和一只宠物建立起关系。', '🤝', 'RELATION_COUNT', NULL, 1, 40, 1, 18, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_achievement` (`id`, `code`, `name`, `description`, `icon`, `condition_type`, `condition_subtype`, `condition_value`, `exp_reward`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9003019, 'RELATION_3', '社交达人', '同时拥有 3 段宠物关系。', '👯', 'RELATION_COUNT', NULL, 3, 100, 1, 19, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_achievement` (`id`, `code`, `name`, `description`, `icon`, `condition_type`, `condition_subtype`, `condition_value`, `exp_reward`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9003020, 'FIRST_FRIEND', '第一个朋友', '添加第一位好友。', '🫂', 'FRIEND_COUNT', NULL, 1, 30, 1, 20, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_achievement` (`id`, `code`, `name`, `description`, `icon`, `condition_type`, `condition_subtype`, `condition_value`, `exp_reward`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9003021, 'FRIEND_5', '朋友圈', '好友数量达到 5 位。', '🌈', 'FRIEND_COUNT', NULL, 5, 90, 1, 21, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_achievement` (`id`, `code`, `name`, `description`, `icon`, `condition_type`, `condition_subtype`, `condition_value`, `exp_reward`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9003022, 'WALL_10', '留言小能手', '在别人的留言墙留下 10 条留言。', '📝', 'WALL_MESSAGE_COUNT', NULL, 10, 60, 1, 22, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_achievement` (`id`, `code`, `name`, `description`, `icon`, `condition_type`, `condition_subtype`, `condition_value`, `exp_reward`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9003023, 'COMFORT_60', '温馨小窝', '家园舒适度达到 60。', '🏡', 'ROOM_COMFORT', NULL, 60, 70, 1, 23, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_achievement` (`id`, `code`, `name`, `description`, `icon`, `condition_type`, `condition_subtype`, `condition_value`, `exp_reward`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9003024, 'COMFORT_150', '梦想家园', '家园舒适度达到 150。', '🏰', 'ROOM_COMFORT', NULL, 150, 150, 1, 24, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_achievement` (`id`, `code`, `name`, `description`, `icon`, `condition_type`, `condition_subtype`, `condition_value`, `exp_reward`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9003025, 'QUEST_20', '任务达人', '累计完成 20 个每日任务。', '✅', 'QUEST_COUNT', NULL, 20, 80, 1, 25, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_achievement` (`id`, `code`, `name`, `description`, `icon`, `condition_type`, `condition_subtype`, `condition_value`, `exp_reward`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9003026, 'COMPANION_5H', '陪伴时光', '累计陪伴宠物 5 小时。', '⏳', 'COMPANION_HOURS', NULL, 5, 60, 1, 26, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_achievement` (`id`, `code`, `name`, `description`, `icon`, `condition_type`, `condition_subtype`, `condition_value`, `exp_reward`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9003027, 'COMPANION_50H', '最长情的告白', '累计陪伴宠物 50 小时。', '💫', 'COMPANION_HOURS', NULL, 50, 200, 1, 27, '2026-10-14 10:25:49', '2026-10-14 10:25:49');

-- Required catalog/configuration: pet_career_config
INSERT INTO `pet_career_config` (`id`, `code`, `name`, `description`, `career_line`, `tier`, `icon`, `required_level`, `required_intelligence`, `duration_seconds`, `energy_cost`, `hunger_cost`, `exp_reward`, `currency_reward`, `promote_to_code`, `promote_required_count`, `promote_star_cost`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9009001, 'dessert_apprentice', '甜品学徒', '在后厨学做小蛋糕，围裙上永远沾着糖霜。', '美食', 1, '🧁', 3, 0, 1800, 18, 10, 35, 160, 'cafe_owner', 5, 400, 1, 1, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_career_config` (`id`, `code`, `name`, `description`, `career_line`, `tier`, `icon`, `required_level`, `required_intelligence`, `duration_seconds`, `energy_cost`, `hunger_cost`, `exp_reward`, `currency_reward`, `promote_to_code`, `promote_required_count`, `promote_star_cost`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9009002, 'cafe_owner', '咖啡馆主理人', '自己开了一间街角小店，会拉花了也会算账了。', '美食', 2, '☕', 8, 12, 3600, 28, 15, 65, 300, NULL, 0, 0, 1, 2, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_career_config` (`id`, `code`, `name`, `description`, `career_line`, `tier`, `icon`, `required_level`, `required_intelligence`, `duration_seconds`, `energy_cost`, `hunger_cost`, `exp_reward`, `currency_reward`, `promote_to_code`, `promote_required_count`, `promote_star_cost`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9009003, 'junior_explorer', '见习探险家', '跟着前辈认识山路与星图，背包里装满了石头。', '探险', 1, '🧭', 3, 0, 2400, 20, 12, 38, 170, 'ruin_guide', 5, 420, 1, 3, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_career_config` (`id`, `code`, `name`, `description`, `career_line`, `tier`, `icon`, `required_level`, `required_intelligence`, `duration_seconds`, `energy_cost`, `hunger_cost`, `exp_reward`, `currency_reward`, `promote_to_code`, `promote_required_count`, `promote_star_cost`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9009004, 'ruin_guide', '遗迹向导', '能带社区的小伙伴安全穿过遗迹，讲一路的故事。', '探险', 2, '🗺️', 8, 10, 3600, 30, 18, 70, 320, NULL, 0, 0, 1, 4, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_career_config` (`id`, `code`, `name`, `description`, `career_line`, `tier`, `icon`, `required_level`, `required_intelligence`, `duration_seconds`, `energy_cost`, `hunger_cost`, `exp_reward`, `currency_reward`, `promote_to_code`, `promote_required_count`, `promote_star_cost`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9009005, 'street_painter', '街头画家', '在社区广场给路人画速写，一天能画满一本。', '艺术', 1, '🎨', 3, 0, 1800, 16, 8, 32, 150, 'mural_master', 5, 380, 1, 5, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_career_config` (`id`, `code`, `name`, `description`, `career_line`, `tier`, `icon`, `required_level`, `required_intelligence`, `duration_seconds`, `energy_cost`, `hunger_cost`, `exp_reward`, `currency_reward`, `promote_to_code`, `promote_required_count`, `promote_star_cost`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9009006, 'mural_master', '壁画师', '把自己的画留在了社区文化墙上，很多年都不会褪色。', '艺术', 2, '🖌️', 8, 15, 3600, 26, 14, 68, 310, NULL, 0, 0, 1, 6, '2026-10-14 10:25:49', '2026-10-14 10:25:49');

-- Required catalog/configuration: pet_content_sensitive_word
INSERT INTO `pet_content_sensitive_word` (`id`, `word`, `category`, `status`, `created_at`, `updated_at`) VALUES (1930000000000000001, '自杀', 'CRISIS', 1, '2026-10-14 10:25:52', '2026-10-14 10:25:52.470');
INSERT INTO `pet_content_sensitive_word` (`id`, `word`, `category`, `status`, `created_at`, `updated_at`) VALUES (1930000000000000002, '轻生', 'CRISIS', 1, '2026-10-14 10:25:52', '2026-10-14 10:25:52.470');
INSERT INTO `pet_content_sensitive_word` (`id`, `word`, `category`, `status`, `created_at`, `updated_at`) VALUES (1930000000000000003, '不想活', 'CRISIS', 1, '2026-10-14 10:25:52', '2026-10-14 10:25:52.470');
INSERT INTO `pet_content_sensitive_word` (`id`, `word`, `category`, `status`, `created_at`, `updated_at`) VALUES (1930000000000000004, '想死', 'CRISIS', 1, '2026-10-14 10:25:52', '2026-10-14 10:25:52.470');
INSERT INTO `pet_content_sensitive_word` (`id`, `word`, `category`, `status`, `created_at`, `updated_at`) VALUES (1930000000000000005, '自残', 'CRISIS', 1, '2026-10-14 10:25:52', '2026-10-14 10:25:52.470');
INSERT INTO `pet_content_sensitive_word` (`id`, `word`, `category`, `status`, `created_at`, `updated_at`) VALUES (1930000000000000006, '结束生命', 'CRISIS', 1, '2026-10-14 10:25:52', '2026-10-14 10:25:52.470');
INSERT INTO `pet_content_sensitive_word` (`id`, `word`, `category`, `status`, `created_at`, `updated_at`) VALUES (1930000000000000007, '加微信', 'AD', 1, '2026-10-14 10:25:52', '2026-10-14 10:25:52.470');
INSERT INTO `pet_content_sensitive_word` (`id`, `word`, `category`, `status`, `created_at`, `updated_at`) VALUES (1930000000000000008, '加QQ', 'AD', 1, '2026-10-14 10:25:52', '2026-10-14 10:25:52.470');
INSERT INTO `pet_content_sensitive_word` (`id`, `word`, `category`, `status`, `created_at`, `updated_at`) VALUES (1930000000000000009, '兼职刷单', 'AD', 1, '2026-10-14 10:25:52', '2026-10-14 10:25:52.470');
INSERT INTO `pet_content_sensitive_word` (`id`, `word`, `category`, `status`, `created_at`, `updated_at`) VALUES (1930000000000000010, '免费领红包', 'AD', 1, '2026-10-14 10:25:52', '2026-10-14 10:25:52.470');
INSERT INTO `pet_content_sensitive_word` (`id`, `word`, `category`, `status`, `created_at`, `updated_at`) VALUES (1930000000000000011, '代练', 'AD', 1, '2026-10-14 10:25:52', '2026-10-14 10:25:52.470');
INSERT INTO `pet_content_sensitive_word` (`id`, `word`, `category`, `status`, `created_at`, `updated_at`) VALUES (1930000000000000012, '傻逼', 'ABUSE', 1, '2026-10-14 10:25:52', '2026-10-14 10:25:52.470');
INSERT INTO `pet_content_sensitive_word` (`id`, `word`, `category`, `status`, `created_at`, `updated_at`) VALUES (1930000000000000013, '滚蛋', 'ABUSE', 1, '2026-10-14 10:25:52', '2026-10-14 10:25:52.470');
INSERT INTO `pet_content_sensitive_word` (`id`, `word`, `category`, `status`, `created_at`, `updated_at`) VALUES (1930000000000000014, '废物', 'ABUSE', 1, '2026-10-14 10:25:52', '2026-10-14 10:25:52.470');
INSERT INTO `pet_content_sensitive_word` (`id`, `word`, `category`, `status`, `created_at`, `updated_at`) VALUES (1930000000000000015, '脑残', 'ABUSE', 1, '2026-10-14 10:25:52', '2026-10-14 10:25:52.470');

-- Required catalog/configuration: pet_daily_quest_config
INSERT INTO `pet_daily_quest_config` (`id`, `code`, `name`, `description`, `icon`, `quest_type`, `target_value`, `exp_reward`, `currency_reward`, `required_level`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9011001, 'daily_feed', '好好吃饭', '喂食 2 次。', '🍖', 'FEED', 2, 20, 20, 1, 1, 1, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_daily_quest_config` (`id`, `code`, `name`, `description`, `icon`, `quest_type`, `target_value`, `exp_reward`, `currency_reward`, `required_level`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9011002, 'daily_play', '一起玩耍', '陪宠物玩耍 2 次。', '🎾', 'PLAY', 2, 20, 20, 1, 1, 2, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_daily_quest_config` (`id`, `code`, `name`, `description`, `icon`, `quest_type`, `target_value`, `exp_reward`, `currency_reward`, `required_level`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9011003, 'daily_clean', '干干净净', '给宠物清洁 1 次。', '🫧', 'CLEAN', 1, 15, 10, 1, 1, 3, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_daily_quest_config` (`id`, `code`, `name`, `description`, `icon`, `quest_type`, `target_value`, `exp_reward`, `currency_reward`, `required_level`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9011004, 'daily_work', '认真打工', '完成 1 次打工。', '💼', 'WORK', 1, 25, 30, 1, 1, 4, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_daily_quest_config` (`id`, `code`, `name`, `description`, `icon`, `quest_type`, `target_value`, `exp_reward`, `currency_reward`, `required_level`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9011005, 'daily_study', '每天读书', '完成 1 次读书。', '📚', 'STUDY', 1, 25, 30, 1, 1, 5, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_daily_quest_config` (`id`, `code`, `name`, `description`, `icon`, `quest_type`, `target_value`, `exp_reward`, `currency_reward`, `required_level`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9011006, 'daily_bottle', '试试手气', '完成 1 次捞漂流瓶。', '🍾', 'BOTTLE', 1, 25, 30, 1, 1, 6, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_daily_quest_config` (`id`, `code`, `name`, `description`, `icon`, `quest_type`, `target_value`, `exp_reward`, `currency_reward`, `required_level`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9011007, 'daily_battle', '切磋一下', '完成 1 场对战。', '⚔️', 'BATTLE', 1, 25, 30, 3, 1, 7, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_daily_quest_config` (`id`, `code`, `name`, `description`, `icon`, `quest_type`, `target_value`, `exp_reward`, `currency_reward`, `required_level`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9011008, 'daily_social', '出门交朋友', '串门或好友互访 1 次。', '🚪', 'VISIT', 1, 20, 20, 1, 1, 8, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_daily_quest_config` (`id`, `code`, `name`, `description`, `icon`, `quest_type`, `target_value`, `exp_reward`, `currency_reward`, `required_level`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9011009, 'daily_chat', '聊聊天', '和宠物聊天 3 句。', '💬', 'CHAT', 3, 15, 10, 1, 1, 9, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_daily_quest_config` (`id`, `code`, `name`, `description`, `icon`, `quest_type`, `target_value`, `exp_reward`, `currency_reward`, `required_level`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9011010, 'daily_decorate', '布置小窝', '摆放或更换 1 件家具。', '🧸', 'DECORATE', 1, 20, 20, 2, 1, 10, '2026-10-14 10:25:49', '2026-10-14 10:25:49');

-- Required catalog/configuration: pet_equipment_config
INSERT INTO `pet_equipment_config` (`id`, `code`, `name`, `description`, `slot`, `icon`, `rarity`, `price_starlight`, `bonus_strength`, `bonus_intelligence`, `bonus_agility`, `bonus_charm`, `bonus_max_hp`, `required_level`, `required_evolution_stage`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9004001, 'straw_hat', '草编渔夫帽', '海边散步必备，据说戴着更容易捞到漂流瓶。', 'HAT', '👒', 'COMMON', 120, 0, 0, 1, 1, 0, 1, 0, 1, 1, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_equipment_config` (`id`, `code`, `name`, `description`, `slot`, `icon`, `rarity`, `price_starlight`, `bonus_strength`, `bonus_intelligence`, `bonus_agility`, `bonus_charm`, `bonus_max_hp`, `required_level`, `required_evolution_stage`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9004002, 'explorer_cap', '探险家鸭舌帽', '后山探险队的队帽，耐磨又精神。', 'HAT', '🧢', 'RARE', 320, 1, 1, 1, 1, 5, 5, 0, 1, 2, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_equipment_config` (`id`, `code`, `name`, `description`, `slot`, `icon`, `rarity`, `price_starlight`, `bonus_strength`, `bonus_intelligence`, `bonus_agility`, `bonus_charm`, `bonus_max_hp`, `required_level`, `required_evolution_stage`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9004003, 'bell_collar', '叮当项圈', '走一步响一声，主人再也不会找不到我。', 'NECKLACE', '🔔', 'COMMON', 150, 0, 0, 2, 0, 0, 1, 0, 1, 3, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_equipment_config` (`id`, `code`, `name`, `description`, `slot`, `icon`, `rarity`, `price_starlight`, `bonus_strength`, `bonus_intelligence`, `bonus_agility`, `bonus_charm`, `bonus_max_hp`, `required_level`, `required_evolution_stage`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9004004, 'crystal_pendant', '星辉吊坠', '把捞到的第一颗星星碎片挂在了脖子上。', 'NECKLACE', '💎', 'EPIC', 680, 0, 3, 0, 4, 10, 10, 1, 1, 4, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_equipment_config` (`id`, `code`, `name`, `description`, `slot`, `icon`, `rarity`, `price_starlight`, `bonus_strength`, `bonus_intelligence`, `bonus_agility`, `bonus_charm`, `bonus_max_hp`, `required_level`, `required_evolution_stage`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9004005, 'knit_scarf', '针织围巾', '冬天读书时最舒服的装备。', 'SCARF', '🧣', 'COMMON', 130, 0, 2, 0, 0, 0, 1, 0, 1, 5, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_equipment_config` (`id`, `code`, `name`, `description`, `slot`, `icon`, `rarity`, `price_starlight`, `bonus_strength`, `bonus_intelligence`, `bonus_agility`, `bonus_charm`, `bonus_max_hp`, `required_level`, `required_evolution_stage`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9004006, 'silk_scarf', '丝绒披巾', '在社区舞台上一亮相就很吸睛。', 'SCARF', '🎀', 'RARE', 380, 1, 0, 0, 3, 0, 6, 0, 1, 6, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_equipment_config` (`id`, `code`, `name`, `description`, `slot`, `icon`, `rarity`, `price_starlight`, `bonus_strength`, `bonus_intelligence`, `bonus_agility`, `bonus_charm`, `bonus_max_hp`, `required_level`, `required_evolution_stage`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9004007, 'canvas_pack', '帆布小书包', '装得下三本课外书和一块干粮。', 'BACKPACK', '🎒', 'COMMON', 160, 2, 0, 0, 0, 0, 1, 0, 1, 7, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_equipment_config` (`id`, `code`, `name`, `description`, `slot`, `icon`, `rarity`, `price_starlight`, `bonus_strength`, `bonus_intelligence`, `bonus_agility`, `bonus_charm`, `bonus_max_hp`, `required_level`, `required_evolution_stage`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9004008, 'star_pack', '星轨行囊', '据说是探险家前辈留下的行囊，装着整片星空。', 'BACKPACK', '🌌', 'EPIC', 720, 2, 2, 2, 2, 15, 12, 1, 1, 8, '2026-10-14 10:25:49', '2026-10-14 10:25:49');

-- Required catalog/configuration: pet_event_config
INSERT INTO `pet_event_config` (`id`, `code`, `name`, `description`, `event_type`, `target_value`, `reward_starlight`, `reward_exp`, `reward_item_code`, `reward_alt_starlight`, `starts_at`, `ends_at`, `enabled`, `event_mode`, `sort`, `created_at`, `updated_at`) VALUES (9008001, 'bottle_newbie', '捞瓶新星', '累计让宠物捞起 3 只漂流瓶（含空手而归的任务完成次数）。', 'BOTTLE', 3, 120, 40, NULL, 20, NULL, NULL, 1, 'LIFETIME', 1, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_event_config` (`id`, `code`, `name`, `description`, `event_type`, `target_value`, `reward_starlight`, `reward_exp`, `reward_item_code`, `reward_alt_starlight`, `starts_at`, `ends_at`, `enabled`, `event_mode`, `sort`, `created_at`, `updated_at`) VALUES (9008002, 'work_week', '打工小能手', '累计完成 5 次打工。', 'WORK', 5, 200, 60, NULL, 20, NULL, NULL, 1, 'LIFETIME', 2, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_event_config` (`id`, `code`, `name`, `description`, `event_type`, `target_value`, `reward_starlight`, `reward_exp`, `reward_item_code`, `reward_alt_starlight`, `starts_at`, `ends_at`, `enabled`, `event_mode`, `sort`, `created_at`, `updated_at`) VALUES (9008003, 'visit_week', '串门周', '去邻居家串门 5 次，替主人交个朋友。', 'VISIT', 5, 180, 50, NULL, 20, NULL, NULL, 1, 'LIFETIME', 3, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_event_config` (`id`, `code`, `name`, `description`, `event_type`, `target_value`, `reward_starlight`, `reward_exp`, `reward_item_code`, `reward_alt_starlight`, `starts_at`, `ends_at`, `enabled`, `event_mode`, `sort`, `created_at`, `updated_at`) VALUES (9008004, 'battle_fest', '对战狂欢季', '活动期间赢下 3 场对战，赢取星轨行囊。', 'BATTLE', 3, 260, 80, 'star_pack', 20, '2026-10-13 10:25:49', '2027-01-12 10:25:49', 1, 'LIFETIME', 4, '2026-10-14 10:25:49', '2026-10-14 10:25:49');

-- Required catalog/configuration: pet_evolution_config
INSERT INTO `pet_evolution_config` (`id`, `code`, `name`, `description`, `stage_from`, `stage_to`, `required_level`, `cost_starlight`, `bonus_max_hp`, `bonus_strength`, `bonus_intelligence`, `bonus_agility`, `bonus_charm`, `unlock_skin_code`, `icon`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9007001, 'evolve_1', '初阶进化', '毛发泛起微光，身体更结实了。', 0, 1, 8, 600, 25, 3, 3, 3, 3, 'aurora_legend', '🌠', 1, 1, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_evolution_config` (`id`, `code`, `name`, `description`, `stage_from`, `stage_to`, `required_level`, `cost_starlight`, `bonus_max_hp`, `bonus_strength`, `bonus_intelligence`, `bonus_agility`, `bonus_charm`, `unlock_skin_code`, `icon`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9007002, 'evolve_2', '高阶进化', '气场全开，社区里谁见了都要夸一句。', 1, 2, 18, 1500, 50, 6, 6, 6, 6, NULL, '✨', 1, 2, '2026-10-14 10:25:49', '2026-10-14 10:25:49');

-- Required catalog/configuration: pet_food_config
INSERT INTO `pet_food_config` (`id`, `code`, `name`, `icon`, `description`, `price_starlight`, `hunger`, `happiness`, `hp`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (1940000000000000001, 'apple', '苹果', '🍎', '脆脆的苹果，宠物最爱', 20, 15, 2, 0, 1, 1, '2026-10-14 10:25:52', '2026-10-14 10:25:52');
INSERT INTO `pet_food_config` (`id`, `code`, `name`, `icon`, `description`, `price_starlight`, `hunger`, `happiness`, `hp`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (1940000000000000002, 'milk', '牛奶', '🥛', '温热的一杯牛奶', 25, 10, 8, 5, 1, 2, '2026-10-14 10:25:52', '2026-10-14 10:25:52');
INSERT INTO `pet_food_config` (`id`, `code`, `name`, `icon`, `description`, `price_starlight`, `hunger`, `happiness`, `hp`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (1940000000000000003, 'fish', '小鱼干', '🐟', '香喷喷的小鱼干', 35, 25, 5, 10, 1, 3, '2026-10-14 10:25:52', '2026-10-14 10:25:52');
INSERT INTO `pet_food_config` (`id`, `code`, `name`, `icon`, `description`, `price_starlight`, `hunger`, `happiness`, `hp`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (1940000000000000004, 'cake', '奶油蛋糕', '🍰', '节日限定的甜品', 60, 40, 12, 15, 1, 4, '2026-10-14 10:25:52', '2026-10-14 10:25:52');

-- Required catalog/configuration: pet_furniture_config
INSERT INTO `pet_furniture_config` (`id`, `code`, `name`, `description`, `category`, `icon`, `rarity`, `price_starlight`, `required_level`, `comfort`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9010001, 'wall_cloud', '云朵壁纸', '像被云朵包住的小房间，安静又柔软。', 'WALL', '☁️', 'COMMON', 120, 1, 4, 1, 1, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_furniture_config` (`id`, `code`, `name`, `description`, `category`, `icon`, `rarity`, `price_starlight`, `required_level`, `comfort`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9010002, 'wall_starry', '星空壁纸', '关灯以后会看到一整片星空。', 'WALL', '🌌', 'RARE', 320, 5, 8, 1, 2, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_furniture_config` (`id`, `code`, `name`, `description`, `category`, `icon`, `rarity`, `price_starlight`, `required_level`, `comfort`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9010003, 'floor_wood', '原木地板', '踩上去会发出好听的脚步声。', 'FLOOR', '🪵', 'COMMON', 120, 1, 4, 1, 3, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_furniture_config` (`id`, `code`, `name`, `description`, `category`, `icon`, `rarity`, `price_starlight`, `required_level`, `comfort`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9010004, 'floor_matcha', '抹茶地毯', '软软的抹茶色地毯，坐着就不想起来。', 'FLOOR', '🍵', 'RARE', 300, 5, 8, 1, 4, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_furniture_config` (`id`, `code`, `name`, `description`, `category`, `icon`, `rarity`, `price_starlight`, `required_level`, `comfort`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9010005, 'sofa_cloud', '懒人沙发', '陷进去以后需要一点意志力才能起来。', 'FURNITURE', '🛋️', 'COMMON', 180, 2, 6, 1, 5, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_furniture_config` (`id`, `code`, `name`, `description`, `category`, `icon`, `rarity`, `price_starlight`, `required_level`, `comfort`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9010006, 'desk_oak', '原木书桌', '读书写字的专属位置，桌面还留着铅笔印。', 'FURNITURE', '🪑', 'RARE', 360, 4, 10, 1, 6, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_furniture_config` (`id`, `code`, `name`, `description`, `category`, `icon`, `rarity`, `price_starlight`, `required_level`, `comfort`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9010007, 'plant_pothos', '窗台绿萝', '不用太多照顾也能活得很好。', 'PLANT', '🪴', 'COMMON', 140, 1, 5, 1, 7, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_furniture_config` (`id`, `code`, `name`, `description`, `category`, `icon`, `rarity`, `price_starlight`, `required_level`, `comfort`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9010008, 'plant_bonsai', '小盆景', '需要每天浇一点点水，会长得很慢。', 'PLANT', '🌿', 'RARE', 340, 6, 9, 1, 8, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_furniture_config` (`id`, `code`, `name`, `description`, `category`, `icon`, `rarity`, `price_starlight`, `required_level`, `comfort`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9010009, 'toy_yarn', '毛线球', '滚到沙发底下过三次，被找回来三次。', 'TOY', '🧶', 'COMMON', 100, 1, 3, 1, 9, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_furniture_config` (`id`, `code`, `name`, `description`, `category`, `icon`, `rarity`, `price_starlight`, `required_level`, `comfort`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9010010, 'toy_ball', '弹力球', '一弹就能弹到房间另一头。', 'TOY', '⚽', 'COMMON', 110, 1, 3, 1, 10, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_furniture_config` (`id`, `code`, `name`, `description`, `category`, `icon`, `rarity`, `price_starlight`, `required_level`, `comfort`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9010011, 'bed_cotton', '棉花小窝', '睡进去只露出一个鼻尖。', 'BED', '🛏️', 'COMMON', 200, 2, 7, 1, 11, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_furniture_config` (`id`, `code`, `name`, `description`, `category`, `icon`, `rarity`, `price_starlight`, `required_level`, `comfort`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9010012, 'bed_starmoon', '星月吊床', '据说睡在这里的宠物会做关于星星的梦。', 'BED', '🌙', 'EPIC', 680, 8, 14, 1, 12, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_furniture_config` (`id`, `code`, `name`, `description`, `category`, `icon`, `rarity`, `price_starlight`, `required_level`, `comfort`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9010101, 'starter_rug', '新手奶油地毯', '领养时收到的小地毯，奶油色的第一份家的味道。', 'FURNITURE', '🧶', 'COMMON', 0, 1, 1, 1, 20, '2026-10-14 10:25:53', '2026-10-14 10:25:53');
INSERT INTO `pet_furniture_config` (`id`, `code`, `name`, `description`, `category`, `icon`, `rarity`, `price_starlight`, `required_level`, `comfort`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9010102, 'cooperation_badge', '合作纪念徽章', '和好友一起完成合作的纪念徽章。', 'FURNITURE', '🎖️', 'RARE', 0, 1, 2, 1, 21, '2026-10-14 10:25:53', '2026-10-14 10:25:53');

-- Required catalog/configuration: pet_job_config
INSERT INTO `pet_job_config` (`id`, `name`, `description`, `duration_seconds`, `energy_cost`, `hunger_cost`, `exp_reward`, `currency_reward`, `required_level`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9001001, '送外卖', '骑着小电驴穿梭在城市的大街小巷，风里来雨里去。', 1800, 20, 10, 25, 100, 1, 1, 1, '2026-10-14 10:25:48', '2026-10-14 10:25:48');
INSERT INTO `pet_job_config` (`id`, `name`, `description`, `duration_seconds`, `energy_cost`, `hunger_cost`, `exp_reward`, `currency_reward`, `required_level`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9001002, '咖啡店兼职', '在街角咖啡店帮忙拉花，闻着咖啡香打一下午工。', 1800, 15, 5, 20, 100, 1, 1, 2, '2026-10-14 10:25:48', '2026-10-14 10:25:48');
INSERT INTO `pet_job_config` (`id`, `name`, `description`, `duration_seconds`, `energy_cost`, `hunger_cost`, `exp_reward`, `currency_reward`, `required_level`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9001003, '图书管理员', '整理书架、登记借阅，顺便偷看几页故事书。', 3600, 20, 10, 40, 180, 5, 1, 3, '2026-10-14 10:25:48', '2026-10-14 10:25:48');
INSERT INTO `pet_job_config` (`id`, `name`, `description`, `duration_seconds`, `energy_cost`, `hunger_cost`, `exp_reward`, `currency_reward`, `required_level`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9001004, '快递分拣员', '把包裹按地址分门别类，考验细心和耐力。', 2700, 25, 12, 35, 150, 3, 1, 4, '2026-10-14 10:25:48', '2026-10-14 10:25:48');
INSERT INTO `pet_job_config` (`id`, `name`, `description`, `duration_seconds`, `energy_cost`, `hunger_cost`, `exp_reward`, `currency_reward`, `required_level`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9001005, '小探险家', '去社区后山探一条新小路，带回有趣的石头和故事。', 5400, 35, 20, 70, 300, 10, 1, 5, '2026-10-14 10:25:48', '2026-10-14 10:25:48');

-- Required catalog/configuration: pet_season_guard
INSERT INTO `pet_season_guard` (`id`) VALUES (1);

-- Required catalog/configuration: pet_skill_config
INSERT INTO `pet_skill_config` (`id`, `code`, `name`, `description`, `skill_type`, `effect`, `effect_value`, `icon`, `price_starlight`, `required_level`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9006001, 'power_strike', '大力一击', '主动技：首回合全力出击，伤害提升 35%。', 'ACTIVE', 'POWER_STRIKE', 0.350, '💥', 500, 3, 1, 1, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_skill_config` (`id`, `code`, `name`, `description`, `skill_type`, `effect`, `effect_value`, `icon`, `price_starlight`, `required_level`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9006002, 'lucky_fish', '幸运打捞', '被动技：捞漂流瓶成功率 +8%。', 'PASSIVE', 'LUCKY_FISH', 0.080, '🍀', 420, 2, 1, 2, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_skill_config` (`id`, `code`, `name`, `description`, `skill_type`, `effect`, `effect_value`, `icon`, `price_starlight`, `required_level`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9006003, 'quick_step', '迅捷身法', '被动技：战斗先手判定敏捷 +3。', 'PASSIVE', 'QUICK_STEP', 3.000, '💨', 380, 4, 1, 3, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_skill_config` (`id`, `code`, `name`, `description`, `skill_type`, `effect`, `effect_value`, `icon`, `price_starlight`, `required_level`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9006004, 'bookworm', '博览群书', '被动技：读书经验收益 +15%。', 'PASSIVE', 'BOOKWORM', 0.150, '📖', 450, 3, 1, 4, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_skill_config` (`id`, `code`, `name`, `description`, `skill_type`, `effect`, `effect_value`, `icon`, `price_starlight`, `required_level`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9006005, 'charm_aura', '魅力光环', '被动技：战斗暴击率 +6%。', 'PASSIVE', 'CHARM_AURA', 0.060, '💖', 480, 5, 1, 5, '2026-10-14 10:25:49', '2026-10-14 10:25:49');
INSERT INTO `pet_skill_config` (`id`, `code`, `name`, `description`, `skill_type`, `effect`, `effect_value`, `icon`, `price_starlight`, `required_level`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9006006, 'tough_body', '硬朗体魄', '被动技：战斗中受到的伤害降低 12%。', 'PASSIVE', 'TOUGH_BODY', 0.120, '🛡️', 520, 6, 1, 6, '2026-10-14 10:25:49', '2026-10-14 10:25:49');

-- Required catalog/configuration: pet_skin_config
INSERT INTO `pet_skin_config` (`id`, `code`, `name`, `description`, `species`, `color`, `accessory`, `icon`, `rarity`, `price_starlight`, `required_level`, `required_evolution_stage`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9005006, 'aurora_legend', '极光传说', '只有完成一阶进化的小宠物才能驾驭的极光色。', NULL, 'aurora', 'crown', '👑', 'EPIC', 880, 10, 1, 1, 6, '2026-10-14 10:25:49', '2026-10-14 10:25:49');

-- Required catalog/configuration: pet_study_config
INSERT INTO `pet_study_config` (`id`, `name`, `description`, `category`, `duration_seconds`, `energy_cost`, `exp_reward`, `intelligence_reward`, `required_level`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9002001, '《编程入门》', '从 Hello World 开始，逻辑思维启蒙。', '编程', 3600, 20, 50, 2, 1, 1, 1, '2026-10-14 10:25:48', '2026-10-14 10:25:48');
INSERT INTO `pet_study_config` (`id`, `name`, `description`, `category`, `duration_seconds`, `energy_cost`, `exp_reward`, `intelligence_reward`, `required_level`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9002002, '《文学欣赏》', '读几篇短篇故事，学着感受文字的美。', '文学', 3600, 15, 40, 2, 1, 1, 2, '2026-10-14 10:25:48', '2026-10-14 10:25:48');
INSERT INTO `pet_study_config` (`id`, `name`, `description`, `category`, `duration_seconds`, `energy_cost`, `exp_reward`, `intelligence_reward`, `required_level`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9002003, '《历史漫谈》', '朝代更迭小故事，越听越精神。', '历史', 3600, 15, 40, 2, 1, 1, 3, '2026-10-14 10:25:48', '2026-10-14 10:25:48');
INSERT INTO `pet_study_config` (`id`, `name`, `description`, `category`, `duration_seconds`, `energy_cost`, `exp_reward`, `intelligence_reward`, `required_level`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9002004, '《科学小实验》', '为什么天是蓝的？一起找答案。', '科学', 3600, 20, 45, 2, 1, 1, 4, '2026-10-14 10:25:48', '2026-10-14 10:25:48');
INSERT INTO `pet_study_config` (`id`, `name`, `description`, `category`, `duration_seconds`, `energy_cost`, `exp_reward`, `intelligence_reward`, `required_level`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9002005, '《艺术涂鸦》', '画一只想象中的小怪兽。', '艺术', 2700, 15, 35, 2, 1, 1, 5, '2026-10-14 10:25:48', '2026-10-14 10:25:48');
INSERT INTO `pet_study_config` (`id`, `name`, `description`, `category`, `duration_seconds`, `energy_cost`, `exp_reward`, `intelligence_reward`, `required_level`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9002006, '《社交礼仪》', '怎么打招呼、怎么交朋友。', '社交', 2700, 10, 30, 2, 1, 1, 6, '2026-10-14 10:25:48', '2026-10-14 10:25:48');
INSERT INTO `pet_study_config` (`id`, `name`, `description`, `category`, `duration_seconds`, `energy_cost`, `exp_reward`, `intelligence_reward`, `required_level`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9002007, '《心理小课堂》', '认识情绪，做情绪的小主人。', '心理', 2700, 10, 30, 2, 1, 1, 7, '2026-10-14 10:25:48', '2026-10-14 10:25:48');
INSERT INTO `pet_study_config` (`id`, `name`, `description`, `category`, `duration_seconds`, `energy_cost`, `exp_reward`, `intelligence_reward`, `required_level`, `enabled`, `sort`, `created_at`, `updated_at`) VALUES (9002008, '《冒险图鉴》', '野外辨向与安全常识，为探险做准备。', '冒险', 5400, 25, 60, 3, 5, 1, 8, '2026-10-14 10:25:48', '2026-10-14 10:25:48');
