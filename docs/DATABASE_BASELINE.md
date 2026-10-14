# C26 开发/测试数据库基线

本次切换仅适用于重新创建的开发/测试数据库。负责人已确认：本机是全部开发/测试环境，允许重建并立即退役旧迁移。不得用此基线覆盖任何保留旧 `flyway_schema_history` 的库，也不得修改旧 V1 后执行 repair。

## 基线来源与目标

以 `scripts/db-baseline/legacy-provenance.json` 记录的 Git 提交与文件 SHA-256 为来源，在独立的 MySQL 9.7.0 空实例逐库执行原有 246 个 SQL 迁移和社区 V3 Java 迁移。旧链重放得到 19 个业务库、308 张表。执行目标调整后通过 `SHOW CREATE TABLE` 导出最终结构，通过显式种子白名单导出配置数据，形成各服务的 `V1__baseline.sql`。这些文件没有拼接历史 ALTER、回填、删除或调试数据。

目标基线包含 304 张业务表：

| 对象 | 处理 |
| --- | --- |
| `pet_operation` | 原链已删除，基线不创建 |
| `pet_wallet_cutover_batch/item` | 删除孤立实体、Mapper、表与历史切换脚本 |
| `wish_account_deletion` | 不创建；注销编排由 mall-user 的现有表承载 |
| `pet_migration_conflict` | 不创建；历史冲突报告与旧 reset 入口退役 |
| `wish_pet_operation` | 改为 `wish_starlight_operation`；同步实体、Mapper、服务和测试名称，保留全部金额、去重、摘要和退款关联字段及约束 |
| `wish_operation` | 保留通用写幂等结构 |
| `pet_event_progress` 等活动表 | 仍有实际读写，保留；C15 尚不满足删除条件 |
| Outbox/Inbox/compensation、undo_log、订单及明细分表 | 全部保留，8 张订单/明细分表对齐当前主表字段与索引（补齐幂等键、报价、退款、实付等字段）；其余未调整表 DDL 摘要与旧链结果一致 |
| 钱包/库存/支付/退款/奖励/请求回执台账 | 保留结构、约束和逻辑，基线不灌入运行记录 |

原空库链缺失后台基础菜单与超级管理员角色，存在孤儿按钮/角色绑定。本次补齐仍有效的静态菜单、角色 id=1、默认部门 id=1 及代码中真实使用的权限点；不复制旧管理员、会员或任何业务记录。五个运营角色沿用显式授权方式，不自动获得全量权限。具体调整见 `scripts/db-baseline/target-plan.json`。

基线种子只包含权限菜单/角色/绑定/部门、社区等级及敏感词、心愿配置/目录、宠物配置/目录和赛季并发守卫单例。**不包含**调试用户、订单、钱包余额、活动参与记录、任务集或迁移冲突报告。管理员账号仍由现有 `AdminDataInitializer` 在首次启动时初始化，属于运行时管理入口，不写入基线 SQL。

## 从零启动

需要 JDK 26、Python 3、Docker 和仓库 Maven Wrapper。Compose 固定 MySQL 9.7.0 与 Nacos 镜像摘要；`docker/mysql/init/` 提供 Nacos、XXL-Job 3.4.0、Seata 的初始化结构。XXL-Job 结构取自[官方 3.4.0-release SQL](https://github.com/xuxueli/xxl-job/blob/3.4.0-release/doc/db/tables_xxl_job.sql)，只保留 CloudMart 执行器与调度锁种子，移除上游示例任务、示例 API key 与默认用户。新 Nacos 使用首次管理员初始化；XXL-Job 如需控制台账号，由本机管理员单独建立，不导入旧账号或示例用户。先设置 `JAVA_HOME`；按 `.env.example` 准备本机 `.env`（不要提交真实凭据）。

```powershell
docker compose up -d
# 全新 MySQL 上正常迁移；已有旧历史的库会校验失败，需走下方显式重建
python scripts/db-baseline/run.py migrate
python scripts/dev-services.py start --build
python scripts/dev-services.py status
```

启动脚本从根 POM 发现全部 23 个业务应用，包含 mall-wish/mall-pet；加载本机 `.env`，在未配置服务令牌密钥时为本地生成并保存共享密钥到忽略目录 `.tmp-c26/`。启动应用前先通过独立物理连接执行 Flyway migrate/validate，确保 Seata AT 数据源代理初始化时已经存在 `undo_log`；应用启动仍再次正常 validate/migrate。Windows 参数文件使用相对工作区类路径，支持中文目录。不修改中间件数据库。日志在 `logs/c26/`，脚本只停止它自己记录且身份匹配的 Java 进程。

订单分片 profile 同样通过物理 MySQL 连接执行 Flyway；`application-sharding.yml` 不再关闭 Flyway。分表已在基线中创建，不能再单独执行历史 V2。

## 统一重建已有开发/测试库

```powershell
python scripts/dev-services.py stop
# 其他方式启动的业务进程也须先停止；脚本会检查服务端口
python scripts/db-baseline/run.py recreate --confirm-local-development --container cloudmart-mysql --redis-container cloudmart-redis
# 外部集成测试库在独立 MySQL，使用 mall_*_it，不复用业务库；端口由 MYSQL_IT_PORT 控制，默认 8307
python scripts/db-baseline/run.py recreate --confirm-local-development --container cloudmart-mysql-it
python scripts/dev-services.py start --build
```

重建入口限制为本机固定 CloudMart 容器，校验端口映射，先把固定业务库清单备份到 `.tmp-c26/backups/<时间>/business.sql`，再重新建库并执行基线。不会触碰 `nacos_config`、`xxl_job`、`seata` 或 MySQL 系统库。显式选择 `cloudmart-redis` 时清空开发 Redis 的 DB 0，防止旧缓存和会话污染新库。备份含本机旧开发数据，留在忽略目录，不进入版本库。

旧宠物 `pet_dev_data_reset.sql` 已删除。重建时不逐单补偿测试记录，也不执行历史钱包切换或旧用户迁移报表。

## 验证与后续增量

```powershell
python scripts/db-baseline/run.py verify
./mvnw.cmd -B -ntp test
```

`verify` 创建独立的临时 MySQL，逐库迁移、validate、再次 migrate（必须执行 0 项），验证中间件初始化表与最小执行器/锁种子，再校验全表 DDL 与种子行数/业务内容 SHA-256（仅忽略创建/更新时间等审计时间，仍校验业务时间字段）；核对 8 张分表与逻辑表结构完全一致、菜单父子关系、角色绑定、控制器权限种子；验证 checksum 漂移、迁移缺失、自动 clean、无历史的非空库全部被拒绝。临时容器在结束时销毁。生成报告在 `.tmp-c26/verify-schema.json`。

CI 使用相同入口。当前每库只有 V1，空库与二次启动均必须通过；未来出现 V2+ 时保留已有的存量续跑门禁及代表数据断言。任何失败都不能靠 `repair`、关闭 validate 或 ignore missing/future 绕过。

业务接口冒烟（会创建临时验收记录，不能作为基线种子）：

```powershell
python scripts/db-baseline/smoke.py --confirm-local-development
```

商品搜索索引可在服务启动后通过 `python scripts/db-baseline/smoke.py --confirm-local-development --refresh-search-only` 从当前数据库蓝绿重建，避免 SQL 重建后旧 ES 文档仍可被搜索；此模式不创建验收用户或订单。

该入口通过真实管理员/会员登录验证菜单、商品发布与读取、报价下单及幂等重放、领养与任务集生成。实测同时修正了商品库存建档客户端的旧路径：对齐 `/internal/inventory/init`，令牌仅授予 mall-product 对该建档路径的 `inventory:admin` 权限。测试结束后停止服务，使用上述 recreate 入口清除全部验收记录，再启动服务得到干净环境；无需逐单补偿。

基线启用后，**V1 不再改写**，下一次变更从 V2 开始正常增量迁移。新增权限同时补对应菜单种子；新增配置种子须更新工具中显式白名单。更新结构预期时运行 `python scripts/db-baseline/run.py record`，审查 `.tmp-c26/verify-schema.json` 后替换 `scripts/db-baseline/schema-manifest.json`，再运行 verify；record 不改写任何迁移文件。

旧 SQL、Java 历史迁移、四个自动 repair/版本专用保护配置、旧钱包切换脚本均已从当前工作树退役。历史源码只从 provenance 指定的 Git 提交恢复进行审计，不放回 Flyway 扫描目录。
