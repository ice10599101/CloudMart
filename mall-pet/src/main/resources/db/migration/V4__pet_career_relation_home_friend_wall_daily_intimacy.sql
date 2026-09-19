-- CloudMart 社区宠物模块 V4：三期能力
-- 覆盖：宠物职业、宠物关系（情侣/闺蜜/兄弟/死党）、宠物房间/家园、好友互访、宠物留言墙、
--       宠物每日任务、亲密度/陪伴时长
-- 复用边界不变：星光收支走 mall-wish 内部端点；宠物物品统一落 pet_inventory（本期扩 FURNITURE）；
-- 行为留痕统一落 pet_activity（本期扩 CAREER_WORK），不另建任务/行为框架。

-- ---- 1. 宠物主表扩展：职业 + 亲密度/陪伴时长 ----
ALTER TABLE `pet`
    ADD COLUMN `career_code` VARCHAR(60) DEFAULT NULL COMMENT '当前职业编码(pet_career_config.code, NULL=未入职)' AFTER `personality`,
    ADD COLUMN `intimacy` INT NOT NULL DEFAULT 0 COMMENT '与主人的亲密度(0起,阈值见 PetProperties.Intimacy)' AFTER `charm`,
    ADD COLUMN `companion_seconds` BIGINT NOT NULL DEFAULT 0 COMMENT '累计陪伴时长(秒,心跳累加)' AFTER `intimacy`,
    ADD COLUMN `companion_days` INT NOT NULL DEFAULT 0 COMMENT '累计陪伴天数(去重日期数)' AFTER `companion_seconds`,
    ADD COLUMN `companion_streak` INT NOT NULL DEFAULT 0 COMMENT '连续陪伴天数(断签重置)' AFTER `companion_days`,
    ADD COLUMN `last_companion_date` DATE DEFAULT NULL COMMENT '最近一次陪伴日期(UTC,连续天数判定)' AFTER `companion_streak`,
    ADD COLUMN `today_companion_seconds` INT NOT NULL DEFAULT 0 COMMENT '今日陪伴秒数(跨天惰性重置,日上限见配置)' AFTER `last_companion_date`;

ALTER TABLE `pet`
    ADD INDEX `idx_pet_career` (`career_code`);

-- ---- 2. 活动类型扩展：职业打工（复用统一活动状态机）----
ALTER TABLE `pet_activity`
    MODIFY COLUMN `activity_type` ENUM('WORK','STUDY','BOTTLE_FISHING','REST','FEED','PLAY','CLEAN','VISIT','EVOLVE','CAREER_WORK')
        NOT NULL COMMENT '活动类型:打工/读书/捞瓶/休息/喂食/玩耍/清洁/串门/进化/职业打工(即时行为直接CLAIMED)';

-- ---- 3. 宠物背包物品类型扩展：家具（家园）----
ALTER TABLE `pet_inventory`
    MODIFY COLUMN `item_type` ENUM('EQUIPMENT','SKIN','SKILL_BOOK','FURNITURE')
        NOT NULL COMMENT '物品类型:装备/皮肤/技能书/家具';

-- ---- 4. 成就判定维度扩展：亲密度/关系/好友/留言/舒适度/每日任务/陪伴 ----
ALTER TABLE `pet_achievement`
    MODIFY COLUMN `condition_type` ENUM('BOTTLE_COUNT','BATTLE_WIN','LEVEL','CHAT_COUNT','STATS_FULL','ACTIVITY_COUNT',
        'VISIT_COUNT','EVOLUTION','INTIMACY','RELATION_COUNT','FRIEND_COUNT','WALL_MESSAGE_COUNT','ROOM_COMFORT',
        'QUEST_COUNT','COMPANION_HOURS')
        NOT NULL COMMENT '判定类型:捞瓶数/胜场/等级/聊天句数/满属性/行为计数/串门数/进化阶数/亲密度/关系数/好友数/留言数/房间舒适度/每日任务数/陪伴小时数';

-- ---- 5. 宠物职业配置（职业路线 × 阶段；晋升需工作次数 + 星光）----
CREATE TABLE IF NOT EXISTS `pet_career_config` (
    `id`                    BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `code`                  VARCHAR(60) NOT NULL COMMENT '唯一编码',
    `name`                  VARCHAR(60) NOT NULL COMMENT '职业名',
    `description`           VARCHAR(255) NOT NULL DEFAULT '' COMMENT '职业描述',
    `career_line`           VARCHAR(40) NOT NULL COMMENT '职业路线（同线内逐阶晋升）',
    `tier`                  TINYINT NOT NULL DEFAULT 1 COMMENT '阶段(1初级/2中级/3高级)',
    `icon`                  VARCHAR(30) NOT NULL DEFAULT '💼' COMMENT '图标(emoji或URL)',
    `required_level`        INT NOT NULL DEFAULT 1 COMMENT '入职最低等级',
    `required_intelligence` INT NOT NULL DEFAULT 0 COMMENT '入职最低智力',
    `duration_seconds`      INT NOT NULL COMMENT '单次工作耗时(秒)',
    `energy_cost`           INT NOT NULL DEFAULT 0 COMMENT '精力消耗',
    `hunger_cost`           INT NOT NULL DEFAULT 0 COMMENT '饥饿消耗',
    `exp_reward`            INT NOT NULL DEFAULT 0 COMMENT '基础经验奖励',
    `currency_reward`       INT NOT NULL DEFAULT 0 COMMENT '基础星光奖励',
    `promote_to_code`       VARCHAR(60) DEFAULT NULL COMMENT '晋升目标职业(NULL=已是最高阶)',
    `promote_required_count` INT NOT NULL DEFAULT 0 COMMENT '晋升所需本职业工作次数',
    `promote_star_cost`     INT NOT NULL DEFAULT 0 COMMENT '晋升消耗星光',
    `enabled`               TINYINT NOT NULL DEFAULT 1 COMMENT '是否开放(1开放/0停招)',
    `sort`                  INT NOT NULL DEFAULT 0 COMMENT '排序(升序)',
    `created_at`            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_career_config` (`id`),
    UNIQUE KEY `uk_pet_career_code` (`code`),
    INDEX `idx_pet_career_line` (`career_line`, `tier`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物职业配置(路线 × 阶段)';

-- ---- 6. 宠物职业进度（每宠物每职业一行；工作次数决定晋升资格）----
CREATE TABLE IF NOT EXISTS `pet_career_progress` (
    `id`             BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `pet_id`         BIGINT UNSIGNED NOT NULL COMMENT '宠物ID',
    `user_id`        BIGINT UNSIGNED NOT NULL COMMENT '用户ID(查询冗余)',
    `career_code`    VARCHAR(60) NOT NULL COMMENT '职业编码(pet_career_config.code)',
    `work_count`     INT NOT NULL DEFAULT 0 COMMENT '本职业累计工作次数',
    `total_currency` INT NOT NULL DEFAULT 0 COMMENT '本职业累计星光收入',
    `started_at`     DATETIME NOT NULL COMMENT '入职时间(UTC)',
    `promoted_at`    DATETIME DEFAULT NULL COMMENT '晋升离开本职业时间(UTC,NULL=在职)',
    `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_career_progress` (`id`),
    UNIQUE KEY `uk_pet_career_progress` (`pet_id`, `career_code`),
    INDEX `idx_pet_career_progress_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物职业进度(uk幂等,重复入职不覆盖历史)';

-- ---- 7. 宠物关系（情侣/闺蜜/兄弟/死党；双向确认后 ACTIVE）----
CREATE TABLE IF NOT EXISTS `pet_relation` (
    `id`              BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `from_pet_id`     BIGINT UNSIGNED NOT NULL COMMENT '发起方宠物ID',
    `to_pet_id`       BIGINT UNSIGNED NOT NULL COMMENT '接收方宠物ID',
    `from_user_id`    BIGINT UNSIGNED NOT NULL COMMENT '发起方主人用户ID',
    `to_user_id`      BIGINT UNSIGNED NOT NULL COMMENT '接收方主人用户ID',
    `rel_type`        ENUM('COUPLE','BESTIE','BROTHER','CONFIDANT') NOT NULL COMMENT '关系类型:情侣/闺蜜/兄弟/死党',
    `status`          ENUM('PENDING','ACTIVE','REJECTED','DISSOLVED') NOT NULL DEFAULT 'PENDING' COMMENT '状态:待确认/已建立/已拒绝/已解除',
    `intimacy`        INT NOT NULL DEFAULT 0 COMMENT '关系亲密度(互访/留言/对战累积)',
    `message`         VARCHAR(60) DEFAULT NULL COMMENT '申请留言',
    `accepted_at`     DATETIME DEFAULT NULL COMMENT '确认时间(UTC)',
    `last_intimacy_at` DATETIME DEFAULT NULL COMMENT '最近一次亲密度增长时间(UTC)',
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_relation` (`id`),
    UNIQUE KEY `uk_pet_relation` (`from_pet_id`, `to_pet_id`, `rel_type`),
    INDEX `idx_pet_relation_to` (`to_pet_id`, `status`),
    INDEX `idx_pet_relation_from_user` (`from_user_id`, `status`),
    INDEX `idx_pet_relation_to_user` (`to_user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物关系(uk幂等,同一对宠物同一类型仅一条)';

-- ---- 8. 家具配置（家园装扮；墙体/地板为风格键，其余可摆放）----
CREATE TABLE IF NOT EXISTS `pet_furniture_config` (
    `id`              BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `code`            VARCHAR(60) NOT NULL COMMENT '唯一编码',
    `name`            VARCHAR(60) NOT NULL COMMENT '家具名',
    `description`     VARCHAR(255) NOT NULL DEFAULT '' COMMENT '描述',
    `category`        ENUM('WALL','FLOOR','FURNITURE','PLANT','TOY','BED') NOT NULL COMMENT '分类:墙纸/地板/家具/绿植/玩具/床',
    `icon`            VARCHAR(30) NOT NULL DEFAULT '🧸' COMMENT '图标(emoji或URL)',
    `rarity`          ENUM('COMMON','RARE','EPIC') NOT NULL DEFAULT 'COMMON' COMMENT '稀有度',
    `price_starlight` INT NOT NULL DEFAULT 0 COMMENT '售价(星光)',
    `required_level`  INT NOT NULL DEFAULT 1 COMMENT '购买最低等级',
    `comfort`         INT NOT NULL DEFAULT 0 COMMENT '舒适度分值(房间舒适度=已摆放之和)',
    `enabled`         TINYINT NOT NULL DEFAULT 1 COMMENT '是否上架(1上架/0下架)',
    `sort`            INT NOT NULL DEFAULT 0 COMMENT '排序(升序)',
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_furniture_config` (`id`),
    UNIQUE KEY `uk_pet_furniture_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物家具配置(家园商城在售)';

-- ---- 9. 宠物房间（每宠物一间；主题 + 舒适度 + 访问/点赞统计）----
CREATE TABLE IF NOT EXISTS `pet_room` (
    `id`               BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `pet_id`           BIGINT UNSIGNED NOT NULL COMMENT '宠物ID',
    `user_id`          BIGINT UNSIGNED NOT NULL COMMENT '用户ID(访问查询冗余)',
    `wall_code`        VARCHAR(60) DEFAULT NULL COMMENT '墙纸编码(pet_furniture_config.code, NULL=默认)',
    `floor_code`       VARCHAR(60) DEFAULT NULL COMMENT '地板编码(pet_furniture_config.code, NULL=默认)',
    `welcome_message`  VARCHAR(80) NOT NULL DEFAULT '' COMMENT '欢迎语(来访者可见)',
    `is_public`        TINYINT NOT NULL DEFAULT 1 COMMENT '是否允许来访(1公开/0仅自己)',
    `comfort`          INT NOT NULL DEFAULT 0 COMMENT '当前舒适度(已摆放家具之和)',
    `visit_count`      INT NOT NULL DEFAULT 0 COMMENT '累计来访次数(去重按日,服务端维护)',
    `like_count`       INT NOT NULL DEFAULT 0 COMMENT '累计点赞数',
    `created_at`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_room` (`id`),
    UNIQUE KEY `uk_pet_room` (`pet_id`),
    INDEX `idx_pet_room_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物房间(懒创建:首次进入家园时生成)';

-- ---- 10. 宠物房间摆放（网格坐标唯一；同一家具可摆放多件需多件持有）----
CREATE TABLE IF NOT EXISTS `pet_room_item` (
    `id`             BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `pet_id`         BIGINT UNSIGNED NOT NULL COMMENT '宠物ID',
    `user_id`        BIGINT UNSIGNED NOT NULL COMMENT '用户ID(查询冗余)',
    `furniture_code` VARCHAR(60) NOT NULL COMMENT '家具编码(pet_furniture_config.code)',
    `pos_x`          TINYINT NOT NULL COMMENT '网格 X(0-3)',
    `pos_y`          TINYINT NOT NULL COMMENT '网格 Y(0-2)',
    `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_room_item` (`id`),
    UNIQUE KEY `uk_pet_room_item_pos` (`pet_id`, `pos_x`, `pos_y`),
    INDEX `idx_pet_room_item_pet` (`pet_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物房间摆放(格子唯一,换位=先卸下再摆)';

-- ---- 11. 宠物好友（双向各一行；互访互相加经验/人气）----
CREATE TABLE IF NOT EXISTS `pet_friend` (
    `id`             BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `user_id`        BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `friend_user_id` BIGINT UNSIGNED NOT NULL COMMENT '好友用户ID',
    `status`         ENUM('PENDING','ACTIVE','REJECTED') NOT NULL DEFAULT 'PENDING' COMMENT '状态:待确认/好友/已拒绝',
    `source`         VARCHAR(20) NOT NULL DEFAULT 'VISIT' COMMENT '来源:VISIT访问/SEARCH搜索/RELATION关系',
    `visit_count`    INT NOT NULL DEFAULT 0 COMMENT '我访问好友次数',
    `last_visit_at`  DATETIME DEFAULT NULL COMMENT '最近一次互访时间(UTC)',
    `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_friend` (`id`),
    UNIQUE KEY `uk_pet_friend` (`user_id`, `friend_user_id`),
    INDEX `idx_pet_friend_reverse` (`friend_user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物好友(申请单向行,确认时双向落库)';

-- ---- 12. 宠物留言墙留言（含主人回复，两层结构）----
CREATE TABLE IF NOT EXISTS `pet_wall_message` (
    `id`             BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `pet_id`         BIGINT UNSIGNED NOT NULL COMMENT '被留言宠物ID(房间主人)',
    `user_id`        BIGINT UNSIGNED NOT NULL COMMENT '房间主人用户ID',
    `author_user_id` BIGINT UNSIGNED NOT NULL COMMENT '留言者用户ID',
    `author_pet_id`  BIGINT UNSIGNED DEFAULT NULL COMMENT '留言者主宠ID(展示宠物口吻,可空)',
    `parent_id`      BIGINT UNSIGNED DEFAULT NULL COMMENT '父留言ID(NULL=一级留言,非空=主人回复)',
    `content`        VARCHAR(120) NOT NULL COMMENT '留言内容(1-120字)',
    `mood`           VARCHAR(20) DEFAULT NULL COMMENT '心情标签(可选,展示用)',
    `status`         ENUM('NORMAL','HIDDEN','DELETED') NOT NULL DEFAULT 'NORMAL' COMMENT '状态:正常/管理员隐藏/已删除',
    `like_count`     INT NOT NULL DEFAULT 0 COMMENT '点赞数',
    `reply_count`    INT NOT NULL DEFAULT 0 COMMENT '回复数',
    `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_wall_message` (`id`),
    INDEX `idx_pet_wall_owner` (`pet_id`, `status`, `id`),
    INDEX `idx_pet_wall_author` (`author_user_id`, `created_at`),
    INDEX `idx_pet_wall_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物留言墙留言(管理员可隐藏,幂等点赞)';

-- ---- 13. 宠物留言点赞（uk 幂等，取消点赞即删行）----
CREATE TABLE IF NOT EXISTS `pet_wall_like` (
    `id`         BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `message_id` BIGINT UNSIGNED NOT NULL COMMENT '留言ID',
    `user_id`    BIGINT UNSIGNED NOT NULL COMMENT '点赞用户ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_wall_like` (`id`),
    UNIQUE KEY `uk_pet_wall_like` (`message_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物留言点赞(uk幂等,重复点击不重复计数)';

-- ---- 14. 宠物每日任务配置 ----
CREATE TABLE IF NOT EXISTS `pet_daily_quest_config` (
    `id`              BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `code`            VARCHAR(60) NOT NULL COMMENT '唯一编码',
    `name`            VARCHAR(60) NOT NULL COMMENT '任务名',
    `description`     VARCHAR(255) NOT NULL DEFAULT '' COMMENT '任务描述',
    `icon`            VARCHAR(30) NOT NULL DEFAULT '📌' COMMENT '图标(emoji或URL)',
    `quest_type`      ENUM('FEED','PLAY','CLEAN','REST','WORK','STUDY','BOTTLE','BATTLE','VISIT','CHAT',
        'CAREER_WORK','FRIEND_VISIT','WALL_MESSAGE','COMPANION','DECORATE') NOT NULL COMMENT '统计口径(与业务埋点一一对应)',
    `target_value`    INT NOT NULL COMMENT '目标次数',
    `exp_reward`      INT NOT NULL DEFAULT 0 COMMENT '奖励经验',
    `currency_reward` INT NOT NULL DEFAULT 0 COMMENT '奖励星光',
    `required_level`  INT NOT NULL DEFAULT 1 COMMENT '宠物等级门槛',
    `enabled`         TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用(1启用/0停用)',
    `sort`            INT NOT NULL DEFAULT 0 COMMENT '排序(升序)',
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_daily_quest_config` (`id`),
    UNIQUE KEY `uk_pet_daily_quest_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物每日任务配置(每日按启用配置生成进度行)';

-- ---- 15. 宠物每日任务进度（按 pet × 日期 × 任务 唯一；跨天自然重置）----
CREATE TABLE IF NOT EXISTS `pet_daily_quest` (
    `id`           BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `pet_id`       BIGINT UNSIGNED NOT NULL COMMENT '宠物ID',
    `user_id`      BIGINT UNSIGNED NOT NULL COMMENT '用户ID(查询冗余)',
    `quest_date`   DATE NOT NULL COMMENT '任务日期(UTC,自然日)',
    `quest_code`   VARCHAR(60) NOT NULL COMMENT '任务编码(pet_daily_quest_config.code)',
    `progress`     INT NOT NULL DEFAULT 0 COMMENT '当前进度',
    `target_value` INT NOT NULL COMMENT '目标值(生成时快照,配置改动不影响当日)',
    `status`       ENUM('IN_PROGRESS','COMPLETE','CLAIMED') NOT NULL DEFAULT 'IN_PROGRESS' COMMENT '状态:进行中/已完成/已领奖',
    `completed_at` DATETIME DEFAULT NULL COMMENT '完成时间(UTC)',
    `claimed_at`   DATETIME DEFAULT NULL COMMENT '领奖时间(UTC,幂等标记)',
    `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_daily_quest` (`id`),
    UNIQUE KEY `uk_pet_daily_quest` (`pet_id`, `quest_date`, `quest_code`),
    INDEX `idx_pet_daily_quest_user` (`user_id`, `quest_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物每日任务进度(进度由业务埋点累加,CAS领奖幂等)';

-- ---- 16. 种子数据：宠物职业（3 条路线 × 2 阶段）----
INSERT INTO `pet_career_config`
    (`id`, `code`, `name`, `description`, `career_line`, `tier`, `icon`, `required_level`, `required_intelligence`,
     `duration_seconds`, `energy_cost`, `hunger_cost`, `exp_reward`, `currency_reward`,
     `promote_to_code`, `promote_required_count`, `promote_star_cost`, `enabled`, `sort`) VALUES
    (9009001, 'dessert_apprentice', '甜品学徒', '在后厨学做小蛋糕，围裙上永远沾着糖霜。', '美食', 1, '🧁', 3, 0, 1800, 18, 10, 35, 160, 'cafe_owner', 5, 400, 1, 1),
    (9009002, 'cafe_owner', '咖啡馆主理人', '自己开了一间街角小店，会拉花了也会算账了。', '美食', 2, '☕', 8, 12, 3600, 28, 15, 65, 300, NULL, 0, 0, 1, 2),
    (9009003, 'junior_explorer', '见习探险家', '跟着前辈认识山路与星图，背包里装满了石头。', '探险', 1, '🧭', 3, 0, 2400, 20, 12, 38, 170, 'ruin_guide', 5, 420, 1, 3),
    (9009004, 'ruin_guide', '遗迹向导', '能带社区的小伙伴安全穿过遗迹，讲一路的故事。', '探险', 2, '🗺️', 8, 10, 3600, 30, 18, 70, 320, NULL, 0, 0, 1, 4),
    (9009005, 'street_painter', '街头画家', '在社区广场给路人画速写，一天能画满一本。', '艺术', 1, '🎨', 3, 0, 1800, 16, 8, 32, 150, 'mural_master', 5, 380, 1, 5),
    (9009006, 'mural_master', '壁画师', '把自己的画留在了社区文化墙上，很多年都不会褪色。', '艺术', 2, '🖌️', 8, 15, 3600, 26, 14, 68, 310, NULL, 0, 0, 1, 6);

-- ---- 17. 种子数据：家具（墙纸/地板/家具/绿植/玩具/床）----
INSERT INTO `pet_furniture_config`
    (`id`, `code`, `name`, `description`, `category`, `icon`, `rarity`, `price_starlight`, `required_level`, `comfort`, `enabled`, `sort`) VALUES
    (9010001, 'wall_cloud', '云朵壁纸', '像被云朵包住的小房间，安静又柔软。', 'WALL', '☁️', 'COMMON', 120, 1, 4, 1, 1),
    (9010002, 'wall_starry', '星空壁纸', '关灯以后会看到一整片星空。', 'WALL', '🌌', 'RARE', 320, 5, 8, 1, 2),
    (9010003, 'floor_wood', '原木地板', '踩上去会发出好听的脚步声。', 'FLOOR', '🪵', 'COMMON', 120, 1, 4, 1, 3),
    (9010004, 'floor_matcha', '抹茶地毯', '软软的抹茶色地毯，坐着就不想起来。', 'FLOOR', '🍵', 'RARE', 300, 5, 8, 1, 4),
    (9010005, 'sofa_cloud', '懒人沙发', '陷进去以后需要一点意志力才能起来。', 'FURNITURE', '🛋️', 'COMMON', 180, 2, 6, 1, 5),
    (9010006, 'desk_oak', '原木书桌', '读书写字的专属位置，桌面还留着铅笔印。', 'FURNITURE', '🪑', 'RARE', 360, 4, 10, 1, 6),
    (9010007, 'plant_pothos', '窗台绿萝', '不用太多照顾也能活得很好。', 'PLANT', '🪴', 'COMMON', 140, 1, 5, 1, 7),
    (9010008, 'plant_bonsai', '小盆景', '需要每天浇一点点水，会长得很慢。', 'PLANT', '🌿', 'RARE', 340, 6, 9, 1, 8),
    (9010009, 'toy_yarn', '毛线球', '滚到沙发底下过三次，被找回来三次。', 'TOY', '🧶', 'COMMON', 100, 1, 3, 1, 9),
    (9010010, 'toy_ball', '弹力球', '一弹就能弹到房间另一头。', 'TOY', '⚽', 'COMMON', 110, 1, 3, 1, 10),
    (9010011, 'bed_cotton', '棉花小窝', '睡进去只露出一个鼻尖。', 'BED', '🛏️', 'COMMON', 200, 2, 7, 1, 11),
    (9010012, 'bed_starmoon', '星月吊床', '据说睡在这里的宠物会做关于星星的梦。', 'BED', '🌙', 'EPIC', 680, 8, 14, 1, 12);

-- ---- 18. 种子数据：每日任务（8 项 + 全清宝箱由配置给奖）----
INSERT INTO `pet_daily_quest_config`
    (`id`, `code`, `name`, `description`, `icon`, `quest_type`, `target_value`, `exp_reward`, `currency_reward`, `required_level`, `enabled`, `sort`) VALUES
    (9011001, 'daily_feed', '好好吃饭', '喂食 2 次。', '🍖', 'FEED', 2, 20, 20, 1, 1, 1),
    (9011002, 'daily_play', '一起玩耍', '陪宠物玩耍 2 次。', '🎾', 'PLAY', 2, 20, 20, 1, 1, 2),
    (9011003, 'daily_clean', '干干净净', '给宠物清洁 1 次。', '🫧', 'CLEAN', 1, 15, 10, 1, 1, 3),
    (9011004, 'daily_work', '认真打工', '完成 1 次打工。', '💼', 'WORK', 1, 25, 30, 1, 1, 4),
    (9011005, 'daily_study', '每天读书', '完成 1 次读书。', '📚', 'STUDY', 1, 25, 30, 1, 1, 5),
    (9011006, 'daily_bottle', '试试手气', '完成 1 次捞漂流瓶。', '🍾', 'BOTTLE', 1, 25, 30, 1, 1, 6),
    (9011007, 'daily_battle', '切磋一下', '完成 1 场对战。', '⚔️', 'BATTLE', 1, 25, 30, 3, 1, 7),
    (9011008, 'daily_social', '出门交朋友', '串门或好友互访 1 次。', '🚪', 'VISIT', 1, 20, 20, 1, 1, 8),
    (9011009, 'daily_chat', '聊聊天', '和宠物聊天 3 句。', '💬', 'CHAT', 3, 15, 10, 1, 1, 9),
    (9011010, 'daily_decorate', '布置小窝', '摆放或更换 1 件家具。', '🧸', 'DECORATE', 1, 20, 20, 2, 1, 10);

-- ---- 19. 种子数据：三期新增成就（关系/好友/留言/舒适度/任务/陪伴/亲密度）----
INSERT INTO `pet_achievement`
    (`id`, `code`, `name`, `description`, `icon`, `condition_type`, `condition_subtype`, `condition_value`, `exp_reward`, `enabled`, `sort`) VALUES
    (9003016, 'INTIMACY_500', '心有灵犀', '与主人的亲密度达到 500。', '💗', 'INTIMACY', NULL, 500, 80, 1, 16),
    (9003017, 'INTIMACY_2000', '形影不离', '与主人的亲密度达到 2000。', '💞', 'INTIMACY', NULL, 2000, 160, 1, 17),
    (9003018, 'FIRST_RELATION', '结伴同行', '和一只宠物建立起关系。', '🤝', 'RELATION_COUNT', NULL, 1, 40, 1, 18),
    (9003019, 'RELATION_3', '社交达人', '同时拥有 3 段宠物关系。', '👯', 'RELATION_COUNT', NULL, 3, 100, 1, 19),
    (9003020, 'FIRST_FRIEND', '第一个朋友', '添加第一位好友。', '🫂', 'FRIEND_COUNT', NULL, 1, 30, 1, 20),
    (9003021, 'FRIEND_5', '朋友圈', '好友数量达到 5 位。', '🌈', 'FRIEND_COUNT', NULL, 5, 90, 1, 21),
    (9003022, 'WALL_10', '留言小能手', '在别人的留言墙留下 10 条留言。', '📝', 'WALL_MESSAGE_COUNT', NULL, 10, 60, 1, 22),
    (9003023, 'COMFORT_60', '温馨小窝', '家园舒适度达到 60。', '🏡', 'ROOM_COMFORT', NULL, 60, 70, 1, 23),
    (9003024, 'COMFORT_150', '梦想家园', '家园舒适度达到 150。', '🏰', 'ROOM_COMFORT', NULL, 150, 150, 1, 24),
    (9003025, 'QUEST_20', '任务达人', '累计完成 20 个每日任务。', '✅', 'QUEST_COUNT', NULL, 20, 80, 1, 25),
    (9003026, 'COMPANION_5H', '陪伴时光', '累计陪伴宠物 5 小时。', '⏳', 'COMPANION_HOURS', NULL, 5, 60, 1, 26),
    (9003027, 'COMPANION_50H', '最长情的告白', '累计陪伴宠物 50 小时。', '💫', 'COMPANION_HOURS', NULL, 50, 200, 1, 27);
