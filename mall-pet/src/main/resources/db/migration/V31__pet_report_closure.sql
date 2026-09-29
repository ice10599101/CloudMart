-- P0-1/P0-2 举报闭环：
-- 1) target_type 扩展 CHAT_MESSAGE —— 聊天危机词自动举报落库需要
-- 2) 处理动作/处理说明/自动标记 —— 管理端处理闭环（reason + action + 通知举报人）

ALTER TABLE `pet_report`
    MODIFY COLUMN `target_type`
        ENUM('WALL_MESSAGE','BOTTLE_CONTENT','NICKNAME','CHAT_MESSAGE') NOT NULL
        COMMENT '举报对象类型';

ALTER TABLE `pet_report`
    ADD COLUMN `handle_action` VARCHAR(32) DEFAULT NULL
        COMMENT '处理动作：CONTENT_REMOVED/USER_WARNED/USER_PET_BANNED/DISMISSED' AFTER `handled_at`,
    ADD COLUMN `handle_reason` VARCHAR(200) DEFAULT NULL
        COMMENT '处理说明（通知举报人的依据）' AFTER `handle_action`,
    ADD COLUMN `is_auto` TINYINT NOT NULL DEFAULT 0
        COMMENT '是否系统自动举报：1自动(如危机词命中) 0用户提交' AFTER `handle_reason`;
