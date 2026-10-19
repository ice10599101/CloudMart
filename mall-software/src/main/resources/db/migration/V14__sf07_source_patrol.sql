-- =============================================================================
-- V14__sf07_source_patrol.sql — SF07 来源健康巡检
-- 巡检记录（留历史；同指纹最新一条为准）：仅自动检查明确允许的 HTTPS 来源
-- （HTTPS_DOWNLOAD/OFFICIAL_PAGE/WEB_APP）；磁力/种子/需登录网盘不伪装成已检查。
-- 巡检结论写入既有 software_source_state（不覆盖人工 INVALID/DISABLED）。
-- =============================================================================

CREATE TABLE `software_source_patrol` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `software_id` bigint unsigned NOT NULL COMMENT '软件ID',
  `source_key` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '稳定来源键',
  `source_fingerprint` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '检查时来源内容指纹（与当前不一致则结论不适用）',
  `source_type` varchar(24) NOT NULL COMMENT '来源类型（仅 HTTPS 类会被自动检查）',
  `url_host` varchar(200) NOT NULL COMMENT '检查目标主机（审计可见，不含完整 URL）',
  `outcome` varchar(16) NOT NULL COMMENT '结论:AVAILABLE/INVALID/UNKNOWN',
  `reason_code` varchar(32) DEFAULT NULL COMMENT '结论原因:DNS_FAILED/CONN_REFUSED/TIMEOUT/HTTP_ERROR/RATE_LIMITED/OK 等',
  `http_status` int unsigned DEFAULT NULL COMMENT 'HTTP 状态码（无 HTTP 响应为 NULL）',
  `redirect_count` int unsigned NOT NULL DEFAULT 0 COMMENT '实际跟随的跳转次数（≤3）',
  `elapsed_ms` int unsigned DEFAULT NULL COMMENT '总耗时（毫秒）',
  `checked_at` datetime(3) NOT NULL COMMENT '检查时间',
  `next_check_at` datetime(3) NOT NULL COMMENT '下次计划检查时间（按结论退避）',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_patrol_source` (`software_id`,`source_key`,`checked_at`),
  KEY `idx_patrol_next` (`next_check_at`,`id`),
  CONSTRAINT `chk_patrol_outcome` CHECK (`outcome` IN ('AVAILABLE','INVALID','UNKNOWN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='来源健康巡检记录（SF07；不覆盖人工状态，作者与管理员可查失败原因）';
