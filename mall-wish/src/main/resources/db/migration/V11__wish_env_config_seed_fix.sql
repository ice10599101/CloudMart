-- =============================================
-- CloudMart 心愿宇宙模块 数据库迁移 V11
-- 模块: mall-wish
-- 说明: 修复 V10 环境配置种子缺陷（缺 id 列导致仅 1 行生效）
-- 根因:
--   V10 的 INSERT IGNORE 未提供 id，而 wish_env_config.id 为
--   雪花主键（BIGINT UNSIGNED NOT NULL，无 AUTO_INCREMENT）。
--   MySQL 9 严格模式下 INSERT IGNORE 将 ER_NO_DEFAULT_FOR_FIELD
--   降级为警告并取隐式默认值 id=0 → 仅首行 SUNNY 插入成功，
--   其余 15 行因主键 id=0 冲突被 IGNORE 跳过。
-- 修复策略（历史迁移不改，新增迁移兜底）:
--   ① 删除 V10 残留的 id=0 行（含其占用的 uk_env_code=SUNNY）
--   ② 以显式字典表 id（3101-3116，沿用 wish_badge 2001-2004
--      小整数段模式）幂等重种 16 条，与测试基类补种同口径
-- 时区: created_at/updated_at 由数据库 DEFAULT CURRENT_TIMESTAMP 维护 (UTC)
-- =============================================

-- ① 清除 V10 隐式默认 id 的残留行（严格模式库仅此 1 行；无行时为无害空删）
DELETE FROM `wish_env_config` WHERE `id` = 0;

-- ② 显式 id 重种（ON DUPLICATE KEY UPDATE 幂等：重复执行内容回正）
INSERT INTO `wish_env_config`
    (`id`, `env_code`, `category`, `name`, `description`, `priority`, `visual`) VALUES
    -- 天气环境（priority 50；真实天气来自和风天气 API，降级晴天）
    (3101, 'SUNNY',         'WEATHER', '晴天',   '晴空万里，树心暖金光晕', 50,
     JSON_OBJECT('skyColor', '#87ceeb', 'lightCoreColor', '#ffd700', 'particle', 'NONE')),
    (3102, 'CLOUDY',        'WEATHER', '多云',   '云层柔和，树影朦胧',     50,
     JSON_OBJECT('skyColor', '#9aa5b1', 'lightCoreColor', '#cfd8dc', 'particle', 'NONE')),
    (3103, 'RAIN',          'WEATHER', '下雨',   '细雨润泽，果实微光涟漪', 50,
     JSON_OBJECT('skyColor', '#5d737e', 'lightCoreColor', '#4facfe', 'particle', 'RAIN')),
    (3104, 'SNOW',          'WEATHER', '下雪',   '落雪覆枝，冬夜静谧',     50,
     JSON_OBJECT('skyColor', '#7a8ba3', 'lightCoreColor', '#bfe8ff', 'particle', 'SNOWFLAKE')),
    (3105, 'RAINBOW',       'WEATHER', '彩虹',   '雨后初霁，七彩拱桥横跨树冠（情绪联动触发）', 80,
     JSON_OBJECT('skyColor', '#6c5ce7', 'lightCoreColor', '#ff9ff3', 'particle', 'NONE')),
    -- 季节环境（priority 30；树冠色与前端四端契约一致）
    (3106, 'SPRING',        'SEASON',  '春季',   '嫩绿花瓣飘落',           30,
     JSON_OBJECT('crownColor', '#7ef0c0', 'particle', 'PETAL')),
    (3107, 'SUMMER',        'SEASON',  '夏季',   '绿叶阳光斑驳',           30,
     JSON_OBJECT('crownColor', '#3ddc97', 'particle', 'SUNBURST')),
    (3108, 'AUTUMN',        'SEASON',  '秋季',   '金黄落叶纷飞',           30,
     JSON_OBJECT('crownColor', '#ffb347', 'particle', 'LEAF')),
    (3109, 'WINTER',        'SEASON',  '冬季',   '枯枝雪花点缀',           30,
     JSON_OBJECT('crownColor', '#bfe8ff', 'particle', 'SNOWFLAKE')),
    -- 时段环境（priority 10；天空底色随时段渐变，前端按本地时区叠加）
    (3110, 'DAY',           'TIME',    '白天',   '晨光清朗（06-12 时）',    10,
     JSON_OBJECT('skyColor', '#87ceeb')),
    (3111, 'DUSK',          'TIME',    '黄昏',   '暮色橙霞（12-18 时）',    10,
     JSON_OBJECT('skyColor', '#ff9a76')),
    (3112, 'NIGHT',         'TIME',    '夜晚',   '星幕初垂（18-24 时）',    10,
     JSON_OBJECT('skyColor', '#0c1b3a')),
    (3113, 'LATE_NIGHT',    'TIME',    '深夜',   '万籁俱寂（00-06 时）',    10,
     JSON_OBJECT('skyColor', '#060b18')),
    -- 特殊事件环境（priority 100；管理员触发全站同步）
    (3114, 'METEOR_SHOWER', 'SPECIAL_EVENT', '流星雨', '全站流星划过树冠，愿望随星而落', 100,
     JSON_OBJECT('skyColor', '#0c1b3a', 'lightCoreColor', '#ffd700', 'particle', 'METEOR')),
    (3115, 'AURORA',        'SPECIAL_EVENT', '极光',   '极光绸缎萦绕世界树',               100,
     JSON_OBJECT('skyColor', '#0a1f2e', 'lightCoreColor', '#7ef0c0', 'particle', 'AURORA')),
    (3116, 'STAR_NIGHT',    'SPECIAL_EVENT', '星辰夜', '满天星辰为愿望加冕',               100,
     JSON_OBJECT('skyColor', '#0c1b3a', 'lightCoreColor', '#ffd700', 'particle', 'STAR'))
ON DUPLICATE KEY UPDATE `category` = VALUES(`category`), `name` = VALUES(`name`),
    `description` = VALUES(`description`), `priority` = VALUES(`priority`),
    `visual` = VALUES(`visual`), `is_active` = 1;
