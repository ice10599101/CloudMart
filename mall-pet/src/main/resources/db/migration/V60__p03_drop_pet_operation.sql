-- P03 二阶段：LEGACY 币域（社区星光 + pet_operation 幂等/恢复）物理删除收尾。
-- 前置核实（2026-10-03）：pet_operation 表 0 行（LEGACY 通道从未产生流量），
-- PET_COIN 独立钱包（pet_wallet_account/transaction）为唯一活账本。
-- 代码面：PetOperationService/Store/Recovery/Recoverable/WishFeignClient starlight
-- 方法已随本次提交删除；表按 AGENTS 数据删除策略以 DROP 收尾（0 行无可归档）。
DROP TABLE IF EXISTS `pet_operation`;
