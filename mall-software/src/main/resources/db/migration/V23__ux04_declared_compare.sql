-- UX04-B：软件对比——结构化可审核的 declaredCapabilities。
-- 运营在 Items 维护；未提供显示"未提供"，禁止推断"支持/不支持"；
-- 只补列表页没有的结构化声明字段（是否开源/离线可用），其余字段复用现有数据。

CREATE TABLE `software_declared` (
  `software_id` bigint unsigned NOT NULL COMMENT '软件ID（一对一）',
  `open_source` tinyint(1) DEFAULT NULL COMMENT '是否开源（NULL=未提供；不推断）',
  `offline_capable` tinyint(1) DEFAULT NULL COMMENT '离线可用（NULL=未提供；不推断）',
  `declared_note` varchar(200) DEFAULT NULL COMMENT '声明说明（依据来源；不伪造测试结论）',
  `updated_by` bigint unsigned NOT NULL COMMENT '最后维护人（管理员 ID）',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`software_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='软件结构化声明（UX04-B：对比用客观字段，编辑推荐与客观字段分离）';
