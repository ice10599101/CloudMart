-- V26: 心愿管理对齐帖子管理模式（2026-09-07 用户需求）
-- 管理端支持置顶：is_top=1 的心愿在用户端广场/列表中优先展示
ALTER TABLE wish
    ADD COLUMN is_top TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否管理端置顶(置顶心愿在广场优先展示)' AFTER is_visible;
