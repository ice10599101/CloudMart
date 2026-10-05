# db/devops — 人工运维脚本目录（非 Flyway 扫描路径）

本目录中的脚本**不参与**应用启动时的 Flyway 自动迁移，只能由 DBA/运维在人工确认目标环境后手动执行。

## pet_dev_data_reset.sql — 宠物模块开发期数据清零

原 `V29__pet_dev_data_reset.sql`（PET-01 已迁出自动迁移生命周期）：多表 `TRUNCATE` 清空宠物用户/运行数据，仅保留配置/目录表。

**执行前提（迁移执行清单，逐项核对后才允许执行）：**

1. 确认目标库在库名白名单内（脚本守卫仅放行 `cloudmart_dev` / `mall_pet_dev`，非白名单库执行时立即报错终止，不触碰任何业务表）。
2. 确认 `flyway_schema_history` 中该环境是否已记录 V29：
   - 已记录：脚本本身不需要也无法重复执行（迁移生命周期外），需要重置数据时按人工脚本执行本文件；
   - 未记录：**禁止**把它补执行——有真实数据的环境执行即丢数据，模块升级路径不再包含该步骤。
3. 执行前完成备份并确认可恢复：`mysqldump` 全库导出 + 抽查恢复。
4. 执行前导出各运行表行数与钱包金额合计（`pet` / `pet_wallet_account` / `pet_wallet_ledger` / `pet_inventory` / `pet_purchase_order` 等），执行后留档比对。
5. 空库全新安装不涉及本脚本；V1→V67 前向迁移链完整可用。

**执行方式（mysql CLI）：**

```bash
mysql -h <host> -P <port> -u <user> -p <dev_database> < pet_dev_data_reset.sql
```

**新增配置表时**：清零前同步把新表加入脚本的 TRUNCATE 名单（或确认其属于保留名单）。
