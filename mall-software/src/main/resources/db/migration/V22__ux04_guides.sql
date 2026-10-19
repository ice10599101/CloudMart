-- UX04-A：教程/选型攻略——软件域只存 guide 元数据与 articleId；
-- 正文与审核真源在 mall-community（复用 Post：content/审核/评论），不双份同步。
-- 软件域查询时实时校验文章公开状态，文章撤回立即从软件入口隐藏。

CREATE TABLE `software_guide` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `guide_type` varchar(20) NOT NULL COMMENT '类型:INSTALL_GUIDE(安装入门)/WORKFLOW(场景工作流)/COMPARISON(选型对比)/TROUBLESHOOTING(常见问题)',
  `title` varchar(120) NOT NULL COMMENT '教程标题',
  `summary` varchar(200) DEFAULT NULL COMMENT '摘要（列表卡片用；正文在社区）',
  `article_id` bigint unsigned NOT NULL COMMENT '社区帖子 ID（正文与审核真源）',
  `cover_asset_id` bigint unsigned DEFAULT NULL COMMENT '封面素材（可空；空则用社区帖封面）',
  `related_software_ids` json DEFAULT NULL COMMENT '关联软件 ID JSON 数组（≤3；至少关联一个软件或专题）',
  `related_topic_id` bigint unsigned DEFAULT NULL COMMENT '关联专题（可选）',
  `platform_scope` varchar(200) DEFAULT NULL COMMENT '适用平台 CSV（WINDOWS/MACOS/LINUX/WEB；NULL=不限）',
  `applies_to_version` varchar(60) DEFAULT NULL COMMENT '适用业务版本范围说明',
  `checked_at` date DEFAULT NULL COMMENT '内容检查日期',
  `check_note` varchar(200) DEFAULT NULL COMMENT '检查说明（无真实材料不启用教程区块）',
  `enabled` tinyint(1) NOT NULL DEFAULT 0 COMMENT '软件域开关（公开可见还需社区文章公开）',
  `display_order` int unsigned NOT NULL DEFAULT 0 COMMENT '展示顺序',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted_at` datetime(3) DEFAULT NULL COMMENT '删除时间（软删除）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_guide_article` (`article_id`),
  KEY `idx_guide_type` (`guide_type`,`enabled`,`display_order`),
  CONSTRAINT `chk_guide_type` CHECK (`guide_type` IN ('INSTALL_GUIDE','WORKFLOW','COMPARISON','TROUBLESHOOTING'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='软件教程元数据（UX04：正文与审核真源在社区，复用不复制）';
