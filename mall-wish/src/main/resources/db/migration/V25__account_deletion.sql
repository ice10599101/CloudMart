-- V7: 账号注销宽限期（合规 34.2 / API 2.13，文档表⑲）
CREATE TABLE IF NOT EXISTS wish_account_deletion (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '用户 ID',
  status ENUM('PENDING','CANCELED','EXECUTED') NOT NULL DEFAULT 'PENDING' COMMENT '状态：宽限期/已撤回/已执行',
  reason VARCHAR(500) NULL COMMENT '注销原因（可选）',
  requested_at DATETIME NOT NULL COMMENT '申请时间',
  execute_after DATETIME NOT NULL COMMENT '实际执行时间（requested_at + 30 天宽限期）',
  canceled_at DATETIME NULL COMMENT '撤回时间',
  executed_at DATETIME NULL COMMENT '实际完成注销时间',
  code_hash VARCHAR(64) NULL COMMENT '二次确认验证码 SHA-256 哈希（验证用，验证码本体仅存 Redis）',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_deletion_user (user_id),
  KEY idx_deletion_execute (execute_after, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='账号注销宽限期（Sprint 合规 34.2）';
