-- =============================================
-- V77: R03/R04 协作 PVE 状态机与奖励改造（与 V73 同域收敛）
--   1) pet_pve_run 扩展：独立回合号/行动方/版本 CAS/回合截止/总时限/规则版本/种子
--      /奖励状态——日志条数不再决定轮次；回合流水迁独立表（100 回合不超长）。
--   2) pet_pve_participant：用户活动占用行（uk user_id，start/join 共用，
--      终态释放）——数据库唯一约束保证一个用户一个活动副本，切宠不可绕过。
--   3) pet_pve_round_log：回合明细独立表 (run_id, turn_no, seq) 唯一；
--      攻击请求键 (run_id, request_key) 唯一——重复攻击重放原回合结果。
--   4) 奖励事实复用 pet_reward_claim（uk user+bizType+bizId+slot，bizType=PVE），
--      不新建平行台账；run.reward_status 仅作聚合展示（PENDING/SETTLED）。
--   开发期无历史数据：rounds JSON 列与 reward_granted 布尔随收敛删除。
-- =============================================
ALTER TABLE pet_pve_run
    ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本（CAS 推进）' AFTER status,
    ADD COLUMN turn_no INT NOT NULL DEFAULT 0 COMMENT '已完成回合数（下一回合=turn_no+1）' AFTER version,
    ADD COLUMN next_actor_user_id BIGINT UNSIGNED NULL COMMENT '当前行动方用户 ID（第 1 回合=发起人）' AFTER turn_no,
    ADD COLUMN turn_deadline_at DATETIME NULL COMMENT '本回合行动截止（超时托管/失败收口）' AFTER next_actor_user_id,
    ADD COLUMN run_expires_at DATETIME NULL COMMENT '副本总时限（FIGHTING 超时批量收口）' AFTER turn_deadline_at,
    ADD COLUMN rule_version VARCHAR(16) NOT NULL DEFAULT 'V1' COMMENT '战斗规则版本（伤害口径/种子回放）' AFTER run_expires_at,
    ADD COLUMN seed BIGINT NOT NULL DEFAULT 0 COMMENT '固定种子（开局快照+回合号决定性重放）' AFTER rule_version,
    ADD COLUMN reward_status VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT 'NONE/PENDING/SETTLED（聚合展示，权威在 pet_reward_claim）' AFTER seed,
    ADD COLUMN initiator_request_key VARCHAR(64) NULL COMMENT '发起请求键（重放幂等）' AFTER reward_status,
    ADD UNIQUE INDEX uk_initiator_request (initiator_request_key),
    ADD INDEX idx_status_expire (status, run_expires_at);

ALTER TABLE pet_pve_run
    DROP COLUMN rounds,
    DROP COLUMN reward_granted;

CREATE TABLE IF NOT EXISTS pet_pve_participant (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '用户 ID（一个用户同时仅一个活动副本）',
    run_id BIGINT UNSIGNED NOT NULL COMMENT '副本 ID',
    pet_id BIGINT UNSIGNED NOT NULL COMMENT '参战宠物 ID（占用时刻冻结，切宠不迁移）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '占用时间',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_user_active (user_id),
    INDEX idx_run (run_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'PVE 参与占用（R03：发起/加入共用）';

CREATE TABLE IF NOT EXISTS pet_pve_round_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    run_id BIGINT UNSIGNED NOT NULL COMMENT '副本 ID',
    turn_no INT NOT NULL COMMENT '回合号（1 起）',
    seq INT NOT NULL COMMENT '回合内动作序号（1=宠物攻击，2=Boss 反击）',
    actor_user_id BIGINT UNSIGNED NULL COMMENT '动作方用户 ID（Boss 行为为 NULL）',
    actor_pet_id BIGINT UNSIGNED NULL COMMENT '动作方宠物 ID',
    actor_name VARCHAR(64) NOT NULL COMMENT '动作方名称',
    action VARCHAR(16) NOT NULL COMMENT 'ATTACK/COUNTER',
    damage INT NOT NULL DEFAULT 0 COMMENT '伤害（0=被闪避）',
    critical TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否暴击',
    dodged TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否被闪避',
    target_name VARCHAR(64) NOT NULL COMMENT '受击方名称',
    target_hp_after INT NOT NULL DEFAULT 0 COMMENT '受击方剩余 HP',
    request_key VARCHAR(64) NULL COMMENT '攻击请求键（仅 seq=1 行携带，重放判重）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_run_turn_seq (run_id, turn_no, seq),
    UNIQUE INDEX uk_run_request (run_id, request_key),
    INDEX idx_run (run_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'PVE 回合明细（R03：独立追加，替代 VARCHAR(8000) JSON）';
