-- V26: 品牌奖励发放日志（文档表㉕，Sprint 3.6 达标发奖链路）
CREATE TABLE IF NOT EXISTS wish_brand_reward_log (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  pool_id BIGINT UNSIGNED NOT NULL COMMENT '许愿池 ID',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '获得奖励的用户 ID',
  reward_type VARCHAR(20) NOT NULL DEFAULT 'STARLIGHT' COMMENT '奖励类型',
  reward_amount INT NOT NULL DEFAULT 0 COMMENT '奖励数量',
  coupon_code VARCHAR(64) NULL COMMENT '券码（如适用）',
  granted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发放时间',
  PRIMARY KEY (id),
  KEY idx_reward_user (user_id, granted_at),
  KEY idx_reward_pool (pool_id, granted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='品牌奖励发放日志（合规 34.4 数据共享边界）';
