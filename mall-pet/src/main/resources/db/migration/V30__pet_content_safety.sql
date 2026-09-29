-- P0-1 内容安全：敏感词库表（可热更新，服务端内存自动机按版本指纹轮询刷新）
-- 拦截点：宠物名（拒绝）/ 留言墙（拒绝）/ 宠物聊天危机词（安抚 + 自动举报，见 V31 pet_report 扩展）

CREATE TABLE IF NOT EXISTS `pet_content_sensitive_word` (
    `id`         BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `word`       VARCHAR(64) NOT NULL COMMENT '敏感词（匹配时忽略大小写）',
    `category`   ENUM('POLITICS','ABUSE','AD','CRISIS') NOT NULL COMMENT '类别：POLITICS政治/ABUSE辱骂/AD广告导流/CRISIS危机干预',
    `status`     TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用 0停用',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间(UTC,毫秒)：词库热更新指纹依据，禁止业务写入',
    PRIMARY KEY `pk_pet_content_sensitive_word` (`id`),
    UNIQUE KEY `uk_word` (`word`),
    INDEX `idx_pet_sensitive_word_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='宠物内容安全敏感词库(内存自动机热加载)';

-- 初始种子词库：仅覆盖高频底线场景，运营通过管理端 CRUD 持续维护
INSERT INTO `pet_content_sensitive_word` (`id`, `word`, `category`) VALUES
    (1930000000000000001, '自杀', 'CRISIS'),
    (1930000000000000002, '轻生', 'CRISIS'),
    (1930000000000000003, '不想活', 'CRISIS'),
    (1930000000000000004, '想死', 'CRISIS'),
    (1930000000000000005, '自残', 'CRISIS'),
    (1930000000000000006, '结束生命', 'CRISIS'),
    (1930000000000000007, '加微信', 'AD'),
    (1930000000000000008, '加QQ', 'AD'),
    (1930000000000000009, '兼职刷单', 'AD'),
    (1930000000000000010, '免费领红包', 'AD'),
    (1930000000000000011, '代练', 'AD'),
    (1930000000000000012, '傻逼', 'ABUSE'),
    (1930000000000000013, '滚蛋', 'ABUSE'),
    (1930000000000000014, '废物', 'ABUSE'),
    (1930000000000000015, '脑残', 'ABUSE');
