-- T01/LC30：删除旧支付链路的 `payments` 表。
-- 唯一支付真值源为 payment_attempt（V4）+ payment_notify_log（V4）；
-- 退款事实由 T02 refund_order 契约承载（后续迁移）。
-- 开发库重建路径见方案 12.3：本脚本随全量迁移在空库/既有开发库执行后旧表消失。
DROP TABLE IF EXISTS `payments`;
