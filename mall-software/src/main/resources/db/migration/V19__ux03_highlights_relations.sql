-- UX03：软件详情重组——亮点与同类关系。
-- 1) highlights：随快照审核的短亮点（JSON 数组，≤3 条、每条 ≤60 字，应用层校验）。
-- 2) software_relation：同类关系（同类用途/同平台替代），人工关系不上黑盒推荐；
--    禁止自关联与重复，公开聚合时实时过滤不可见目标。

ALTER TABLE `software_revision`
    ADD COLUMN `highlights` json DEFAULT NULL
        COMMENT '短亮点 JSON 数组（≤3 条、每条 ≤60 字，应用层校验；随快照审核）' AFTER `system_requirements`;

CREATE TABLE `software_relation` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `software_id` bigint unsigned NOT NULL COMMENT '软件ID',
  `related_id` bigint unsigned NOT NULL COMMENT '关联软件ID（不可见目标在公开聚合时实时过滤）',
  `relation_type` varchar(20) NOT NULL COMMENT '关系类型:SIMILAR_USE(同类用途)/ALTERNATIVE_PLATFORM(同平台替代)',
  `reason` varchar(200) DEFAULT NULL COMMENT '编辑解释（如"同类用途""同平台替代"，不宣称优劣）',
  `display_order` int unsigned NOT NULL DEFAULT 0 COMMENT '展示顺序',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_relation` (`software_id`,`related_id`,`relation_type`),
  KEY `idx_relation_related` (`related_id`),
  CONSTRAINT `chk_relation_type` CHECK (`relation_type` IN ('SIMILAR_USE','ALTERNATIVE_PLATFORM'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='软件同类关系（UX03：人工关系，禁止自关联，公开聚合实时过滤不可见目标）';
