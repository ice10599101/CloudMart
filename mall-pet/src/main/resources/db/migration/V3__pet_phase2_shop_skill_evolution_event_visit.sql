-- CloudMart 社区宠物模块 V3：二期能力（原文档 §1.1 宠物串门 / §89 第二阶段）
-- 覆盖：宠物装备与背包、宠物技能、宠物进化、宠物皮肤商城、社区宠物活动、多宠物（主宠切换）
-- 复用边界不变：购买/进化消耗的星光走 mall-wish 内部端点，背包是本模块内的"宠物物品"，不复用商城库存。

-- ---- 1. 宠物主表扩展：多宠物（主宠）+ 进化阶段 + 皮肤 ----
ALTER TABLE `pet`
    ADD COLUMN `evolution_stage` TINYINT NOT NULL DEFAULT 0 COMMENT '进化阶段(0未进化/1一阶/2二阶; 由pet_evolution_config驱动, 影响属性上限与皮肤解锁)' AFTER `growth_stage`,
    ADD COLUMN `skin_code` VARCHAR(60) DEFAULT NULL COMMENT '当前穿戴皮肤编码(pet_skin_config.code, NULL=原生外观)' AFTER `appearance`,
    ADD COLUMN `is_active` TINYINT NOT NULL DEFAULT 1 COMMENT '是否当前主宠(1主宠/0备选; 每用户至多一只主宠)' AFTER `is_public`;

-- 一用户多宠：原 uk_pet_user 改为"每用户至多一只主宠"函数唯一索引（同 pet_activity 先例）
ALTER TABLE `pet` DROP INDEX `uk_pet_user`;
ALTER TABLE `pet`
    ADD UNIQUE KEY `uk_pet_user_active` (`user_id`, (IF(`is_active` = 1 AND `deleted_at` IS NULL, 1, NULL))),
    ADD INDEX `idx_pet_user` (`user_id`);

-- ---- 2. 活动类型扩展：串门 / 进化（统一 pet_activity 状态机，不另建任务框架）----
ALTER TABLE `pet_activity`
    MODIFY COLUMN `activity_type` ENUM('WORK','STUDY','BOTTLE_FISHING','REST','FEED','PLAY','CLEAN','VISIT','EVOLVE')
        NOT NULL COMMENT '活动类型:打工/读书/捞瓶/休息/喂食/玩耍/清洁/串门/进化(后五者为即时行为留痕,直接CLAIMED)';

-- ---- 3. 成就判定维度扩展：串门 / 进化 ----
ALTER TABLE `pet_achievement`
    MODIFY COLUMN `condition_type` ENUM('BOTTLE_COUNT','BATTLE_WIN','LEVEL','CHAT_COUNT','STATS_FULL','ACTIVITY_COUNT','VISIT_COUNT','EVOLUTION')
        NOT NULL COMMENT '判定类型:捞瓶数/胜场/等级/聊天句数/满属性/行为计数/串门数/进化阶数';

-- ---- 4. 宠物背包（装备/皮肤/技能书统一入包；购买即入包，装备/穿戴/学习再消费）----
CREATE TABLE IF NOT EXISTS `pet_inventory` (
    `id`          BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `pet_id`      BIGINT UNSIGNED NOT NULL COMMENT '宠物ID',
    `user_id`     BIGINT UNSIGNED NOT NULL COMMENT '用户ID(审计/查询冗余)',
    `item_type`   ENUM('EQUIPMENT','SKIN','SKILL_BOOK') NOT NULL COMMENT '物品类型:装备/皮肤/技能书',
    `item_code`   VARCHAR(60) NOT NULL COMMENT '物品编码(对应各配置表code)',
    `quantity`    INT NOT NULL DEFAULT 1 COMMENT '持有数量',
    `equipped`    TINYINT NOT NULL DEFAULT 0 COMMENT '是否处于装备/穿戴状态(装备与皮肤有效)',
    `slot`        VARCHAR(20) DEFAULT NULL COMMENT '装备部位(装备类型有效: HAT/NECKLACE/SCARF/BACKPACK)',
    `acquired_at` DATETIME NOT NULL COMMENT '获得时间(UTC)',
    `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_inventory` (`id`),
    UNIQUE KEY `uk_pet_inventory_item` (`pet_id`, `item_type`, `item_code`),
    INDEX `idx_pet_inventory_user` (`user_id`),
    INDEX `idx_pet_inventory_equipped` (`pet_id`, `equipped`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物背包(装备/皮肤/技能书)';

-- ---- 5. 装备配置 ----
CREATE TABLE IF NOT EXISTS `pet_equipment_config` (
    `id`                       BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `code`                     VARCHAR(60) NOT NULL COMMENT '唯一编码',
    `name`                     VARCHAR(60) NOT NULL COMMENT '装备名',
    `description`              VARCHAR(255) NOT NULL DEFAULT '' COMMENT '装备描述',
    `slot`                     ENUM('HAT','NECKLACE','SCARF','BACKPACK') NOT NULL COMMENT '部位:帽子/项圈/围巾/背包',
    `icon`                     VARCHAR(30) NOT NULL DEFAULT '🎀' COMMENT '图标(emoji或URL)',
    `rarity`                   ENUM('COMMON','RARE','EPIC') NOT NULL DEFAULT 'COMMON' COMMENT '稀有度',
    `price_starlight`          INT NOT NULL DEFAULT 0 COMMENT '售价(星光)',
    `bonus_strength`           INT NOT NULL DEFAULT 0 COMMENT '力量加成',
    `bonus_intelligence`       INT NOT NULL DEFAULT 0 COMMENT '智力加成',
    `bonus_agility`            INT NOT NULL DEFAULT 0 COMMENT '敏捷加成',
    `bonus_charm`              INT NOT NULL DEFAULT 0 COMMENT '魅力加成',
    `bonus_max_hp`             INT NOT NULL DEFAULT 0 COMMENT '生命上限加成',
    `required_level`           INT NOT NULL DEFAULT 1 COMMENT '购买/装备最低等级',
    `required_evolution_stage` TINYINT NOT NULL DEFAULT 0 COMMENT '购买/装备最低进化阶段',
    `enabled`                  TINYINT NOT NULL DEFAULT 1 COMMENT '是否上架(1上架/0下架)',
    `sort`                     INT NOT NULL DEFAULT 0 COMMENT '排序(升序)',
    `created_at`               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_equipment_config` (`id`),
    UNIQUE KEY `uk_pet_equipment_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物装备配置(商城在售)';

-- ---- 6. 皮肤配置（外观预设，穿戴即写 pet.appearance/skin_code）----
CREATE TABLE IF NOT EXISTS `pet_skin_config` (
    `id`                       BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `code`                     VARCHAR(60) NOT NULL COMMENT '唯一编码',
    `name`                     VARCHAR(60) NOT NULL COMMENT '皮肤名',
    `description`              VARCHAR(255) NOT NULL DEFAULT '' COMMENT '皮肤描述',
    `species`                  VARCHAR(20) DEFAULT NULL COMMENT '限定种类(CAT/DOG/RABBIT/FOX/PANDA, NULL=通用)',
    `color`                    VARCHAR(20) NOT NULL COMMENT '主色(前端/Cocos 调色板键)',
    `accessory`                VARCHAR(20) NOT NULL DEFAULT 'none' COMMENT '配饰键',
    `icon`                     VARCHAR(30) NOT NULL DEFAULT '✨' COMMENT '图标(emoji或URL)',
    `rarity`                   ENUM('COMMON','RARE','EPIC') NOT NULL DEFAULT 'COMMON' COMMENT '稀有度',
    `price_starlight`          INT NOT NULL DEFAULT 0 COMMENT '售价(星光)',
    `required_level`           INT NOT NULL DEFAULT 1 COMMENT '购买/穿戴最低等级',
    `required_evolution_stage` TINYINT NOT NULL DEFAULT 0 COMMENT '购买/穿戴最低进化阶段',
    `enabled`                  TINYINT NOT NULL DEFAULT 1 COMMENT '是否上架(1上架/0下架)',
    `sort`                     INT NOT NULL DEFAULT 0 COMMENT '排序(升序)',
    `created_at`               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_skin_config` (`id`),
    UNIQUE KEY `uk_pet_skin_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物皮肤配置(商城在售)';

-- ---- 7. 技能配置（主动技参与战斗演出；被动技影响战斗/捞瓶/读书收益）----
CREATE TABLE IF NOT EXISTS `pet_skill_config` (
    `id`              BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `code`            VARCHAR(60) NOT NULL COMMENT '唯一编码',
    `name`            VARCHAR(60) NOT NULL COMMENT '技能名',
    `description`     VARCHAR(255) NOT NULL DEFAULT '' COMMENT '技能描述',
    `skill_type`      ENUM('ACTIVE','PASSIVE') NOT NULL COMMENT '类型:主动/被动',
    `effect`          ENUM('POWER_STRIKE','LUCKY_FISH','QUICK_STEP','BOOKWORM','CHARM_AURA','TOUGH_BODY') NOT NULL COMMENT '效果标识(服务端公式唯一开关)',
    `effect_value`    DECIMAL(6,3) NOT NULL DEFAULT 0.000 COMMENT '效果数值(百分比为0-1小数/点数为整数)',
    `icon`            VARCHAR(30) NOT NULL DEFAULT '🌟' COMMENT '图标(emoji或URL)',
    `price_starlight` INT NOT NULL DEFAULT 0 COMMENT '售价(星光,购买后落背包技能书)',
    `required_level`  INT NOT NULL DEFAULT 1 COMMENT '学习最低等级',
    `enabled`         TINYINT NOT NULL DEFAULT 1 COMMENT '是否上架(1上架/0下架)',
    `sort`            INT NOT NULL DEFAULT 0 COMMENT '排序(升序)',
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_skill_config` (`id`),
    UNIQUE KEY `uk_pet_skill_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物技能配置';

-- ---- 8. 宠物已学技能 ----
CREATE TABLE IF NOT EXISTS `pet_skill` (
    `id`         BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `pet_id`     BIGINT UNSIGNED NOT NULL COMMENT '宠物ID',
    `user_id`    BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `skill_code` VARCHAR(60) NOT NULL COMMENT '技能编码(pet_skill_config.code)',
    `equipped`   TINYINT NOT NULL DEFAULT 1 COMMENT '是否已装配(一期学习即装配,保留字段供多技能切换)',
    `learned_at` DATETIME NOT NULL COMMENT '学习时间(UTC)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_skill` (`id`),
    UNIQUE KEY `uk_pet_skill` (`pet_id`, `skill_code`),
    INDEX `idx_pet_skill_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物已学技能(uk幂等,同一技能只学一次)';

-- ---- 9. 进化配置（等级门槛 + 星光消耗 → 提升属性上限并解锁皮肤）----
CREATE TABLE IF NOT EXISTS `pet_evolution_config` (
    `id`                BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `code`              VARCHAR(60) NOT NULL COMMENT '唯一编码',
    `name`              VARCHAR(60) NOT NULL COMMENT '进化名',
    `description`       VARCHAR(255) NOT NULL DEFAULT '' COMMENT '进化描述',
    `stage_from`        TINYINT NOT NULL COMMENT '起始进化阶段',
    `stage_to`          TINYINT NOT NULL COMMENT '目标进化阶段',
    `required_level`    INT NOT NULL COMMENT '所需等级',
    `cost_starlight`    INT NOT NULL DEFAULT 0 COMMENT '消耗星光',
    `bonus_max_hp`      INT NOT NULL DEFAULT 0 COMMENT '生命上限提升',
    `bonus_strength`    INT NOT NULL DEFAULT 0 COMMENT '力量提升',
    `bonus_intelligence` INT NOT NULL DEFAULT 0 COMMENT '智力提升',
    `bonus_agility`     INT NOT NULL DEFAULT 0 COMMENT '敏捷提升',
    `bonus_charm`       INT NOT NULL DEFAULT 0 COMMENT '魅力提升',
    `unlock_skin_code`  VARCHAR(60) DEFAULT NULL COMMENT '解锁皮肤编码(pet_skin_config.code, 可空)',
    `icon`              VARCHAR(30) NOT NULL DEFAULT '🌠' COMMENT '图标(emoji或URL)',
    `enabled`           TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用(1启用/0停用)',
    `sort`              INT NOT NULL DEFAULT 0 COMMENT '排序(升序)',
    `created_at`        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_evolution_config` (`id`),
    UNIQUE KEY `uk_pet_evolution_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物进化配置';

-- ---- 10. 社区宠物活动配置（原文档 §89 社区宠物活动）----
CREATE TABLE IF NOT EXISTS `pet_event_config` (
    `id`               BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `code`             VARCHAR(60) NOT NULL COMMENT '唯一编码',
    `name`             VARCHAR(60) NOT NULL COMMENT '活动名',
    `description`      VARCHAR(255) NOT NULL DEFAULT '' COMMENT '活动描述',
    `event_type`       ENUM('BOTTLE','BATTLE','WORK','STUDY','FEED','PLAY','VISIT') NOT NULL COMMENT '统计口径:捞瓶/对战/打工/读书/喂食/玩耍/串门次数',
    `target_value`     INT NOT NULL COMMENT '目标次数',
    `reward_starlight` INT NOT NULL DEFAULT 0 COMMENT '奖励星光',
    `reward_exp`       INT NOT NULL DEFAULT 0 COMMENT '奖励宠物经验',
    `reward_item_code` VARCHAR(60) DEFAULT NULL COMMENT '额外奖励物品编码(pet_equipment_config.code, 可空)',
    `starts_at`        DATETIME DEFAULT NULL COMMENT '开始时间(NULL=常驻,UTC)',
    `ends_at`          DATETIME DEFAULT NULL COMMENT '结束时间(NULL=常驻,UTC)',
    `enabled`          TINYINT NOT NULL DEFAULT 1 COMMENT '是否上架(1上架/0下架)',
    `sort`             INT NOT NULL DEFAULT 0 COMMENT '排序(升序)',
    `created_at`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_event_config` (`id`),
    UNIQUE KEY `uk_pet_event_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='社区宠物活动配置(常驻/限时)';

-- ---- 11. 宠物活动进度 ----
CREATE TABLE IF NOT EXISTS `pet_event_progress` (
    `id`         BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `pet_id`     BIGINT UNSIGNED NOT NULL COMMENT '宠物ID',
    `user_id`    BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `event_code` VARCHAR(60) NOT NULL COMMENT '活动编码(pet_event_config.code)',
    `progress`   INT NOT NULL DEFAULT 0 COMMENT '已完成次数(惰性统计落库)',
    `claimed_at` DATETIME DEFAULT NULL COMMENT '领奖时间(非空=已领取,幂等标记)',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_pet_event_progress` (`id`),
    UNIQUE KEY `uk_pet_event_progress` (`pet_id`, `event_code`),
    INDEX `idx_pet_event_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物活动进度(进度由业务表惰性统计,不在写入路径累加)';

-- ---- 12. 种子数据：装备（4 部位 × 2 档）----
INSERT INTO `pet_equipment_config`
    (`id`, `code`, `name`, `description`, `slot`, `icon`, `rarity`, `price_starlight`,
     `bonus_strength`, `bonus_intelligence`, `bonus_agility`, `bonus_charm`, `bonus_max_hp`,
     `required_level`, `required_evolution_stage`, `enabled`, `sort`) VALUES
    (9004001, 'straw_hat', '草编渔夫帽', '海边散步必备，据说戴着更容易捞到漂流瓶。', 'HAT', '👒', 'COMMON', 120, 0, 0, 1, 1, 0, 1, 0, 1, 1),
    (9004002, 'explorer_cap', '探险家鸭舌帽', '后山探险队的队帽，耐磨又精神。', 'HAT', '🧢', 'RARE', 320, 1, 1, 1, 1, 5, 5, 0, 1, 2),
    (9004003, 'bell_collar', '叮当项圈', '走一步响一声，主人再也不会找不到我。', 'NECKLACE', '🔔', 'COMMON', 150, 0, 0, 2, 0, 0, 1, 0, 1, 3),
    (9004004, 'crystal_pendant', '星辉吊坠', '把捞到的第一颗星星碎片挂在了脖子上。', 'NECKLACE', '💎', 'EPIC', 680, 0, 3, 0, 4, 10, 10, 1, 1, 4),
    (9004005, 'knit_scarf', '针织围巾', '冬天读书时最舒服的装备。', 'SCARF', '🧣', 'COMMON', 130, 0, 2, 0, 0, 0, 1, 0, 1, 5),
    (9004006, 'silk_scarf', '丝绒披巾', '在社区舞台上一亮相就很吸睛。', 'SCARF', '🎀', 'RARE', 380, 1, 0, 0, 3, 0, 6, 0, 1, 6),
    (9004007, 'canvas_pack', '帆布小书包', '装得下三本课外书和一块干粮。', 'BACKPACK', '🎒', 'COMMON', 160, 2, 0, 0, 0, 0, 1, 0, 1, 7),
    (9004008, 'star_pack', '星轨行囊', '据说是探险家前辈留下的行囊，装着整片星空。', 'BACKPACK', '🌌', 'EPIC', 720, 2, 2, 2, 2, 15, 12, 1, 1, 8);

-- ---- 13. 种子数据：皮肤（原文档 §89 宠物皮肤）----
INSERT INTO `pet_skin_config`
    (`id`, `code`, `name`, `description`, `species`, `color`, `accessory`, `icon`, `rarity`, `price_starlight`,
     `required_level`, `required_evolution_stage`, `enabled`, `sort`) VALUES
    (9005001, 'mint_cat', '薄荷奶盖', '清爽的薄荷色猫咪，配一枚蝴蝶结。', 'CAT', 'mint', 'bow', '🍃', 'COMMON', 260, 2, 0, 1, 1),
    (9005002, 'golden_dog', '金渐层柴', '阳光下毛色会发光的柴犬。', 'DOG', 'golden', 'bandana', '🌟', 'COMMON', 260, 2, 0, 1, 2),
    (9005003, 'snow_rabbit', '雪团兔', '像一团会跳的雪，围着暖围巾。', 'RABBIT', 'snow', 'scarf', '❄️', 'COMMON', 280, 2, 0, 1, 3),
    (9005004, 'midnight_fox', '夜行狐狸', '夜色皮毛上别着一颗小星星。', 'FOX', 'midnight', 'star', '🌙', 'RARE', 420, 5, 0, 1, 4),
    (9005005, 'panda_pajama', '熊猫睡衣', '穿着睡衣也想接着睡一会儿。', 'PANDA', 'ink', 'pajama', '🎋', 'RARE', 420, 5, 0, 1, 5),
    (9005006, 'aurora_legend', '极光传说', '只有完成一阶进化的小宠物才能驾驭的极光色。', NULL, 'aurora', 'crown', '👑', 'EPIC', 880, 10, 1, 1, 6);

-- ---- 14. 种子数据：技能（1 主动 + 5 被动）----
INSERT INTO `pet_skill_config`
    (`id`, `code`, `name`, `description`, `skill_type`, `effect`, `effect_value`, `icon`, `price_starlight`, `required_level`, `enabled`, `sort`) VALUES
    (9006001, 'power_strike', '大力一击', '主动技：首回合全力出击，伤害提升 35%。', 'ACTIVE', 'POWER_STRIKE', 0.350, '💥', 500, 3, 1, 1),
    (9006002, 'lucky_fish', '幸运打捞', '被动技：捞漂流瓶成功率 +8%。', 'PASSIVE', 'LUCKY_FISH', 0.080, '🍀', 420, 2, 1, 2),
    (9006003, 'quick_step', '迅捷身法', '被动技：战斗先手判定敏捷 +3。', 'PASSIVE', 'QUICK_STEP', 3.000, '💨', 380, 4, 1, 3),
    (9006004, 'bookworm', '博览群书', '被动技：读书经验收益 +15%。', 'PASSIVE', 'BOOKWORM', 0.150, '📖', 450, 3, 1, 4),
    (9006005, 'charm_aura', '魅力光环', '被动技：战斗暴击率 +6%。', 'PASSIVE', 'CHARM_AURA', 0.060, '💖', 480, 5, 1, 5),
    (9006006, 'tough_body', '硬朗体魄', '被动技：战斗中受到的伤害降低 12%。', 'PASSIVE', 'TOUGH_BODY', 0.120, '🛡️', 520, 6, 1, 6);

-- ---- 15. 种子数据：进化（两阶）----
INSERT INTO `pet_evolution_config`
    (`id`, `code`, `name`, `description`, `stage_from`, `stage_to`, `required_level`, `cost_starlight`,
     `bonus_max_hp`, `bonus_strength`, `bonus_intelligence`, `bonus_agility`, `bonus_charm`,
     `unlock_skin_code`, `icon`, `enabled`, `sort`) VALUES
    (9007001, 'evolve_1', '初阶进化', '毛发泛起微光，身体更结实了。', 0, 1, 8, 600, 25, 3, 3, 3, 3, 'aurora_legend', '🌠', 1, 1),
    (9007002, 'evolve_2', '高阶进化', '气场全开，社区里谁见了都要夸一句。', 1, 2, 18, 1500, 50, 6, 6, 6, 6, NULL, '✨', 1, 2);

-- ---- 16. 种子数据：社区宠物活动（常驻 + 限时）----
INSERT INTO `pet_event_config`
    (`id`, `code`, `name`, `description`, `event_type`, `target_value`, `reward_starlight`, `reward_exp`,
     `reward_item_code`, `starts_at`, `ends_at`, `enabled`, `sort`) VALUES
    (9008001, 'bottle_newbie', '捞瓶新星', '累计让宠物捞起 3 只漂流瓶（含空手而归的任务完成次数）。', 'BOTTLE', 3, 120, 40, NULL, NULL, NULL, 1, 1),
    (9008002, 'work_week', '打工小能手', '累计完成 5 次打工。', 'WORK', 5, 200, 60, NULL, NULL, NULL, 1, 2),
    (9008003, 'visit_week', '串门周', '去邻居家串门 5 次，替主人交个朋友。', 'VISIT', 5, 180, 50, NULL, NULL, NULL, 1, 3),
    (9008004, 'battle_fest', '对战狂欢季', '活动期间赢下 3 场对战，赢取星轨行囊。', 'BATTLE', 3, 260, 80, 'star_pack',
     DATE_SUB(UTC_TIMESTAMP(), INTERVAL 1 DAY), DATE_ADD(UTC_TIMESTAMP(), INTERVAL 90 DAY), 1, 4);

-- ---- 17. 种子数据：二期新增成就（串门/进化）----
INSERT INTO `pet_achievement`
    (`id`, `code`, `name`, `description`, `icon`, `condition_type`, `condition_subtype`, `condition_value`, `exp_reward`, `enabled`, `sort`) VALUES
    (9003013, 'FIRST_VISIT', '串门新客', '第一次去邻居家串门。', '🚪', 'VISIT_COUNT', NULL, 1, 20, 1, 13),
    (9003014, 'VISIT_20', '社区常客', '累计串门 20 次。', '🏘️', 'VISIT_COUNT', NULL, 20, 60, 1, 14),
    (9003015, 'EVOLVE_1', '进化之光', '完成第一次进化。', '🌠', 'EVOLUTION', NULL, 1, 80, 1, 15);
