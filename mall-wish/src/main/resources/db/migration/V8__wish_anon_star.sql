-- ---------------------------------------------
-- Sprint 2.6 匿名星光（ANON_STAR）
-- wish 表新增匿名星光计数列（文档 2.2/4.1/6.2 节）
-- ---------------------------------------------
-- 语义：同一心愿被匿名星光帮助的累计次数
-- 唯一性：由 wish_interaction.uk_interaction_unique 保证
--        （非 LIGHT 且未删除时 wish_id+user_id+type 唯一，同一用户同一心愿仅 1 次）
-- 计数口径：与 light_count/same_wish_count/bless_count 一致，
--        取消互动时 -1（GREATEST 防负），已扣星光不退还
-- 说明：support_count 生成列保持"点亮+同求+祝福"口径不变（V1 原文定义）
ALTER TABLE `wish`
    ADD COLUMN `anon_star_count` INT NOT NULL DEFAULT 0
    COMMENT '累计匿名星光数(含取消后回滚)'
    AFTER `bless_count`;
