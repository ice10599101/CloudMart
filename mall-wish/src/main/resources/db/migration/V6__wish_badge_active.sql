-- ---------------------------------------------
-- V6 徽章上下架（Sprint 1.8 管理端徽章 CRUD，文档 33.4.7：
-- 徽章管理新增/编辑/上下架 + condition JSON 编辑校验）
-- is_active=0 下架徽章：不参与授予判定/不出现在徽章墙与图鉴
-- （已获得记录保留，重新上架自动恢复展示）
-- ---------------------------------------------
ALTER TABLE `wish_badge`
    ADD COLUMN `is_active` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '上架状态(1=上架,0=下架不判定不展示)' AFTER `rarity`;
