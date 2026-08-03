CREATE TABLE blacklist_entries (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    target_type VARCHAR(20) NOT NULL COMMENT '目标类型: USER/IP/DEVICE',
    target_value VARCHAR(100) NOT NULL COMMENT '目标值: 用户ID/IP地址/设备ID',
    reason VARCHAR(500) DEFAULT NULL COMMENT '加入黑名单原因',
    expired_at DATETIME DEFAULT NULL COMMENT '过期时间，NULL表示永久',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY pk_blacklist_entries_id (id),
    INDEX idx_blacklist_target (target_type, target_value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='黑名单表';
