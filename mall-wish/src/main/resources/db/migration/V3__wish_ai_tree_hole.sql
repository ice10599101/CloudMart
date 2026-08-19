-- =============================================
-- CloudMart 心愿宇宙模块 数据库迁移 V3
-- 模块: mall-wish
-- 说明: Sprint 1.3 AI 树洞治愈回复
--       ⑳ wish_consent（用户同意记录，文档 34.2 / 39.8）
--       ㊲c wish_ai_conversation（AI 对话历史，文档 2.11 联动）
-- 决策: 1. session_id 采用普通索引而非文档定义的 uk_ai_session——
--          同一会话含 USER/ASSISTANT 多条记录，唯一约束与按角色分条存储矛盾；
--          会话逻辑唯一性由业务层生成规则保证（{scene}:{wishId}:{userId}）。
--       2. sentiment_score 按 ㊲c 定义存储 TINYINT（-100~100 整数），
--          API 层返回 -1.0~+1.0 浮点（文档 30.1），由 Service 层换算。
-- ID策略: 雪花算法 (MyBatis-Plus assign_id), 不使用 AUTO_INCREMENT
-- 字符集: utf8mb4_0900_ai_ci, 引擎: InnoDB
-- 时区: 所有 DATETIME 字段统一存储 UTC (见文档第26章)
-- =============================================

-- ---------------------------------------------
-- wish_consent 用户同意记录（GDPR / 个保法留痕）
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_consent` (
    `id`                BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `user_id`           BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `consent_type`      ENUM('PRIVACY_POLICY','AI_DATA_PROCESSING','BRAND_DATA_SHARE') NOT NULL COMMENT '同意类型: 隐私政策/AI数据处理/品牌数据共享',
    `version`           VARCHAR(20) NOT NULL COMMENT '协议版本号(每次协议更新递增)',
    `consent_text_hash` VARCHAR(64) NOT NULL COMMENT '同意时协议文本SHA-256哈希(防篡改)',
    `action`            ENUM('GRANT','WITHDRAW') NOT NULL COMMENT '动作: GRANT同意/WITHDRAW撤回',
    `ip`                VARCHAR(45) DEFAULT NULL COMMENT '操作IP(IPv4/IPv6)',
    `user_agent`        VARCHAR(255) DEFAULT NULL COMMENT '操作User-Agent',
    `created_at`        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    PRIMARY KEY `pk_wish_consent` (`id`),
    UNIQUE KEY `uk_consent_unique` (`user_id`, `consent_type`, `version`, `action`),
    INDEX `idx_consent_user` (`user_id`, `consent_type`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户同意记录(合规留痕)';

-- ---------------------------------------------
-- wish_ai_conversation AI 对话历史（树洞/AI助手）
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_ai_conversation` (
    `id`               BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `user_id`          BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    `session_id`       VARCHAR(64) NOT NULL COMMENT '会话ID(树洞: tree-hole-{wishId}-{userId})',
    `scene`            ENUM('GOAL_BREAKDOWN','TREE_HOLE','ANNUAL_REPORT') NOT NULL COMMENT '场景: 目标拆解/树洞/年度报告',
    `role`             ENUM('USER','ASSISTANT') NOT NULL COMMENT '角色: 用户消息/AI回复',
    `content`          TEXT NOT NULL COMMENT '消息内容(发送侧已脱敏,存储原始对话)',
    `sentiment_score`  TINYINT DEFAULT NULL COMMENT '情感分数(-100~100,仅TREE_HOLE场景ASSISTANT记录)',
    `resources`        JSON DEFAULT NULL COMMENT '推荐资源JSON(仅TREE_HOLE场景ASSISTANT记录)',
    `deleted_at`       DATETIME DEFAULT NULL COMMENT '软删除时间',
    `created_at`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_wish_ai_conversation` (`id`),
    INDEX `idx_ai_conv_user` (`user_id`, `scene`, `created_at`),
    INDEX `idx_ai_conv_session` (`session_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI对话历史(树洞/AI助手/年度报告)';
