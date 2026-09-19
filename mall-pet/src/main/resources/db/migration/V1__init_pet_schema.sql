-- CloudMart 社区宠物模块 数据库初始化 V1
-- ID策略: 雪花算法 (MyBatis-Plus assign_id), 不使用 AUTO_INCREMENT
-- 字符集: utf8mb4_0900_ai_ci, 引擎: InnoDB
-- 时区: 所有 DATETIME 字段统一存储 UTC (与 mall-wish 文档第 26 章一致)
-- 复用边界: 用户/星光(mall_wish)/漂流瓶(mall_wish)/通知(mall_notification)不落本库;
--          pet_bottle_record.bottle_id 仅引用 wish_drift_bottle.id, 不冗余瓶内容

CREATE TABLE IF NOT EXISTS `pet` (
    `id`                   BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `user_id`              BIGINT UNSIGNED NOT NULL COMMENT '主人用户ID(网关X-User-Id)',
    `name`                 VARCHAR(60) NOT NULL COMMENT '宠物名(1-12字符,30天可改一次)',
    `species`              ENUM('CAT','DOG','RABBIT','FOX','PANDA') NOT NULL COMMENT '种类:猫/柴犬/兔子/狐狸/熊猫',
    `appearance`           VARCHAR(255) NOT NULL DEFAULT '{}' COMMENT '外观JSON:{"color":"orange","accessory":"bell"}',
    `personality`          ENUM('LIVELY','GENTLE','TSUNDERE','SIMPLE','COOL','CHATTERBOX') NOT NULL COMMENT '性格(决定AI说话风格)',
    `level`                INT NOT NULL DEFAULT 1 COMMENT '等级(1起)',
    `exp`                  INT NOT NULL DEFAULT 0 COMMENT '当前等级内经验',
    `growth_stage`         ENUM('BABY','YOUNG','ADULT') NOT NULL DEFAULT 'BABY' COMMENT '成长阶段:幼年/成长/成年',
    `hp`                   INT NOT NULL DEFAULT 100 COMMENT '生命值',
    `max_hp`               INT NOT NULL DEFAULT 100 COMMENT '生命上限(升级+5)',
    `hunger`               INT NOT NULL DEFAULT 80 COMMENT '饥饿度0-100(越高越饱)',
    `happiness`            INT NOT NULL DEFAULT 80 COMMENT '心情0-100',
    `energy`               INT NOT NULL DEFAULT 100 COMMENT '精力0-100',
    `cleanliness`          INT NOT NULL DEFAULT 90 COMMENT '清洁度0-100',
    `strength`             INT NOT NULL DEFAULT 5 COMMENT '力量(战斗伤害)',
    `intelligence`         INT NOT NULL DEFAULT 5 COMMENT '智力(读书/学习收益)',
    `agility`              INT NOT NULL DEFAULT 5 COMMENT '敏捷(战斗先手/捞瓶成功率)',
    `charm`                INT NOT NULL DEFAULT 5 COMMENT '魅力(暴击率加成)',
    `status`               ENUM('IDLE','WORKING','STUDYING','FISHING','RESTING') NOT NULL DEFAULT 'IDLE' COMMENT '状态快照(权威状态由pet_activity合成)',
    `is_public`            TINYINT NOT NULL DEFAULT 1 COMMENT '是否在个人主页公开(1公开/0隐私)',
    `last_state_update_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '懒更新游标:上次状态自然变化结算时间(UTC)',
    `last_renamed_at`      DATETIME DEFAULT NULL COMMENT '最近一次改名时间(30天冷却,UTC)',
    `version`              INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `created_at`           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    `deleted_at`           DATETIME DEFAULT NULL COMMENT '软删除时间(放生/注销清理)',
    PRIMARY KEY `pk_pet` (`id`),
    UNIQUE KEY `uk_pet_user` (`user_id`),
    INDEX `idx_pet_level` (`level`),
    INDEX `idx_pet_public` (`is_public`, `level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物主表(一期一用户一宠)';

CREATE TABLE IF NOT EXISTS `pet_activity` (
    `id`            BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `pet_id`        BIGINT UNSIGNED NOT NULL COMMENT '宠物ID',
    `user_id`       BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `activity_type` ENUM('WORK','STUDY','BOTTLE_FISHING','REST','FEED','PLAY','CLEAN') NOT NULL COMMENT '活动类型:打工/读书/捞瓶/休息/喂食/玩耍/清洁(后三者为即时行为留痕,直接CLAIMED)',
    `config_id`     BIGINT UNSIGNED DEFAULT NULL COMMENT '关联配置ID(WORK→pet_job_config/STUDY→pet_study_config,捞瓶休息为空)',
    `status`        ENUM('IN_PROGRESS','COMPLETED','CLAIMED','EXPIRED') NOT NULL COMMENT '状态机:进行中/已完成/已领取/已过期',
    `started_at`    DATETIME NOT NULL COMMENT '开始时间(UTC)',
    `finished_at`   DATETIME NOT NULL COMMENT '预计完成时间(UTC,started_at+duration)',
    `claimed_at`    DATETIME DEFAULT NULL COMMENT '领取时间(幂等标记)',
    `result`        JSON DEFAULT NULL COMMENT '结果JSON(奖励明细/捞瓶outcome与bottleId)',
    `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_activity` (`id`),
    UNIQUE KEY `uk_activity_user_active` (`user_id`, `activity_type`, (IF(`status` = 'IN_PROGRESS', 1, NULL))),
    INDEX `idx_activity_pet` (`pet_id`),
    INDEX `idx_activity_finished` (`status`, `finished_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物统一活动表(打工/读书/捞瓶/休息共用状态机)';
-- uk_activity_user_active 为函数唯一索引(MySQL 8.0.13+): "每用户每类型至多一条 IN_PROGRESS 记录"
-- 数据库层兜底并发重复开工; CLAIMED/COMPLETED/EXPIRED 之间的重复由领取 CAS 保证,不依赖本索引。

CREATE TABLE IF NOT EXISTS `pet_job_config` (
    `id`               BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `name`             VARCHAR(60) NOT NULL COMMENT '岗位名(如:咖啡店兼职)',
    `description`      VARCHAR(255) NOT NULL DEFAULT '' COMMENT '岗位描述',
    `duration_seconds` INT NOT NULL COMMENT '工作时长(秒)',
    `energy_cost`      INT NOT NULL DEFAULT 0 COMMENT '消耗精力',
    `hunger_cost`      INT NOT NULL DEFAULT 0 COMMENT '消耗饥饿(越工作越饿)',
    `exp_reward`       INT NOT NULL DEFAULT 0 COMMENT '宠物经验奖励',
    `currency_reward`  INT NOT NULL DEFAULT 0 COMMENT '星光奖励(经mall-wish内部端点发放,流水来源PET_REWARD)',
    `required_level`   INT NOT NULL DEFAULT 1 COMMENT '接单最低等级',
    `enabled`          TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用(1启用/0停用)',
    `sort`             INT NOT NULL DEFAULT 0 COMMENT '排序(升序)',
    `created_at`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_job_config` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物打工岗位配置';

CREATE TABLE IF NOT EXISTS `pet_study_config` (
    `id`                  BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `name`                VARCHAR(60) NOT NULL COMMENT '课程名(如:编程入门)',
    `description`         VARCHAR(255) NOT NULL DEFAULT '' COMMENT '课程描述',
    `category`            VARCHAR(30) NOT NULL DEFAULT 'GENERAL' COMMENT '分类:文学/历史/科学/艺术/编程/社交/心理/冒险',
    `duration_seconds`    INT NOT NULL COMMENT '学习时长(秒)',
    `energy_cost`         INT NOT NULL DEFAULT 0 COMMENT '消耗精力',
    `exp_reward`          INT NOT NULL DEFAULT 0 COMMENT '宠物经验奖励',
    `intelligence_reward` INT NOT NULL DEFAULT 0 COMMENT '智力提升',
    `required_level`      INT NOT NULL DEFAULT 1 COMMENT '接课最低等级',
    `enabled`             TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用(1启用/0停用)',
    `sort`                INT NOT NULL DEFAULT 0 COMMENT '排序(升序)',
    `created_at`          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_study_config` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物读书课程配置';

CREATE TABLE IF NOT EXISTS `pet_bottle_record` (
    `id`          BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `pet_id`      BIGINT UNSIGNED NOT NULL COMMENT '宠物ID',
    `user_id`     BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `activity_id` BIGINT UNSIGNED NOT NULL COMMENT '关联pet_activity.id(BOTTLE_FISHING任务)',
    `bottle_id`   BIGINT UNSIGNED DEFAULT NULL COMMENT '捞到的漂流瓶ID(wish_drift_bottle.id,EMPTY/FAILED为空)',
    `outcome`     ENUM('CAUGHT','EMPTY','FAILED') NOT NULL COMMENT '结果:捞到/空手而归/服务降级可重试',
    `success_rate` DECIMAL(5,4) DEFAULT NULL COMMENT '本次成功率快照0-1(便于回放与调参)',
    `started_at`  DATETIME NOT NULL COMMENT '开始时间(UTC)',
    `finished_at` DATETIME NOT NULL COMMENT '完成时间(UTC)',
    `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_bottle_record` (`id`),
    UNIQUE KEY `uk_bottle_record_activity` (`activity_id`),
    INDEX `idx_bottle_record_user` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物捞漂流瓶流水(bottle_id指向mall-wish,不建第二套漂流瓶)';

CREATE TABLE IF NOT EXISTS `pet_battle` (
    `id`                BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `mode`              ENUM('PVE','PVP') NOT NULL COMMENT '模式:PvE野生宠物立即结算/PvP异步快照应战',
    `attacker_pet_id`   BIGINT UNSIGNED NOT NULL COMMENT '挑战方宠物ID',
    `attacker_user_id`  BIGINT UNSIGNED NOT NULL COMMENT '挑战方用户ID',
    `defender_pet_id`   BIGINT UNSIGNED NOT NULL COMMENT '防守方宠物ID(PvE为模板生成ID=0)',
    `defender_user_id`  BIGINT UNSIGNED DEFAULT NULL COMMENT '防守方用户ID(PvE为空)',
    `status`            ENUM('PENDING','FINISHED','DECLINED','EXPIRED') NOT NULL COMMENT '状态:待应战/已结束/已拒绝/已过期',
    `seed`              BIGINT UNSIGNED NOT NULL COMMENT '战斗随机种子(结算前生成,保证可离线复现)',
    `winner_pet_id`     BIGINT UNSIGNED DEFAULT NULL COMMENT '胜者宠物ID(平局/未结算为空)',
    `attacker_snapshot` JSON NOT NULL COMMENT '挑战方宠物属性快照',
    `defender_snapshot` JSON NOT NULL COMMENT '防守方宠物属性快照(PvE为野生宠物模板)',
    `rounds`            JSON DEFAULT NULL COMMENT '回合流水JSON:[{round,actor,action,damage,critical,dodge,remainingHp}]',
    `exp_reward`        INT NOT NULL DEFAULT 0 COMMENT '经验奖励(结算后双方各自发放)',
    `currency_reward`   INT NOT NULL DEFAULT 0 COMMENT '星光奖励(仅胜者,经mall-wish发放)',
    `started_at`        DATETIME NOT NULL COMMENT '发起时间(UTC)',
    `finished_at`       DATETIME DEFAULT NULL COMMENT '结算时间(UTC)',
    `created_at`        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_battle` (`id`),
    INDEX `idx_battle_attacker` (`attacker_user_id`, `created_at`),
    INDEX `idx_battle_defender` (`defender_user_id`, `status`),
    INDEX `idx_battle_attacker_pet` (`attacker_pet_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物对战记录(异步回合制,服务端全权计算)';

CREATE TABLE IF NOT EXISTS `pet_chat_session` (
    `id`         BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `user_id`    BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `pet_id`     BIGINT UNSIGNED NOT NULL COMMENT '宠物ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_chat_session` (`id`),
    UNIQUE KEY `uk_pet_chat_session_user` (`user_id`),
    INDEX `idx_chat_session_pet` (`pet_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物聊天会话(一人一宠一会话)';

CREATE TABLE IF NOT EXISTS `pet_chat_message` (
    `id`          BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `session_id`  BIGINT UNSIGNED NOT NULL COMMENT '会话ID',
    `role`        ENUM('USER','PET','SYSTEM') NOT NULL COMMENT '角色:用户/宠物/系统',
    `content`     VARCHAR(2000) NOT NULL COMMENT '消息内容',
    `token_count` INT NOT NULL DEFAULT 0 COMMENT '估算token数(成本观测)',
    `is_ai_reply` TINYINT NOT NULL DEFAULT 0 COMMENT '是否AI生成(0=模板降级/固定行为)',
    `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_chat_message` (`id`),
    INDEX `idx_chat_message_session` (`session_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物聊天消息(上下文窗口只取最近N条,长期信息沉淀pet_memory)';

CREATE TABLE IF NOT EXISTS `pet_memory` (
    `id`           BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `user_id`      BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `pet_id`       BIGINT UNSIGNED NOT NULL COMMENT '宠物ID',
    `memory_type`  ENUM('FAVORITE','HABIT','FACT') NOT NULL COMMENT '记忆类型:喜好/习惯/事实',
    `memory_key`   VARCHAR(60) NOT NULL COMMENT '记忆键(如favorite_food/owner_nickname)',
    `memory_value` VARCHAR(255) NOT NULL COMMENT '记忆值(如"鱼")',
    `importance`   INT NOT NULL DEFAULT 3 COMMENT '重要度1-5(注入prompt排序权重)',
    `confidence`   DECIMAL(4,3) NOT NULL DEFAULT 0.900 COMMENT '置信度0-1(规则抽取默认0.9)',
    `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_memory` (`id`),
    UNIQUE KEY `uk_pet_memory_key` (`pet_id`, `memory_key`),
    INDEX `idx_memory_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物结构化记忆(聊天关键信息抽取,非全量历史回放)';

CREATE TABLE IF NOT EXISTS `pet_achievement` (
    `id`              BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `code`            VARCHAR(60) NOT NULL COMMENT '唯一编码(如FIRST_BOTTLE)',
    `name`            VARCHAR(60) NOT NULL COMMENT '成就名',
    `description`     VARCHAR(255) NOT NULL DEFAULT '' COMMENT '达成条件描述',
    `icon`            VARCHAR(30) NOT NULL DEFAULT '🏆' COMMENT '图标(emoji或URL)',
    `condition_type`  ENUM('BOTTLE_COUNT','BATTLE_WIN','LEVEL','CHAT_COUNT','STATS_FULL','ACTIVITY_COUNT') NOT NULL COMMENT '判定类型:捞瓶数/胜场/等级/聊天句数/满属性/行为计数',
    `condition_subtype` VARCHAR(30) DEFAULT NULL COMMENT '计数子类型(ACTIVITY_COUNT时: WORK/STUDY/FEED/CLEAN)',
    `condition_value` INT NOT NULL COMMENT '判定阈值',
    `exp_reward`      INT NOT NULL DEFAULT 0 COMMENT '达成奖励经验',
    `enabled`         TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用(1启用/0停用)',
    `sort`            INT NOT NULL DEFAULT 0 COMMENT '排序(升序)',
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_achievement` (`id`),
    UNIQUE KEY `uk_achievement_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物成就定义';

CREATE TABLE IF NOT EXISTS `pet_achievement_record` (
    `id`             BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `pet_id`         BIGINT UNSIGNED NOT NULL COMMENT '宠物ID',
    `user_id`        BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `achievement_id` BIGINT UNSIGNED NOT NULL COMMENT '成就ID',
    `achieved_at`    DATETIME NOT NULL COMMENT '达成时间(UTC)',
    `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_achievement_record` (`id`),
    UNIQUE KEY `uk_pet_ach_record` (`pet_id`, `achievement_id`),
    INDEX `idx_ach_record_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物成就达成记录(uk幂等,同一成就一只宠物只发一次)';

CREATE TABLE IF NOT EXISTS `pet_context_counter` (
    `id`          BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `user_id`     BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `new_comments` INT NOT NULL DEFAULT 0 COMMENT '未播报新评论数(聚合清零制)',
    `new_likes`   INT NOT NULL DEFAULT 0 COMMENT '未播报新点赞数',
    `new_follows` INT NOT NULL DEFAULT 0 COMMENT '未播报新关注数',
    `new_collects` INT NOT NULL DEFAULT 0 COMMENT '未播报新收藏数',
    `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_context_counter` (`id`),
    UNIQUE KEY `uk_pet_context_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='社区事件计数器(宠物AI上下文白名单数据,播报后清零)';

-- ---- 种子数据：打工岗位（原文档 §11 示例职业）----
INSERT INTO `pet_job_config` (`id`, `name`, `description`, `duration_seconds`, `energy_cost`, `hunger_cost`, `exp_reward`, `currency_reward`, `required_level`, `enabled`, `sort`) VALUES
    (9001001, '送外卖', '骑着小电驴穿梭在城市的大街小巷，风里来雨里去。', 1800, 20, 10, 25, 100, 1, 1, 1),
    (9001002, '咖啡店兼职', '在街角咖啡店帮忙拉花，闻着咖啡香打一下午工。', 1800, 15, 5, 20, 100, 1, 1, 2),
    (9001003, '图书管理员', '整理书架、登记借阅，顺便偷看几页故事书。', 3600, 20, 10, 40, 180, 5, 1, 3),
    (9001004, '快递分拣员', '把包裹按地址分门别类，考验细心和耐力。', 2700, 25, 12, 35, 150, 3, 1, 4),
    (9001005, '小探险家', '去社区后山探一条新小路，带回有趣的石头和故事。', 5400, 35, 20, 70, 300, 10, 1, 5);

-- ---- 种子数据：读书课程（原文档 §12 八大类）----
INSERT INTO `pet_study_config` (`id`, `name`, `description`, `category`, `duration_seconds`, `energy_cost`, `exp_reward`, `intelligence_reward`, `required_level`, `enabled`, `sort`) VALUES
    (9002001, '《编程入门》', '从 Hello World 开始，逻辑思维启蒙。', '编程', 3600, 20, 50, 2, 1, 1, 1),
    (9002002, '《文学欣赏》', '读几篇短篇故事，学着感受文字的美。', '文学', 3600, 15, 40, 2, 1, 1, 2),
    (9002003, '《历史漫谈》', '朝代更迭小故事，越听越精神。', '历史', 3600, 15, 40, 2, 1, 1, 3),
    (9002004, '《科学小实验》', '为什么天是蓝的？一起找答案。', '科学', 3600, 20, 45, 2, 1, 1, 4),
    (9002005, '《艺术涂鸦》', '画一只想象中的小怪兽。', '艺术', 2700, 15, 35, 2, 1, 1, 5),
    (9002006, '《社交礼仪》', '怎么打招呼、怎么交朋友。', '社交', 2700, 10, 30, 2, 1, 1, 6),
    (9002007, '《心理小课堂》', '认识情绪，做情绪的小主人。', '心理', 2700, 10, 30, 2, 1, 1, 7),
    (9002008, '《冒险图鉴》', '野外辨向与安全常识，为探险做准备。', '冒险', 5400, 25, 60, 3, 5, 1, 8);

-- ---- 种子数据：成就（12 枚，对应原文档 §36 成就清单）----
INSERT INTO `pet_achievement` (`id`, `code`, `name`, `description`, `icon`, `condition_type`, `condition_subtype`, `condition_value`, `exp_reward`, `enabled`, `sort`) VALUES
    (9003001, 'FIRST_BOTTLE', '第一次捞到漂流瓶', '帮主人捞起了第一只漂流瓶。', '🍾', 'BOTTLE_COUNT', NULL, 1, 20, 1, 1),
    (9003002, 'BOTTLE_10', '打捞小能手', '累计捞起 10 只漂流瓶。', '🧺', 'BOTTLE_COUNT', NULL, 10, 60, 1, 2),
    (9003003, 'FIRST_WIN', '初试锋芒', '赢得第一场对战。', '⚔️', 'BATTLE_WIN', NULL, 1, 20, 1, 3),
    (9003004, 'WIN_10', '擂台常客', '累计赢下 10 场对战。', '🏆', 'BATTLE_WIN', NULL, 10, 80, 1, 4),
    (9003005, 'LEVEL_10', '成长期', '等级达到 Lv.10。', '🌱', 'LEVEL', NULL, 10, 50, 1, 5),
    (9003006, 'LEVEL_20', '成年礼', '等级达到 Lv.20。', '🌳', 'LEVEL', NULL, 20, 120, 1, 6),
    (9003007, 'WORK_10', '打工达人', '累计完成 10 次打工。', '💼', 'ACTIVITY_COUNT', 'WORK', 10, 50, 1, 7),
    (9003008, 'STUDY_10', '学霸宠物', '累计读完 10 门课程。', '📚', 'ACTIVITY_COUNT', 'STUDY', 10, 60, 1, 8),
    (9003009, 'FEED_50', '干饭小能手', '被喂食 50 次。', '🍖', 'ACTIVITY_COUNT', 'FEED', 50, 40, 1, 9),
    (9003010, 'CHAT_100', '话匣子', '和主人聊满 100 句。', '💬', 'CHAT_COUNT', NULL, 100, 60, 1, 10),
    (9003011, 'STATS_FULL', '全面发展', '四项成长属性全部达到 50。', '🌟', 'STATS_FULL', NULL, 50, 100, 1, 11),
    (9003012, 'CLEAN_50', '香喷喷', '被清洁 50 次。', '🫧', 'ACTIVITY_COUNT', 'CLEAN', 50, 40, 1, 12);
