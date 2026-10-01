-- 主人称呼设置（需求：用户自定义宠物怎么称呼自己）：
-- 每宠物一份（不同宠物可以叫法不同），默认「主人」。
-- 应用点：AI 聊天 prompt/人设卡、宠物主动提醒文案（publish 漏斗统一替换）。

ALTER TABLE `pet`
    ADD COLUMN `owner_title` VARCHAR(16) NOT NULL DEFAULT '主人'
        COMMENT '主人称呼(宠物对主人的叫法,1~12字,默认主人)' AFTER `personality`;
