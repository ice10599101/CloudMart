-- V26: B02/N03 合作修复（BE-01/BE-02）+ PERF-01 榜单索引 + ADM-02 版本唯一约束
-- 1) 合作状态机补齐 REJECTED/EXPIRED（追加枚举尾部，MySQL ENUM 保持既有取值索引不变）；
-- 2) 胜场榜 (status, winner_pet_id) 复合索引（PERF-01：数据量增长后避免全表扫描）；
-- 3) pet_config_version 版本唯一约束（ADM-02：max+1 竞态由应用层重试 + 数据库唯一键共同保证；
--    远程环境已核验无重复版本数据，可直接加约束）；
-- 4) DB-01 差异登记：V8 的 pet_migration_conflict INSERT 未填主键 id——远程（2026-09-26）与
--    全新环境均在空冲突数据下成功执行，仅"存量冲突数据预检升级"路径会触发；按 §13.3 不修改
--    已执行迁移，升级前由 scripts/pet-wallet/preflight.sql 的冲突预检拦截。

ALTER TABLE `pet_cooperation`
    MODIFY COLUMN `status` ENUM('INVITED','ACTIVE','COMPLETED','ENDED','REJECTED','EXPIRED')
        NOT NULL DEFAULT 'INVITED'
        COMMENT '状态机:INVITED待接受/ACTIVE进行中/COMPLETED已达成/ENDED已退出/REJECTED已拒绝/EXPIRED已过期';

ALTER TABLE `pet_battle`
    ADD INDEX `idx_pet_battle_status_winner` (`status`, `winner_pet_id`);

ALTER TABLE `pet_config_version`
    ADD UNIQUE KEY `uk_pet_config_version` (`config_type`, `config_id`, `version`);
