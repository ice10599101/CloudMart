-- =============================================
-- CloudMart 社区模块 数据库迁移 V10
-- 模块: mall-community
-- 说明: 编辑器附件——投票与问卷
--       1) 投票/问卷/题目 ID 采用客户端生成 UUID 作为主键：
--          发布时前端幂等落库（INSERT IGNORE 语义），正文无需回写 ID
--       2) 选项行各自自增 ID；投票按 (poll, user, option) 唯一防刷
--       3) 问卷答案按 (survey, question, user) 唯一，可重复提交覆盖
-- =============================================

CREATE TABLE IF NOT EXISTS `community_polls` (
  `id`          varchar(36)   NOT NULL COMMENT '投票ID(客户端生成UUID)',
  `target_type` varchar(20)   NOT NULL COMMENT '宿主内容类型: POST/WISH/CAPSULE/LETTER',
  `target_id`   varchar(64)   NOT NULL COMMENT '宿主内容ID',
  `creator_id`  bigint unsigned NOT NULL COMMENT '创建者用户ID',
  `question`    varchar(200)  NOT NULL COMMENT '投票问题',
  `is_multiple` tinyint(1)    NOT NULL DEFAULT 0 COMMENT '是否多选(1多选/0单选)',
  `created_at`  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  CONSTRAINT `pk_community_polls` PRIMARY KEY (`id`),
  KEY `idx_poll_target` (`target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='投票主表(编辑器附件)';

CREATE TABLE IF NOT EXISTS `community_poll_options` (
  `id`         bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '选项ID',
  `poll_id`    varchar(36) NOT NULL COMMENT '投票ID',
  `content`    varchar(200) NOT NULL COMMENT '选项文本',
  `sort`       int NOT NULL DEFAULT 0 COMMENT '排序(越小越靠前)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_poll_option` (`poll_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='投票选项';

CREATE TABLE IF NOT EXISTS `community_poll_votes` (
  `id`         bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '投票记录ID',
  `poll_id`    varchar(36)    NOT NULL COMMENT '投票ID',
  `option_id`  bigint unsigned NOT NULL COMMENT '选项ID',
  `user_id`    bigint unsigned NOT NULL COMMENT '投票用户ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '投票时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_poll_user_option` (`poll_id`, `user_id`, `option_id`),
  KEY `idx_poll_vote_user` (`poll_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='投票记录';

CREATE TABLE IF NOT EXISTS `community_surveys` (
  `id`          varchar(36) NOT NULL COMMENT '问卷ID(客户端生成UUID)',
  `target_type` varchar(20) NOT NULL COMMENT '宿主内容类型: POST/WISH/CAPSULE/LETTER',
  `target_id`   varchar(64) NOT NULL COMMENT '宿主内容ID',
  `creator_id`  bigint unsigned NOT NULL COMMENT '创建者用户ID',
  `title`       varchar(200) NOT NULL COMMENT '问卷标题',
  `created_at`  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  CONSTRAINT `pk_community_surveys` PRIMARY KEY (`id`),
  KEY `idx_survey_target` (`target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='问卷主表(编辑器附件)';

CREATE TABLE IF NOT EXISTS `community_survey_questions` (
  `id`          bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '题目ID',
  `survey_id`   varchar(36) NOT NULL COMMENT '问卷ID',
  `content`     varchar(500) NOT NULL COMMENT '题干',
  `type`        enum('SINGLE','MULTI','TEXT') NOT NULL DEFAULT 'SINGLE' COMMENT '题型:单选/多选/填空',
  `options`     varchar(2000) NOT NULL DEFAULT '' COMMENT '选项文本JSON数组(填空题为空串)',
  `is_required` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否必答',
  `sort`        int NOT NULL DEFAULT 0 COMMENT '排序(越小越靠前)',
  `created_at`  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_survey_question` (`survey_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='问卷题目';

CREATE TABLE IF NOT EXISTS `community_survey_answers` (
  `id`           bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '答案ID',
  `survey_id`    varchar(36) NOT NULL COMMENT '问卷ID',
  `question_id`  bigint unsigned NOT NULL COMMENT '题目ID',
  `user_id`      bigint unsigned NOT NULL COMMENT '答卷用户ID',
  `option_ids`   varchar(500) DEFAULT NULL COMMENT '选中选项ID的JSON数组(选择题)',
  `text_content` varchar(500) DEFAULT NULL COMMENT '填空内容(填空题)',
  `created_at`   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次提交时间',
  `updated_at`   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_survey_question_user` (`survey_id`, `question_id`, `user_id`),
  KEY `idx_survey_answer_user` (`survey_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='问卷答案(可重复提交覆盖)';
