-- =============================================
-- CloudMart 心愿宇宙模块 数据库迁移 V17
-- 模块: mall-wish
-- 说明: Sprint 2.8 体验打磨与全量（灰度控制 + AI 质量抽检 + 索引体检）
--       wish_grayscale_config  灰度比例配置（按用户哈希分流，同一用户恒同档）
--       wish_ai_review         AI 回复人工抽检（合格率/问题分类统计）
--       wish 表补 2 个榜单复合索引（排行榜全量扫描支撑，EXPLAIN 见文档）
-- ID策略: 雪花算法 (MyBatis-Plus assign_id), 不使用 AUTO_INCREMENT
-- 字符集: utf8mb4_0900_ai_ci, 引擎: InnoDB
-- =============================================

-- ---------------------------------------------
-- wish_grayscale_config 灰度比例配置
-- feature_key 全局固定清单（代码枚举），gray_ratio 0/5/20/50/100；
-- 路由：bucket = 稳定哈希(userId × feature_key) % 100 < ratio → 命中灰度
-- （同一用户恒命中同一档，文档 2.8 验收）；回滚 = ratio 置 0。
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_grayscale_config` (
    `id`           BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `feature_key`  VARCHAR(64) NOT NULL COMMENT '功能键(代码枚举白名单)',
    `gray_ratio`   INT NOT NULL DEFAULT 0 COMMENT '灰度比例(0-100,回滚=置0)',
    `description`  VARCHAR(200) DEFAULT NULL COMMENT '功能说明',
    `updated_by`   BIGINT UNSIGNED DEFAULT NULL COMMENT '最后修改人(管理后台用户ID)',
    `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    `updated_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间(UTC)',
    PRIMARY KEY `pk_wish_grayscale_config` (`id`),
    UNIQUE KEY `uk_grayscale_feature` (`feature_key`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='灰度比例配置(哈希分流,可回滚)';

-- ---------------------------------------------
-- wish_ai_review AI 回复人工抽检（文档 2.7：AI 质量抽检）
-- 管理端生成抽检样本（随机抽 ASSISTANT 回复）→ 人工评分
-- （PASS/FAIL + 问题分类：机械感/错误信息/不相关）→ 合格率统计。
-- ---------------------------------------------
CREATE TABLE IF NOT EXISTS `wish_ai_review` (
    `id`              BIGINT UNSIGNED NOT NULL COMMENT '主键(雪花算法)',
    `conversation_id` BIGINT UNSIGNED NOT NULL COMMENT '被抽检的 AI 回复ID(wish_ai_conversation)',
    `scene`           VARCHAR(32) NOT NULL COMMENT 'AI场景(GOAL_BREAKDOWN/TREE_HOLE/ANNUAL_REPORT/EXPECTED_GUIDE)',
    `content`         TEXT NOT NULL COMMENT '抽检时回复内容快照(后改不影响评分对象)',
    `result`          ENUM('PASS','FAIL') DEFAULT NULL COMMENT '人工评分(NULL=未评)',
    `issue_type`      VARCHAR(32) DEFAULT NULL COMMENT '问题分类(MECHANICAL机械感/ERROR错误信息/IRRELEVANT不相关,FAIL时填写)',
    `note`            VARCHAR(200) DEFAULT NULL COMMENT '评语',
    `reviewed_by`     BIGINT UNSIGNED DEFAULT NULL COMMENT '评分人(管理后台用户ID)',
    `reviewed_at`     DATETIME DEFAULT NULL COMMENT '评分时间(UTC)',
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(UTC)',
    PRIMARY KEY `pk_wish_ai_review` (`id`),
    UNIQUE KEY `uk_review_conversation` (`conversation_id`),
    INDEX `idx_review_scene` (`scene`, `result`),
    INDEX `idx_review_reviewed` (`reviewed_at`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI回复人工抽检(合格率/问题分类)';

-- ---------------------------------------------
-- 榜单/热门查询复合索引（EXPLAIN 体检：排行榜每 10 分钟全量扫描
-- is_visible + audit_status + light/bless 排序，单列索引不覆盖）
-- ---------------------------------------------
ALTER TABLE `wish`
    ADD INDEX `idx_lb_light` (`is_visible`, `audit_status`, `light_count`),
    ADD INDEX `idx_lb_bless` (`is_visible`, `audit_status`, `bless_count`);
