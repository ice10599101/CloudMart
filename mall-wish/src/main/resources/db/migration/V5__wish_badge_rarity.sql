-- =============================================
-- V5: 徽章稀有度列（文档 2.9 GET /wish/badges/definitions 契约要求）
-- =============================================
-- rarity 用于前端徽章墙视觉分层（普通/稀有/史诗/传说）与图鉴展示；
-- 历史迁移不可修改，故 V1 种子的 rarity 补齐在本迁移内 UPDATE。

ALTER TABLE `wish_badge`
    ADD COLUMN `rarity` VARCHAR(16) NOT NULL DEFAULT 'COMMON'
        COMMENT '稀有度: COMMON普通/RARE稀有/EPIC史诗/LEGENDARY传说'
        AFTER `icon`;

-- V1 种子徽章稀有度补齐（HELP_100/PERSIST_365 为长线成就，定高稀有度）
UPDATE `wish_badge` SET `rarity` = 'COMMON'    WHERE `code` = 'FIRST_WISH';
UPDATE `wish_badge` SET `rarity` = 'COMMON'    WHERE `code` = 'FIRST_FULFILL';
UPDATE `wish_badge` SET `rarity` = 'EPIC'      WHERE `code` = 'HELP_100';
UPDATE `wish_badge` SET `rarity` = 'LEGENDARY' WHERE `code` = 'PERSIST_365';
