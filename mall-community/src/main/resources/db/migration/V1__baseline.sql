-- C26 clean development/test baseline. Recreated EMPTY databases only.
-- Materialized from verified MySQL schema; never repair an old history to adopt this file.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE `badges` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(50) NOT NULL COMMENT '徽章名称',
  `icon` varchar(255) NOT NULL COMMENT '徽章图标URL',
  `description` varchar(200) NOT NULL COMMENT '徽章描述',
  `condition` json DEFAULT NULL COMMENT '获得条件',
  `level` tinyint NOT NULL DEFAULT '1' COMMENT '等级',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态: 0-禁用 1-启用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='成就徽章表';

CREATE TABLE `browse_histories` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `user_id` bigint unsigned NOT NULL COMMENT '浏览用户ID',
  `target_type` varchar(20) NOT NULL COMMENT '浏览对象类型：PRODUCT-商品 POST-帖子 WISH-心愿',
  `target_id` bigint unsigned NOT NULL COMMENT '浏览对象ID',
  `title` varchar(200) NOT NULL DEFAULT '' COMMENT '浏览对象标题快照',
  `cover` varchar(500) NOT NULL DEFAULT '' COMMENT '浏览对象封面快照',
  `viewed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近浏览时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次浏览时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_target` (`user_id`,`target_type`,`target_id`),
  KEY `idx_user_viewed` (`user_id`,`viewed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='浏览足迹表';

CREATE TABLE `community_exp_daily_quota` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint unsigned NOT NULL COMMENT '用户 ID',
  `business_date` date NOT NULL COMMENT '业务日期（服务器日；R22 起为用户业务日）',
  `granted` int NOT NULL DEFAULT '0' COMMENT '当日已占用经验额度（原子累加，<=200）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_date` (`user_id`,`business_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='R21 社区经验日配额占用（行锁原子发放）';

CREATE TABLE `community_poll_ballots` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '选票ID',
  `poll_id` varchar(36) NOT NULL COMMENT '投票ID',
  `user_id` bigint unsigned NOT NULL COMMENT '投票用户ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '投票时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ballot_poll_user` (`poll_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='投票选票(每用户每投票唯一)';

CREATE TABLE `community_poll_options` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '选项ID',
  `poll_id` varchar(36) NOT NULL COMMENT '投票ID',
  `content` varchar(200) NOT NULL COMMENT '选项文本',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序(越小越靠前)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_poll_option` (`poll_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='投票选项';

CREATE TABLE `community_poll_votes` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '投票记录ID',
  `poll_id` varchar(36) NOT NULL COMMENT '投票ID',
  `option_id` bigint unsigned NOT NULL COMMENT '选项ID',
  `user_id` bigint unsigned NOT NULL COMMENT '投票用户ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '投票时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_poll_user_option` (`poll_id`,`user_id`,`option_id`),
  KEY `idx_poll_vote_user` (`poll_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='投票记录';

CREATE TABLE `community_polls` (
  `id` varchar(36) NOT NULL COMMENT '投票ID(客户端生成UUID)',
  `target_type` varchar(20) NOT NULL COMMENT '宿主内容类型: POST/WISH/CAPSULE/LETTER',
  `target_id` varchar(64) NOT NULL COMMENT '宿主内容ID',
  `creator_id` bigint unsigned NOT NULL COMMENT '创建者用户ID',
  `question` varchar(200) NOT NULL COMMENT '投票问题',
  `is_multiple` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否多选(1多选/0单选)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_poll_target` (`target_type`,`target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='投票主表(编辑器附件)';

CREATE TABLE `community_survey_answers` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '答案ID',
  `survey_id` varchar(36) NOT NULL COMMENT '问卷ID',
  `question_id` bigint unsigned NOT NULL COMMENT '题目ID',
  `user_id` bigint unsigned NOT NULL COMMENT '答卷用户ID',
  `option_ids` varchar(500) DEFAULT NULL COMMENT '选中选项ID的JSON数组(选择题)',
  `text_content` varchar(500) DEFAULT NULL COMMENT '填空内容(填空题)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次提交时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_survey_question_user` (`survey_id`,`question_id`,`user_id`),
  KEY `idx_survey_answer_user` (`survey_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='问卷答案(可重复提交覆盖)';

CREATE TABLE `community_survey_questions` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '题目ID',
  `survey_id` varchar(36) NOT NULL COMMENT '问卷ID',
  `content` varchar(500) NOT NULL COMMENT '题干',
  `type` enum('SINGLE','MULTI','TEXT') NOT NULL DEFAULT 'SINGLE' COMMENT '题型:单选/多选/填空',
  `options` varchar(2000) NOT NULL DEFAULT '' COMMENT '选项文本JSON数组(填空题为空串)',
  `is_required` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否必答',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序(越小越靠前)',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_survey_question` (`survey_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='问卷题目';

CREATE TABLE `community_surveys` (
  `id` varchar(36) NOT NULL COMMENT '问卷ID(客户端生成UUID)',
  `target_type` varchar(20) NOT NULL COMMENT '宿主内容类型: POST/WISH/CAPSULE/LETTER',
  `target_id` varchar(64) NOT NULL COMMENT '宿主内容ID',
  `creator_id` bigint unsigned NOT NULL COMMENT '创建者用户ID',
  `title` varchar(200) NOT NULL COMMENT '问卷标题',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_survey_target` (`target_type`,`target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='问卷主表(编辑器附件)';

CREATE TABLE `daily_check_ins` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `check_in_date` date NOT NULL COMMENT '签到日期',
  `continuous_days` int unsigned NOT NULL DEFAULT '1' COMMENT '连续签到天数',
  `exp_reward` int unsigned NOT NULL DEFAULT '10' COMMENT '获得经验值',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_date` (`user_id`,`check_in_date`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='每日签到记录';

CREATE TABLE `exp_logs` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `exp_change` int NOT NULL COMMENT '经验值变化(正数增加/负数扣除)',
  `source` varchar(30) NOT NULL COMMENT '来源: CHECK_IN/POST/LIKE_RECEIVED/COMMENT_RECEIVED/FOLLOW_RECEIVED/COLLECT_RECEIVED',
  `biz_id` bigint unsigned DEFAULT NULL COMMENT '关联业务ID',
  `description` varchar(200) DEFAULT NULL COMMENT '描述',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_explog_reward_fact` (`user_id`,`source`,`biz_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_source` (`source`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='经验值变动日志';

CREATE TABLE `hot_searches` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `keyword` varchar(200) NOT NULL COMMENT '搜索关键词',
  `search_count` int unsigned NOT NULL DEFAULT '1' COMMENT '搜索次数',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_keyword` (`keyword`),
  KEY `idx_search_count` (`search_count` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='热搜词表';

CREATE TABLE `level_configs` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `level` int unsigned NOT NULL COMMENT '等级',
  `title` varchar(30) NOT NULL COMMENT '等级称号',
  `min_exp` int unsigned NOT NULL COMMENT '所需最低经验值',
  `icon` varchar(255) DEFAULT NULL COMMENT '等级图标URL',
  `benefits` json DEFAULT NULL COMMENT '等级权益描述',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态: 0-禁用 1-启用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_level` (`level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='等级配置表';

CREATE TABLE `post_collections` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `post_id` bigint unsigned NOT NULL COMMENT '帖子ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_post` (`user_id`,`post_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='帖子收藏表';

CREATE TABLE `post_comments` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `post_id` bigint unsigned NOT NULL COMMENT '帖子ID',
  `user_id` bigint unsigned NOT NULL COMMENT '评论用户ID',
  `parent_id` bigint unsigned DEFAULT NULL COMMENT '父评论ID',
  `reply_to_user_id` bigint unsigned DEFAULT NULL COMMENT '回复目标用户ID',
  `content` varchar(1000) NOT NULL COMMENT '评论内容',
  `like_count` int unsigned NOT NULL DEFAULT '0' COMMENT '点赞数',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态: 0-正常 1-隐藏 2-管理员删除',
  `review_status` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '审核状态: 0-待审核, 1-已通过, 2-已拒绝',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_at` datetime DEFAULT NULL COMMENT '软删除时间',
  PRIMARY KEY (`id`),
  KEY `idx_post_id` (`post_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='帖子评论表';

CREATE TABLE `post_likes` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `target_type` varchar(10) NOT NULL COMMENT '目标类型: POST/COMMENT',
  `target_id` bigint unsigned NOT NULL COMMENT '目标ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_target` (`user_id`,`target_type`,`target_id`),
  KEY `idx_target` (`target_type`,`target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='帖子/评论点赞表';

CREATE TABLE `post_shares` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `post_id` bigint unsigned NOT NULL COMMENT '帖子ID',
  `user_id` bigint unsigned NOT NULL COMMENT '分享用户ID',
  `channel` varchar(20) NOT NULL DEFAULT 'LINK' COMMENT '分享渠道: LINK/WECHAT/WEIBO/QQ/DOUBAN',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_post_id` (`post_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_channel` (`channel`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='帖子分享记录表';

CREATE TABLE `post_tags` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `post_id` bigint unsigned NOT NULL COMMENT '帖子ID',
  `tag_id` bigint unsigned NOT NULL COMMENT '标签ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_tag` (`post_id`,`tag_id`),
  KEY `idx_tag_id` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='帖子-标签关联表';

CREATE TABLE `posts` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint unsigned NOT NULL COMMENT '作者用户ID',
  `title` varchar(200) NOT NULL COMMENT '标题',
  `content` longtext NOT NULL COMMENT '内容(富文本HTML)',
  `cover_image` varchar(500) DEFAULT NULL COMMENT '封面图URL',
  `media_urls` json DEFAULT NULL COMMENT '媒体资源URL列表',
  `media_type` varchar(10) NOT NULL DEFAULT 'IMAGE' COMMENT '媒体类型: IMAGE/VIDEO/MIXED',
  `category_id` bigint unsigned DEFAULT NULL COMMENT '关联分类ID',
  `product_id` bigint unsigned DEFAULT NULL COMMENT '关联商品ID',
  `like_count` int unsigned NOT NULL DEFAULT '0' COMMENT '点赞数',
  `comment_count` int unsigned NOT NULL DEFAULT '0' COMMENT '评论数',
  `collect_count` int unsigned NOT NULL DEFAULT '0' COMMENT '收藏数',
  `share_count` int unsigned NOT NULL DEFAULT '0' COMMENT '分享数',
  `view_count` int unsigned NOT NULL DEFAULT '0' COMMENT '浏览数',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态: 0-草稿 1-已发布 2-隐藏 3-管理员删除',
  `moderation_hidden` tinyint(1) NOT NULL DEFAULT '0' COMMENT '平台处置隐藏标记(C02:置1后作者不可自行恢复发布,申诉/平台恢复发布时清除)',
  `review_status` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '审核状态: 0-待审核, 1-已通过, 2-已拒绝',
  `review_reason` varchar(200) DEFAULT NULL COMMENT '审核拒绝原因',
  `is_top` tinyint NOT NULL DEFAULT '0' COMMENT '是否置顶: 0-否 1-是',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted_at` datetime DEFAULT NULL COMMENT '软删除时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status_created` (`status`,`created_at`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_review_status` (`review_status`),
  KEY `idx_user_status_review_created` (`user_id`,`status`,`review_status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='社区帖子表';

CREATE TABLE `ranking_records` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `season_id` bigint unsigned NOT NULL COMMENT '赛季ID',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `exp_value` int NOT NULL DEFAULT '0' COMMENT '当月获得经验值',
  `rank_no` int NOT NULL COMMENT '排名（从1开始）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_season_user` (`season_id`,`user_id`),
  KEY `idx_season_id` (`season_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='排行榜记录表';

CREATE TABLE `ranking_seasons` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL COMMENT '赛季名称，如：2026年7月经验榜',
  `season_key` char(6) NOT NULL COMMENT '赛季标识，格式 yyyyMM，如 202607',
  `start_date` date NOT NULL COMMENT '赛季开始日期',
  `end_date` date NOT NULL COMMENT '赛季结束日期',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态：0-进行中，1-已归档',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_season_key` (`season_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='排行榜赛季表';

CREATE TABLE `reports` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `reporter_id` bigint unsigned NOT NULL COMMENT '举报人ID',
  `target_type` varchar(10) NOT NULL COMMENT '目标类型: POST/COMMENT/USER',
  `target_id` bigint unsigned NOT NULL COMMENT '目标ID',
  `reason` varchar(50) NOT NULL COMMENT '举报原因',
  `description` varchar(500) DEFAULT NULL COMMENT '详细描述',
  `images` json DEFAULT NULL COMMENT '截图URL列表',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态: 0-待处理 1-处理中 2-驳回 3-已处理',
  `handler_id` bigint unsigned DEFAULT NULL COMMENT '处理人ID',
  `handle_note` varchar(500) DEFAULT NULL COMMENT '处理备注',
  `handled_at` datetime DEFAULT NULL COMMENT '处理时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_target` (`target_type`,`target_id`),
  KEY `idx_reporter_id` (`reporter_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='内容举报表';

CREATE TABLE `search_histories` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `keyword` varchar(200) NOT NULL COMMENT '搜索关键词',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_user_keyword` (`user_id`,`keyword`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='搜索历史表';

CREATE TABLE `sensitive_words` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `word` varchar(100) NOT NULL COMMENT '敏感词',
  `category` varchar(30) NOT NULL DEFAULT 'GENERAL' COMMENT '分类: GENERAL-通用, POLITICAL-政治, PORNOGRAPHIC-色情, ADVERTISING-广告, INSULT-辱骂',
  `level` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '级别: 1-替换, 2-审核, 3-拒绝',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_word` (`word`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='敏感词库';

CREATE TABLE `tag_subscriptions` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `user_id` bigint unsigned NOT NULL COMMENT '订阅用户ID',
  `tag_id` bigint unsigned NOT NULL COMMENT '话题标签ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_tag` (`user_id`,`tag_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_tag_id` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='话题订阅表';

CREATE TABLE `tags` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(50) NOT NULL COMMENT '标签名',
  `icon` varchar(255) DEFAULT NULL COMMENT '图标URL',
  `post_count` int unsigned NOT NULL DEFAULT '0' COMMENT '帖子数',
  `is_hot` tinyint NOT NULL DEFAULT '0' COMMENT '是否热门: 0-否 1-是',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态: 0-禁用 1-启用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='话题标签表';

CREATE TABLE `user_badges` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `badge_id` bigint unsigned NOT NULL COMMENT '徽章ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_badge` (`user_id`,`badge_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户-徽章关联表';

CREATE TABLE `user_blocks` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `user_id` bigint unsigned NOT NULL COMMENT '拉黑者ID',
  `blocked_user_id` bigint unsigned NOT NULL COMMENT '被拉黑者ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_block` (`user_id`,`blocked_user_id`),
  KEY `idx_blocked_user_id` (`blocked_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户拉黑表';

CREATE TABLE `user_follows` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `follower_id` bigint unsigned NOT NULL COMMENT '关注者ID',
  `following_id` bigint unsigned NOT NULL COMMENT '被关注者ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_follower_following` (`follower_id`,`following_id`),
  KEY `idx_following_id` (`following_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户关注关系表';

CREATE TABLE `user_levels` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `level` int unsigned NOT NULL DEFAULT '1' COMMENT '当前等级',
  `exp` int unsigned NOT NULL DEFAULT '0' COMMENT '当前经验值',
  `total_exp` bigint unsigned NOT NULL DEFAULT '0' COMMENT '累计经验值',
  `avatar_frame` varchar(20) NOT NULL DEFAULT 'none' COMMENT '头像框: none/gold/purple/green/pink/rainbow',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户等级表';

CREATE TABLE `user_settings` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `setting_key` varchar(100) NOT NULL COMMENT '设置键',
  `setting_value` varchar(500) NOT NULL DEFAULT '' COMMENT '设置值',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_key` (`user_id`,`setting_key`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户设置表';

-- Required catalog/configuration: level_configs
INSERT INTO `level_configs` (`id`, `level`, `title`, `min_exp`, `icon`, `benefits`, `status`, `created_at`, `updated_at`) VALUES (1, 1, '小答新手', 0, '🌱', '["基础功能"]', 1, '2026-10-14 10:25:40', '2026-10-14 10:25:40');
INSERT INTO `level_configs` (`id`, `level`, `title`, `min_exp`, `icon`, `benefits`, `status`, `created_at`, `updated_at`) VALUES (2, 2, '小答达人', 100, '🌿', '["基础功能", "自定义头像框"]', 1, '2026-10-14 10:25:40', '2026-10-14 10:25:40');
INSERT INTO `level_configs` (`id`, `level`, `title`, `min_exp`, `icon`, `benefits`, `status`, `created_at`, `updated_at`) VALUES (3, 3, '小答高手', 500, '🌳', '["基础功能", "自定义头像框", "专属标签"]', 1, '2026-10-14 10:25:40', '2026-10-14 10:25:40');
INSERT INTO `level_configs` (`id`, `level`, `title`, `min_exp`, `icon`, `benefits`, `status`, `created_at`, `updated_at`) VALUES (4, 4, '小答专家', 2000, '⭐', '["基础功能", "自定义头像框", "专属标签", "优先推荐"]', 1, '2026-10-14 10:25:40', '2026-10-14 10:25:40');
INSERT INTO `level_configs` (`id`, `level`, `title`, `min_exp`, `icon`, `benefits`, `status`, `created_at`, `updated_at`) VALUES (5, 5, '小答大师', 5000, '🌟', '["基础功能", "自定义头像框", "专属标签", "优先推荐", "官方活动优先"]', 1, '2026-10-14 10:25:40', '2026-10-14 10:25:40');
INSERT INTO `level_configs` (`id`, `level`, `title`, `min_exp`, `icon`, `benefits`, `status`, `created_at`, `updated_at`) VALUES (6, 6, '小答传奇', 15000, '👑', '["全部功能", "专属标识", "官方认证", "活动特权"]', 1, '2026-10-14 10:25:40', '2026-10-14 10:25:40');

-- Required catalog/configuration: sensitive_words
INSERT INTO `sensitive_words` (`id`, `word`, `category`, `level`, `created_at`) VALUES (1, '赌博', 'ADVERTISING', 3, '2026-10-14 10:25:40');
INSERT INTO `sensitive_words` (`id`, `word`, `category`, `level`, `created_at`) VALUES (2, '代开发票', 'ADVERTISING', 3, '2026-10-14 10:25:40');
INSERT INTO `sensitive_words` (`id`, `word`, `category`, `level`, `created_at`) VALUES (3, '刷单', 'ADVERTISING', 3, '2026-10-14 10:25:40');
INSERT INTO `sensitive_words` (`id`, `word`, `category`, `level`, `created_at`) VALUES (4, '色情', 'PORNOGRAPHIC', 3, '2026-10-14 10:25:40');
INSERT INTO `sensitive_words` (`id`, `word`, `category`, `level`, `created_at`) VALUES (5, '暴力', 'INSULT', 2, '2026-10-14 10:25:40');
INSERT INTO `sensitive_words` (`id`, `word`, `category`, `level`, `created_at`) VALUES (6, '傻逼', 'INSULT', 2, '2026-10-14 10:25:40');
INSERT INTO `sensitive_words` (`id`, `word`, `category`, `level`, `created_at`) VALUES (7, '操你', 'INSULT', 3, '2026-10-14 10:25:40');
