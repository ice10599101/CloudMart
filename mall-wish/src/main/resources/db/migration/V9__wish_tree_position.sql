-- ---------------------------------------------
-- Sprint 2.1 世界生命树 3D 版（文档 2.5 / 第二章 1.）
-- wish 表新增世界树球面坐标列 + 存量回填
-- ---------------------------------------------
-- 语义：文档 2.5 注——取消绝对三维坐标 {x,y,z}，服务端计算并持久化
--       球面角度参数 position: { theta, phi }：
--   · tree_theta  经度角 [0, 2π) 弧度，0 为本初子午线方向
--   · tree_phi    纬度角 (0, π] 弧度，0=北极 π=南极（acos 反余弦分布）
-- 赋值时机：PUBLIC 心愿创建/转公开时固化（果实位置稳定不跳动）；
--           PRIVATE/TREE_HOLE 不上树不赋值；坐标一经写入不变更
-- 口径说明：果实可见性与公开列表一致——visibility=PUBLIC +
--           audit_status=APPROVED + is_visible=1 + status 白名单 +
--           未软删（WorldTreeServiceImpl 统一封装，四端一致）
-- 索引依据：GET /tree/fruits bounds 视口过滤以 theta 区间为前导
--           range 条件（phi 回表过滤），idx_wish_tree_theta 支撑
--           该访问路径；果实量级 <10w 时选择性充足
-- 回填公式：黄金角散列（同 id 恒同值，幂等可重跑；BIGINT 乘法
--           先取模防溢出）；与 Java 侧 TreePositionCalculator 公式
--           独立（坐标只赋值一次并持久化，两公式无需对齐）
ALTER TABLE `wish`
    ADD COLUMN `tree_theta` DECIMAL(9,7) UNSIGNED NULL
        COMMENT '世界树球面经度角theta[0,2π)弧度(PUBLIC心愿发布时固化,果实挂载方位)'
        AFTER `geohash`,
    ADD COLUMN `tree_phi` DECIMAL(9,7) UNSIGNED NULL
        COMMENT '世界树球面纬度角phi(0,π]弧度(与tree_theta共同定位果实,北极0南极π)'
        AFTER `tree_theta`;

CREATE INDEX `idx_wish_tree_theta` ON `wish` (`tree_theta`);

-- 存量回填：仅回填当前满足上树口径的公开心愿（其余类型不上树无需坐标）
UPDATE `wish` SET
    `tree_theta` = ROUND(
        MOD(MOD(`id`, 2147483647) * 2654435761, 4294967296)
        / 4294967296 * 6.283185307179586, 7),
    `tree_phi` = ROUND(
        ACOS(1 - 2 * (
            MOD(MOD(`id`, 1000000007) * 2654435761, 4294967296)
            / 4294967296)), 7)
WHERE `visibility` = 'PUBLIC'
  AND `audit_status` = 'APPROVED'
  AND `is_visible` = 1
  AND `status` IN ('ACTIVE', 'FULFILLING', 'FULFILLED')
  AND `deleted_at` IS NULL
  AND `tree_theta` IS NULL;
