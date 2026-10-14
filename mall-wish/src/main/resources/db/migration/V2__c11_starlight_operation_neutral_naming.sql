-- C11：星光交易幂等记录域内中立命名。
-- 背景：该表最初服务 mall-pet 宠物星光桥接（wish_pet_operation），C26 基线落地为
-- wish_starlight_operation；宠物币改为 mall-pet 本地账本后（C21 旧退款分支同步删除），
-- 本表仅承载 mall-wish 域内跨服务激励（评价/邀请），故收敛为中性名 starlight_operation。
-- 不可变的 V1 基线保持原样，所有结构演进在本版本化迁移中完成（禁止改历史迁移）。

-- 1. 删除随 C21 旧退款分支退役的死列（索引随列自动删除，先显式删索引避免复制层开销）
ALTER TABLE `wish_starlight_operation`
    DROP INDEX `idx_wish_starlight_operation_refund_of`,
    DROP COLUMN `refund_of_operation_id`;

-- 2. 索引中性改名
ALTER TABLE `wish_starlight_operation`
    RENAME INDEX `uk_wish_starlight_operation` TO `uk_starlight_operation`,
    RENAME INDEX `idx_wish_starlight_operation_user` TO `idx_starlight_operation_user`;

-- 3. 表中性改名并同步注释（ref_id 语义：评价/邀请等跨服务激励）
RENAME TABLE `wish_starlight_operation` TO `starlight_operation`;

ALTER TABLE `starlight_operation`
    MODIFY COLUMN `ref_id` bigint unsigned DEFAULT NULL COMMENT '关联业务ID(评价/邀请等跨服务激励,审计用)',
    COMMENT = '星光交易幂等记录(仅成功结果,重复请求返回原结果;C11 域内中立命名)';
