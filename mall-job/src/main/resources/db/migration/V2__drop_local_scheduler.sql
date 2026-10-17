-- OPS-01：调度只保留一个事实来源（XXL-JOB）——本地 Cron 注册表退役。
-- sys_job 表在远程与各环境均为 0 条（未启用即切换），直接 DROP 不丢事实；
-- 任务配置/执行日志的权威在 xxl_job 库（xxl_job_info / xxl_job_log），
-- mall-job 管理接口改为跨库只读适配层 + 白名单手动运行。
DROP TABLE IF EXISTS `sys_job`;
DROP TABLE IF EXISTS `sys_job_log`;
