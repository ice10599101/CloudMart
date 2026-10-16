-- V8：反馈唯一键纳入状态（A19 回归修复）
--
-- V2 的 uk_feedback_reporter_fingerprint 覆盖全部状态行：反馈被管理员标记 RESOLVED
-- 后，同一用户对同一来源指纹的"再次反馈"（方案 5.3 的闭环要求，服务层去重查询
-- 也只挡 OPEN/VERIFYING）在 INSERT 时撞唯一键 → 500。
-- 将 status 纳入唯一键：同指纹同人每种状态至多一行——OPEN/VERIFYING 期间去重语义
-- 不变；RESOLVED 后允许产生新的 OPEN 行；状态流转原地更新不新增行。
ALTER TABLE `software_link_feedback`
  DROP KEY `uk_feedback_reporter_fingerprint`,
  ADD UNIQUE KEY `uk_feedback_reporter_fp_status`
      (`reporter_id`, `software_id`, `source_key`, `source_fingerprint`, `status`);
