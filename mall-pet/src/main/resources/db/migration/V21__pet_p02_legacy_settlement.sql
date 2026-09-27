-- V21: P02/TX-01..05 旧结算收敛（mall-pet 侧）
-- 1) operation_id 扩为 160：容纳 "业务键:客户端幂等键" 组合与最长雪花 ID（TX-05，两端同宽，
--    wish 侧 V48 同步），禁止截断碰撞——恢复任务按原单全键收敛。
-- 2) status 枚举追加 PROCESSING（恢复器处理租约）与 MANUAL_REVIEW（本地事实无法自动判定的
--    受控终态）：追加在末尾，MySQL ENUM 保持既有取值的索引映射不变。
-- 3) wallet_domain：钱包域切换基线（TX-04/§6.2）——存量与旧链路一律 LEGACY_WISH，
--    后续独立钱包（PET）上线后旧恢复器只按 LEGACY_WISH 路由，不以全局开关猜币种。
-- 4) recover_from_status：恢复租约前的原始状态——租约接管后 EARN 收敛语义依赖
--    "PENDING（本地事务未提交）/UNKNOWN（本地奖励已提交）" 的区分（TX-03）。
-- 5) pet_minigame_round.reward_operation_id 同步扩为 160（同一键空间）。

ALTER TABLE `pet_operation`
    MODIFY COLUMN `operation_id` VARCHAR(160) NOT NULL
        COMMENT '业务操作唯一键(EARN业务事实键/SPEND请求意图键,确定性生成,重试复用,与wish_pet_operation同宽)',
    MODIFY COLUMN `status` ENUM('PENDING','COMPLETED','FAILED','UNKNOWN','COMPENSATING','COMPENSATED','PROCESSING','MANUAL_REVIEW')
        NOT NULL DEFAULT 'PENDING'
        COMMENT '状态机:PENDING待远程/PROCESSING恢复租约/COMPLETED完成/FAILED明确拒绝/UNKNOWN远程未知/COMPENSATING退款中/COMPENSATED已退款/MANUAL_REVIEW人工核查',
    ADD COLUMN `wallet_domain` VARCHAR(16) NOT NULL DEFAULT 'LEGACY_WISH'
        COMMENT '钱包域(LEGACY_WISH=社区星光/PET=宠物币,切换后旧恢复器只路由LEGACY_WISH) AFTER status',
    ADD COLUMN `recover_from_status` VARCHAR(16) DEFAULT NULL
        COMMENT '恢复租约前原始状态(租约接管后区分EARN的PENDING/UNKNOWN收敛语义) AFTER wallet_domain';

ALTER TABLE `pet_minigame_round`
    MODIFY COLUMN `reward_operation_id` VARCHAR(160) DEFAULT NULL COMMENT '奖励操作键(同一局只发一次,与pet_operation同宽)';
