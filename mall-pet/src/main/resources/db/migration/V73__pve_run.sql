-- =============================================
-- V73: 协作 PVE 副本（§6 协作 PVE）：2 人协战 Boss
--   发起（OPEN）→ 队友加入（FIGHTING）→ 每次攻击推进一回合
--   （双宠轮流对 Boss + Boss 反击，快照存 run 行）；Boss HP≤0 → WON 一次性发奖。
--   奖励走 PetQuotaService 每日 PVE 配额（有收益/无收益两档）+ economyService.earn 星光。
--   过期：OPEN 超 24h 由查询懒清理（EXPIRED）。
-- =============================================
CREATE TABLE IF NOT EXISTS pet_pve_run (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    boss_code VARCHAR(32) NOT NULL COMMENT 'Boss 编码（forest_ogre/sand_worm/void_knight）',
    boss_name VARCHAR(64) NOT NULL COMMENT 'Boss 名称',
    boss_max_hp INT NOT NULL COMMENT 'Boss 最大 HP',
    boss_hp INT NOT NULL COMMENT 'Boss 当前 HP',
    initiator_user_id BIGINT UNSIGNED NOT NULL COMMENT '发起人用户 ID',
    initiator_pet_id BIGINT UNSIGNED NOT NULL COMMENT '发起人宠物 ID',
    initiator_snapshot VARCHAR(600) NOT NULL COMMENT '发起人宠物战斗快照 JSON（Fighter）',
    partner_user_id BIGINT UNSIGNED NULL COMMENT '队友用户 ID（加入后回填）',
    partner_pet_id BIGINT UNSIGNED NULL COMMENT '队友宠物 ID',
    partner_snapshot VARCHAR(600) NULL COMMENT '队友宠物战斗快照 JSON',
    initiator_pet_hp INT NOT NULL DEFAULT 0 COMMENT '发起人宠物当前 HP',
    partner_pet_hp INT NULL COMMENT '队友宠物当前 HP',
    rounds VARCHAR(8000) NOT NULL DEFAULT '[]' COMMENT '回合流水 JSON',
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/FIGHTING/WON/FAILED/EXPIRED',
    reward_granted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '奖励是否已发（WON 一次性）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    finished_at DATETIME NULL COMMENT '结束时间',
    PRIMARY KEY (id),
    INDEX idx_status_boss (status, boss_code),
    INDEX idx_initiator (initiator_user_id, created_at),
    INDEX idx_partner (partner_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '协作 PVE 副本（§6）';
