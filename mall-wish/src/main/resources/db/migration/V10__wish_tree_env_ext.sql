-- =============================================
-- CloudMart 心愿宇宙模块 数据库迁移 V10
-- 模块: mall-wish
-- 说明: Sprint 2.2 生命树动态环境扩展（季节落库/特殊事件/环境配置表化）
-- 内容:
--   ① wish_world_tree_state 新增 season 列（mall-job 每日 00:00 扫描写入，
--      3-5 月春/6-8 月夏/9-11 月秋/12-2 月冬，文档第二章 2.1 / Sprint 2.2）
--   ② wish_special_event 特殊事件表（管理员触发"流星雨"等全站事件，
--      文档 Sprint 2.2 特殊事件管理）
--   ③ wish_env_config 环境配置表（环境配置表化，新增"中秋"等环境
--      只需插入配置行不改代码，文档 Sprint 2.2 管理后台环境配置管理）
-- 决策:
--   1. season 落库为 VARCHAR(16) 而非 ENUM——季节值仅四态且由代码枚举
--      TreeSeason 管理，VARCHAR 兼容历史 NULL（未扫描时读取方 fallback
--      实时计算，Sprint 2.1 的 TreeSeason.from 行为不变）
--   2. wish_special_event 单活跃事件语义由应用层保证（触发新事件时
--      自动结束旧活跃事件）：MySQL 函数唯一索引无法表达
--      status='ACTIVE' 条件唯一且业务上"管理员手动结束"也需更新行，
--      物理约束反而碍事
--   3. visual 采用 JSON 而非固定列——四端渲染参数（树冠色/天空色/
--      粒子类型/树心光色）随前端视觉迭代演进，JSON 透传让
--      "新增环境不改代码"成立（管理端改配置即生效）
--   4. wish_env_config 种子覆盖 12 个基础环境（5 天气 + 4 季节 +
--      3 特殊事件）+ 4 个时段环境，视觉色值与四端前端契约对齐
--      （进度文件四K 第 1 节）
-- 字符集: utf8mb4_0900_ai_ci, 引擎: InnoDB
-- 时区: 所有 DATETIME 字段统一存储 UTC (见文档第26章)
-- =============================================

-- ---------------------------------------------
-- ① wish_world_tree_state 新增 season 列
-- ---------------------------------------------
ALTER TABLE `wish_world_tree_state`
    ADD COLUMN `season` VARCHAR(16) NULL DEFAULT NULL
        COMMENT '当前季节(SPRING/SUMMER/AUTUMN/WINTER,mall-job每日00:00按UTC日期扫描写入;NULL=未扫描,读取方实时计算兜底)'
        AFTER `environment`;

-- 存量回填：立即按 UTC 日期补齐当前季节（不等首次扫描）
UPDATE `wish_world_tree_state`
SET `season` = CASE
    WHEN MONTH(UTC_DATE()) BETWEEN 3 AND 5  THEN 'SPRING'
    WHEN MONTH(UTC_DATE()) BETWEEN 6 AND 8  THEN 'SUMMER'
    WHEN MONTH(UTC_DATE()) BETWEEN 9 AND 11 THEN 'AUTUMN'
    ELSE 'WINTER'
END
WHERE `season` IS NULL;

-- ---------------------------------------------
-- ② wish_special_event 全站特殊事件
-- ---------------------------------------------
-- 语义：管理员（mall-admin 经 Feign 代理）触发"流星雨/极光/星辰夜"等
--       全站同步环境视觉事件；expires_at=NULL 表示持续至手动结束；
--       读取方惰性判定（expires_at 过期视同 ENDED，行保留供审计）。
-- ID策略: 雪花 ID（MyBatis-Plus assign_id，历史事件行不可枚举）
CREATE TABLE IF NOT EXISTS `wish_special_event` (
    `id`           BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花ID)',
    `event_code`   VARCHAR(48) NOT NULL COMMENT '事件代码(关联wish_env_config.env_code,如METEOR_SHOWER/AURORA/STAR_NIGHT)',
    `title`        VARCHAR(64) NOT NULL COMMENT '事件标题(管理端展示)',
    `description`  VARCHAR(255) DEFAULT NULL COMMENT '事件描述(管理端展示)',
    `status`       VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态(ACTIVE活跃/ENDED已结束;过期未标记读取方惰性判定)',
    `triggered_by` BIGINT UNSIGNED NOT NULL COMMENT '触发管理员用户ID(审计)',
    `triggered_at` DATETIME NOT NULL COMMENT '触发时间(UTC,全站同步展示起点)',
    `expires_at`   DATETIME DEFAULT NULL COMMENT '过期时间(UTC;NULL=持续至手动结束)',
    `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_wish_special_event` (`id`),
    KEY `idx_special_event_status` (`status`, `triggered_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='世界生命树全站特殊事件(Sprint2.2,流星雨/极光/星辰夜)';

-- ---------------------------------------------
-- ③ wish_env_config 环境配置表（表化，新增环境不改代码）
-- ---------------------------------------------
-- 语义：每个环境一行，visual JSON 为四端渲染参数（crownColor 树冠描边色/
--       skyColor 天空色调/particle 粒子类型/lightCoreColor 树心光色），
--       priority 为同屏叠加时的渲染优先级（特殊事件 > 情绪 > 天气 >
--       季节 > 时段）。category 仅用于管理端分组展示。
-- 视觉契约：色值与前端四端既有实现对齐（进度文件四K 第 1 节：
--       季节树冠色 春#7ef0c0/夏#3ddc97/秋#ffb347/冬#bfe8ff；
--       环境树心光色 晴#ffd700/雨#4facfe/彩虹#ff9ff3）
CREATE TABLE IF NOT EXISTS `wish_env_config` (
    `id`          BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花ID)',
    `env_code`    VARCHAR(48) NOT NULL COMMENT '环境代码(SUNNY/RAIN/SNOW/CLOUDY/RAINBOW/SPRING/.../METEOR_SHOWER;唯一)',
    `category`    VARCHAR(16) NOT NULL COMMENT '环境分类(WEATHER天气/SEASON季节/TIME时段/SPECIAL_EVENT特殊事件;仅管理端分组展示)',
    `name`        VARCHAR(64) NOT NULL COMMENT '环境名称(如流星雨/中秋)',
    `description` VARCHAR(255) DEFAULT NULL COMMENT '环境描述(管理端展示)',
    `priority`    INT NOT NULL DEFAULT 0 COMMENT '渲染优先级(数值大者胜:SPECIAL_EVENT=100/MOOD=80/WEATHER=50/SEASON=30/TIME=10)',
    `visual`      JSON DEFAULT NULL COMMENT '四端渲染视觉参数JSON(crownColor/skyColor/particle/lightCoreColor等,前端透传消费)',
    `is_active`   TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用(0=下架,读取方过滤)',
    `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_wish_env_config` (`id`),
    UNIQUE KEY `uk_env_code` (`env_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='世界生命树环境配置(表化,Sprint2.2新增环境仅插行不改代码)';

-- =============================================
-- 种子数据: 环境配置（12 基础环境 + 4 时段环境）
-- 幂等：INSERT IGNORE 语义由 uk_env_code 唯一索引兜底（重复执行跳过）
-- =============================================
INSERT IGNORE INTO `wish_env_config`
    (`env_code`, `category`, `name`, `description`, `priority`, `visual`) VALUES
    -- 天气环境（priority 50；真实天气来自和风天气 API，降级晴天）
    ('SUNNY',         'WEATHER', '晴天',   '晴空万里，树心暖金光晕', 50,
     JSON_OBJECT('skyColor', '#87ceeb', 'lightCoreColor', '#ffd700', 'particle', 'NONE')),
    ('CLOUDY',        'WEATHER', '多云',   '云层柔和，树影朦胧',     50,
     JSON_OBJECT('skyColor', '#9aa5b1', 'lightCoreColor', '#cfd8dc', 'particle', 'NONE')),
    ('RAIN',          'WEATHER', '下雨',   '细雨润泽，果实微光涟漪', 50,
     JSON_OBJECT('skyColor', '#5d737e', 'lightCoreColor', '#4facfe', 'particle', 'RAIN')),
    ('SNOW',          'WEATHER', '下雪',   '落雪覆枝，冬夜静谧',     50,
     JSON_OBJECT('skyColor', '#7a8ba3', 'lightCoreColor', '#bfe8ff', 'particle', 'SNOWFLAKE')),
    ('RAINBOW',       'WEATHER', '彩虹',   '雨后初霁，七彩拱桥横跨树冠（情绪联动触发）', 80,
     JSON_OBJECT('skyColor', '#6c5ce7', 'lightCoreColor', '#ff9ff3', 'particle', 'NONE')),
    -- 季节环境（priority 30；树冠色与前端四端契约一致）
    ('SPRING',        'SEASON',  '春季',   '嫩绿花瓣飘落',           30,
     JSON_OBJECT('crownColor', '#7ef0c0', 'particle', 'PETAL')),
    ('SUMMER',        'SEASON',  '夏季',   '绿叶阳光斑驳',           30,
     JSON_OBJECT('crownColor', '#3ddc97', 'particle', 'SUNBURST')),
    ('AUTUMN',        'SEASON',  '秋季',   '金黄落叶纷飞',           30,
     JSON_OBJECT('crownColor', '#ffb347', 'particle', 'LEAF')),
    ('WINTER',        'SEASON',  '冬季',   '枯枝雪花点缀',           30,
     JSON_OBJECT('crownColor', '#bfe8ff', 'particle', 'SNOWFLAKE')),
    -- 时段环境（priority 10；天空底色随时段渐变，前端按本地时区叠加）
    ('DAY',           'TIME',    '白天',   '晨光清朗（06-12 时）',    10,
     JSON_OBJECT('skyColor', '#87ceeb')),
    ('DUSK',          'TIME',    '黄昏',   '暮色橙霞（12-18 时）',    10,
     JSON_OBJECT('skyColor', '#ff9a76')),
    ('NIGHT',         'TIME',    '夜晚',   '星幕初垂（18-24 时）',    10,
     JSON_OBJECT('skyColor', '#0c1b3a')),
    ('LATE_NIGHT',    'TIME',    '深夜',   '万籁俱寂（00-06 时）',    10,
     JSON_OBJECT('skyColor', '#060b18')),
    -- 特殊事件环境（priority 100；管理员触发全站同步）
    ('METEOR_SHOWER', 'SPECIAL_EVENT', '流星雨', '全站流星划过树冠，愿望随星而落', 100,
     JSON_OBJECT('skyColor', '#0c1b3a', 'lightCoreColor', '#ffd700', 'particle', 'METEOR')),
    ('AURORA',        'SPECIAL_EVENT', '极光',   '极光绸缎萦绕世界树',               100,
     JSON_OBJECT('skyColor', '#0a1f2e', 'lightCoreColor', '#7ef0c0', 'particle', 'AURORA')),
    ('STAR_NIGHT',    'SPECIAL_EVENT', '星辰夜', '满天星辰为愿望加冕',               100,
     JSON_OBJECT('skyColor', '#0c1b3a', 'lightCoreColor', '#ffd700', 'particle', 'STAR'));
