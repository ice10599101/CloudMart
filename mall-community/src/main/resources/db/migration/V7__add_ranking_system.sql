-- 排行榜赛季表：每月一个赛季，记录赛季信息
CREATE TABLE ranking_seasons (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100)   NOT NULL COMMENT '赛季名称，如：2026年7月经验榜',
    season_key  CHAR(6)        NOT NULL COMMENT '赛季标识，格式 yyyyMM，如 202607',
    start_date  DATE           NOT NULL COMMENT '赛季开始日期',
    end_date    DATE           NOT NULL COMMENT '赛季结束日期',
    status      TINYINT        NOT NULL DEFAULT 0 COMMENT '状态：0-进行中，1-已归档',
    created_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_season_key (season_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='排行榜赛季表';

-- 排行榜记录表：持久化历史榜单数据
CREATE TABLE ranking_records (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    season_id   BIGINT UNSIGNED NOT NULL COMMENT '赛季ID',
    user_id     BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    exp_value   INT             NOT NULL DEFAULT 0 COMMENT '当月获得经验值',
    rank_no     INT             NOT NULL COMMENT '排名（从1开始）',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_season_id (season_id),
    INDEX idx_user_id (user_id),
    UNIQUE KEY uk_season_user (season_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='排行榜记录表';
