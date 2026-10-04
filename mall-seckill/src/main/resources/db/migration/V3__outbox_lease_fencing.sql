-- V3 (T16)：Outbox 租约栅栏（fencing）——领取时递增 lease_version，
-- 状态回写必须绑定 owner+leaseVersion，失去租约的旧实例迟到回写被拒绝，
-- 杜绝 A 实例租约过期被 B 接管后 A 的迟到 markSent/markFailure 覆盖新执行者的事实。

ALTER TABLE outbox_event
    ADD COLUMN `lease_version` INT NOT NULL DEFAULT 0
        COMMENT '租约版本（T16 fencing：每次认领+1，回写校验）' AFTER locked_at;
