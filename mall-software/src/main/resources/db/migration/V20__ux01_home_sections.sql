-- UX01：首页内容运营配置——固定区块 + 区块条目。
-- kind/data_source/target_type 白名单（CHECK + 应用层双重校验），不接受 SQL/脚本/外部接口地址。
-- 保存/启用走服务端事务+状态检查+幂等键，不引入配置 expectedVersion 或发布版本号。

CREATE TABLE `software_home_section` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `slot_key` varchar(40) NOT NULL COMMENT '稳定槽位键（前端降级定位用，唯一）',
  `title` varchar(60) NOT NULL COMMENT '区块标题',
  `subtitle` varchar(120) DEFAULT NULL COMMENT '副标题',
  `kind` varchar(20) NOT NULL COMMENT '区块类型:SPOTLIGHT/SCENARIOS/SOFTWARE_GRID/RANKING_LIST/UPDATE_FEED/GUIDE_LIST',
  `enabled` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否启用（0/1）',
  `display_order` int unsigned NOT NULL DEFAULT 0 COMMENT '展示顺序（reorder 原子调整）',
  `start_at` datetime(3) DEFAULT NULL COMMENT '投放开始（NULL=不限）',
  `end_at` datetime(3) DEFAULT NULL COMMENT '投放结束（NULL=不限）',
  `platform_scope` varchar(200) DEFAULT NULL COMMENT '平台范围 CSV（WEB/APP/TARO；NULL=全部端）',
  `limit_count` int unsigned NOT NULL DEFAULT 8 COMMENT '条目上限（1~20，应用层校验）',
  `min_items` int unsigned NOT NULL DEFAULT 0 COMMENT '最少条目（不足整块隐藏，0=不启用该规则）',
  `data_source` varchar(20) NOT NULL COMMENT '数据来源:MANUAL/FEATURED/LATEST/RANKING/TOPIC/GUIDE',
  `filter_json` json DEFAULT NULL COMMENT '受控筛选 preset（仅 Explore 白名单参数）',
  `fallback_policy` varchar(20) NOT NULL DEFAULT 'HIDE' COMMENT '内容不足策略:HIDE(隐藏)/EMPTY(显式空态)',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted_at` datetime(3) DEFAULT NULL COMMENT '删除时间（软删除）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_home_section_slot` (`slot_key`),
  KEY `idx_home_section_enabled` (`enabled`,`display_order`),
  CONSTRAINT `chk_home_section_kind` CHECK (`kind` IN ('SPOTLIGHT','SCENARIOS','SOFTWARE_GRID','RANKING_LIST','UPDATE_FEED','GUIDE_LIST')),
  CONSTRAINT `chk_home_section_source` CHECK (`data_source` IN ('MANUAL','FEATURED','LATEST','RANKING','TOPIC','GUIDE')),
  CONSTRAINT `chk_home_section_fallback` CHECK (`fallback_policy` IN ('HIDE','EMPTY'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='首页固定区块（UX01：运营配置，多端同一内容规则）';

CREATE TABLE `software_home_section_item` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `section_id` bigint unsigned NOT NULL COMMENT '所属区块',
  `target_type` varchar(20) NOT NULL COMMENT '目标类型:SOFTWARE/TOPIC/GUIDE/FILTER',
  `target_id` bigint unsigned DEFAULT NULL COMMENT '目标 ID（SOFTWARE/TOPIC/GUIDE 必填；FILTER 为空）',
  `filter_preset` varchar(200) DEFAULT NULL COMMENT '受控筛选 preset（FILTER 类型用；须符合 Explore 白名单参数）',
  `headline` varchar(60) DEFAULT NULL COMMENT '运营标题（与作者原始内容分开）',
  `editor_note` varchar(200) DEFAULT NULL COMMENT '编辑推荐理由（运营文案，不借改推荐语修改作者快照）',
  `cover_asset_id` bigint unsigned DEFAULT NULL COMMENT '封面素材（复用 mall-file 绑定）',
  `display_order` int unsigned NOT NULL DEFAULT 0 COMMENT '展示顺序',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_home_item_section` (`section_id`,`display_order`),
  CONSTRAINT `chk_home_item_target` CHECK (`target_type` IN ('SOFTWARE','TOPIC','GUIDE','FILTER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='首页区块条目（UX01：运营推荐，与作者内容分离）';
