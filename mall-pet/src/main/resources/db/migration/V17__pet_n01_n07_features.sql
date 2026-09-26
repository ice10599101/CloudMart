-- V17: N01–N07 七项新增功能数据表（§5 全部实现后端；收益一律走 B01 操作记录 + B06 配额）

-- N01 新手引导：每用户每引导版本唯一；领域事件驱动，不能客户端提交 completed
CREATE TABLE IF NOT EXISTS `pet_onboarding_progress` (
    `id`                   BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `user_id`              BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `guide_version`        VARCHAR(20) NOT NULL DEFAULT 'V1' COMMENT '引导版本',
    `steps`                JSON NOT NULL COMMENT '步骤状态JSON:{FEED/PLAY/WORK/DECORATE: LOCKED|OPEN|DONE}',
    `skipped_at`           DATETIME DEFAULT NULL COMMENT '跳过时间(UTC)',
    `completed_at`         DATETIME DEFAULT NULL COMMENT '完成时间(UTC)',
    `furniture_grant_op_id` VARCHAR(80) DEFAULT NULL COMMENT '基础家具赠送操作键(每用户一次,幂等)',
    `created_at`           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_onboarding_progress` (`id`),
    UNIQUE KEY `uk_onboarding_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='新手引导进度(可恢复,领域事件驱动)';

-- N02 成长日记与相册
CREATE TABLE IF NOT EXISTS `pet_diary_entry` (
    `id`          BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `pet_id`      BIGINT UNSIGNED NOT NULL COMMENT '宠物ID',
    `user_id`     BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `event_id`    VARCHAR(120) NOT NULL COMMENT '业务事件唯一键(去重,禁止伪造)',
    `event_type`  VARCHAR(40) NOT NULL COMMENT '事件类型: ADOPTED/LEVEL_UP/FIRST_WORK/FIRST_WIN/ACHIEVEMENT/EVOLVED/RELATION',
    `occurred_at` DATETIME NOT NULL COMMENT '发生时间(UTC,按事件实际时间归属)',
    `snapshot`    JSON NOT NULL COMMENT '不可变快照(名字/等级/外观资源键/参数)',
    `visibility`  ENUM('OWNER_ONLY','PUBLIC') NOT NULL DEFAULT 'OWNER_ONLY' COMMENT '可见性(公开单条须明确请求)',
    `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_diary_entry` (`id`),
    UNIQUE KEY `uk_diary_event` (`pet_id`, `event_id`),
    INDEX `idx_diary_pet_time` (`pet_id`, `occurred_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='成长日记(领域事件生成,不可变快照)';

CREATE TABLE IF NOT EXISTS `pet_album_asset` (
    `id`              BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `user_id`         BIGINT UNSIGNED NOT NULL COMMENT '上传者(=宠物主人,归属校验)',
    `pet_id`          BIGINT UNSIGNED NOT NULL COMMENT '关联宠物ID',
    `diary_entry_id`  BIGINT UNSIGNED DEFAULT NULL COMMENT '关联日记ID(可空)',
    `file_id`         VARCHAR(64) NOT NULL COMMENT 'mall-file 资源 ID(授权可访问引用)',
    `audit_status`    ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'APPROVED' COMMENT '审核状态',
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_album_asset` (`id`),
    UNIQUE KEY `uk_album_file` (`file_id`),
    INDEX `idx_album_pet` (`pet_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物相册(仅主人可见,静态图片,5MB/100张限额集中配置)';

-- N03 记忆管理扩展（pet_memory V1 已有 (pet_id, memory_key)）
ALTER TABLE `pet_memory`
    ADD COLUMN `source` ENUM('AUTO','USER') NOT NULL DEFAULT 'AUTO' COMMENT '来源(用户编辑优先于自动抽取)' AFTER `memory_value`,
    ADD COLUMN `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '启用状态(删除=0,删除标记防复活)' AFTER `confidence`;

-- N04 接球小游戏
CREATE TABLE IF NOT EXISTS `pet_minigame_round` (
    `id`                BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `user_id`           BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `pet_id`            BIGINT UNSIGNED NOT NULL COMMENT '宠物ID',
    `game_type`         ENUM('CATCH') NOT NULL DEFAULT 'CATCH' COMMENT '玩法:接球(第一版仅此一种)',
    `status`            ENUM('ACTIVE','SETTLED','EXPIRED') NOT NULL DEFAULT 'ACTIVE' COMMENT '状态机',
    `rule_version`      VARCHAR(20) NOT NULL DEFAULT 'V1' COMMENT '规则版本(写入局快照,配置变更不改已开局)',
    `started_at`        DATETIME NOT NULL COMMENT '服务端开始时间(UTC)',
    `deadline_at`       DATETIME NOT NULL COMMENT '服务端截止时间(UTC,30秒+2秒宽限)',
    `sequence`          JSON NOT NULL COMMENT '随机挑战序列: 10 个窗口目标(左/中/右)',
    `ops`               JSON DEFAULT NULL COMMENT '已接受操作: [{seq,windowIndex,slot,serverTime}]',
    `success_count`     INT NOT NULL DEFAULT 0 COMMENT '成功接球次数',
    `reward_eligible`   TINYINT NOT NULL DEFAULT 1 COMMENT '是否有收益(训练局 false)',
    `reward_operation_id` VARCHAR(80) DEFAULT NULL COMMENT '奖励操作键(同一局只发一次)',
    `quota_date`        DATE NOT NULL COMMENT '开局业务日(额度按开局归属,跨午夜不重复扣)',
    `created_at`        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_minigame_round` (`id`),
    UNIQUE KEY `uk_minigame_active` (`user_id`, (IF(`status` = 'ACTIVE', 1, NULL))),
    INDEX `idx_minigame_user` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='轻量互动小游戏局(服务端验证操作,不信客户端分数)';

-- N05 有限托管
CREATE TABLE IF NOT EXISTS `pet_custody_record` (
    `id`             BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `user_id`        BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `pet_id`         BIGINT UNSIGNED NOT NULL COMMENT '托管宠物ID(每次一只)',
    `week_start`     DATE NOT NULL COMMENT '自然周起始(周一,北京时间)',
    `status`         ENUM('ACTIVE','ENDED') NOT NULL DEFAULT 'ACTIVE' COMMENT '状态(不可叠加:每用户一条 ACTIVE)',
    `started_at`     DATETIME NOT NULL COMMENT '开始时间(UTC)',
    `ends_at`        DATETIME NOT NULL COMMENT '计划结束时间(UTC,最长24h)',
    `rule_snapshot`  JSON NOT NULL COMMENT '规则快照(饱食<30→50 最多2次;清洁<30→50 最多1次)',
    `care_feed_used` INT NOT NULL DEFAULT 0 COMMENT '已用喂食照顾次数',
    `care_clean_used` INT NOT NULL DEFAULT 0 COMMENT '已用清洁照顾次数',
    `ended_at`       DATETIME DEFAULT NULL COMMENT '实际结束时间(UTC)',
    `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_custody_record` (`id`),
    UNIQUE KEY `uk_custody_week` (`user_id`, `week_start`),
    UNIQUE KEY `uk_custody_active` (`user_id`, (IF(`status` = 'ACTIVE', 1, NULL)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='离线托管(有限照顾,不产出养成收益)';

-- N06 好友合作周任务
CREATE TABLE IF NOT EXISTS `pet_cooperation` (
    `id`                BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `week_start`        DATE NOT NULL COMMENT '自然周起始(周一,北京时间)',
    `inviter_user_id`   BIGINT UNSIGNED NOT NULL COMMENT '邀请人',
    `invitee_user_id`   BIGINT UNSIGNED DEFAULT NULL COMMENT '受邀人(待接受为空)',
    `status`            ENUM('INVITED','ACTIVE','COMPLETED','ENDED') NOT NULL DEFAULT 'INVITED' COMMENT '状态机',
    `inviter_pet_id`    BIGINT UNSIGNED NOT NULL COMMENT '邀请人绑定宠物(当周)',
    `invitee_pet_id`    BIGINT UNSIGNED DEFAULT NULL COMMENT '受邀人绑定宠物(接受时)',
    `invite_expires_at` DATETIME NOT NULL COMMENT '邀请过期时间(24h且不超本周结束)',
    `accepted_at`       DATETIME DEFAULT NULL COMMENT '接受时间(UTC)',
    `contributions`     JSON NOT NULL COMMENT '贡献: {inviter:n, invitee:n}(各3次有效照顾,每人每天1次)',
    `ended_at`          DATETIME DEFAULT NULL COMMENT '结束时间(UTC)',
    `created_at`        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_cooperation` (`id`),
    UNIQUE KEY `uk_cooperation_inviter` (`inviter_user_id`, `week_start`),
    UNIQUE KEY `uk_cooperation_invitee` (`invitee_user_id`, `week_start`),
    INDEX `idx_cooperation_week` (`week_start`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='好友合作周任务(双方贡献/周名额/个人奖励)';

CREATE TABLE IF NOT EXISTS `pet_cooperation_contribution` (
    `id`             BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `cooperation_id` BIGINT UNSIGNED NOT NULL COMMENT '合作实例ID',
    `user_id`        BIGINT UNSIGNED NOT NULL COMMENT '贡献用户',
    `event_id`       VARCHAR(120) NOT NULL COMMENT '照顾事件唯一键(去重,重复事件不增贡献)',
    `business_date`  DATE NOT NULL COMMENT '业务日(每人每天最多贡献1次)',
    `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    PRIMARY KEY `pk_pet_cooperation_contribution` (`id`),
    UNIQUE KEY `uk_coop_contribution` (`event_id`),
    UNIQUE KEY `uk_coop_daily` (`cooperation_id`, `user_id`, `business_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='合作贡献去重(每事件一次/每日一次)';

-- N07 收藏图鉴
CREATE TABLE IF NOT EXISTS `pet_collection_entry` (
    `id`               BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `category`         ENUM('SPECIES','SKIN','EQUIPMENT','FURNITURE','BOTTLE','DECOR') NOT NULL COMMENT '类别',
    `item_code`        VARCHAR(60) NOT NULL COMMENT '条目编码',
    `resource_key`     VARCHAR(120) DEFAULT NULL COMMENT '资源标识(未制作返回占位)',
    `rarity`           ENUM('COMMON','RARE','EPIC','LEGENDARY') NOT NULL DEFAULT 'COMMON' COMMENT '稀有度',
    `unlock_condition` VARCHAR(200) NOT NULL COMMENT '解锁条件描述(未解锁返回线索)',
    `hidden`           TINYINT NOT NULL DEFAULT 0 COMMENT '隐藏彩蛋(未解锁不泄漏完整正文)',
    `created_at`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_collection_entry` (`id`),
    UNIQUE KEY `uk_collection_entry` (`category`, `item_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收藏图鉴配置(运营维护)';

CREATE TABLE IF NOT EXISTS `pet_collection_record` (
    `id`            BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `user_id`       BIGINT UNSIGNED NOT NULL COMMENT '用户ID(图鉴按用户累计,多宠共享)',
    `entry_code`    VARCHAR(120) NOT NULL COMMENT '条目(category:item_code)',
    `first_pet_id`  BIGINT UNSIGNED NOT NULL COMMENT '首次获得的宠物',
    `first_event_id` VARCHAR(120) DEFAULT NULL COMMENT '首次获得事件',
    `acquired_at`   DATETIME NOT NULL COMMENT '首次获得时间(UTC)',
    `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_collection_record` (`id`),
    UNIQUE KEY `uk_collection_record` (`user_id`, `entry_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收藏记录(重复获得仅解锁一次)';

-- N03 记忆开关（提取/使用独立控制，B18 消费方读列）
ALTER TABLE `pet`
    ADD COLUMN `memory_extract_enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '自动记忆提取开关(N03)' AFTER `cleanliness_frac`,
    ADD COLUMN `memory_use_enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '记忆注入上下文开关(N03/B18)' AFTER `memory_extract_enabled`;
