-- V45 (R04): 相册真实 fileId 授权——绑定状态与审核元数据（方案 9.1 相册扩展的最小集）
-- 审核状态沿用既有 audit_status（PENDING/APPROVED/REJECTED）；本轮补：
--   bind_status      远程文件引用绑定状态（BINDING/BOUND/FAILED）——跨服务无分布式事务，
--                    以显式中间态 + 幂等引用键 PET_ALBUM:{id} 收敛
--   reviewer_id/review_reason  审核动作留痕（谁驳回了、为什么），驳回链审计依据
-- 可空列，旧程序仍可读写（expand 阶段）。

ALTER TABLE `pet_album_asset`
    ADD COLUMN `bind_status` VARCHAR(20) NULL
        COMMENT '文件引用绑定状态: BINDING/BOUND/FAILED（存量行迁移后回填 BOUND）' AFTER `audit_status`,
    ADD COLUMN `reviewer_id` BIGINT UNSIGNED NULL
        COMMENT '审核处理人（adminUserId）' AFTER `bind_status`,
    ADD COLUMN `review_reason` VARCHAR(255) NULL
        COMMENT '审核理由（驳回必填）' AFTER `reviewer_id`;

-- 存量对齐：历史行均为上传成功后落库（旧协议无绑定阶段），回填为 BOUND
UPDATE `pet_album_asset` SET `bind_status` = 'BOUND' WHERE `bind_status` IS NULL;
