-- V8: 头像框（用户装扮）——存储于 user_levels，供全站头像处跨用户展示

ALTER TABLE `user_levels`
    ADD COLUMN `avatar_frame` VARCHAR(20) NOT NULL DEFAULT 'none'
    COMMENT '头像框: none/gold/purple/green/pink/rainbow' AFTER `total_exp`;