-- V29: 打卡心情字段（文档 2.3，Sprint 1.3 mood 契约）
ALTER TABLE wish_checkin ADD COLUMN mood VARCHAR(20) NULL COMMENT '打卡心情（HAPPY/CALM/EXCITED/TIRED）' AFTER content;
