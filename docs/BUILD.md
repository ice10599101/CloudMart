# 构建与验证指南（ENG-01）

## 环境要求

| 工具 | 版本 | 说明 |
|---|---|---|
| JDK | 26+ | 后端编译目标；`java -version` 确认，不一致时设置 `JAVA_HOME` |
| Maven | 3.9+ | 或直接使用仓库自带 `./mvnw`（推荐，版本锁定 3.9.9） |
| Node.js | 26+ | 三端前端 |

## 一键构建（新机器）

```bash
# 后端：根 POM 全模块编译 + 单测（T26：与 CI 唯一口径，禁止维护模块子集清单；
# mvnw 首次运行自动下载 Maven 3.9.9）
./mvnw -B test

# 后端集成测试（Testcontainers 自给自足，需本机 Docker；由 failsafe 执行，
# 报告落 target/failsafe-reports；CI 同口径执行并校验"执行数>0 且 skipped=0"）
./mvnw -B -ntp -Pintegration-test verify

# 三端前端（锁文件严格安装 → 类型检查 → Web 单测 → 各端生产构建）
(cd CloudMart-ui && npm ci && npx tsc --noEmit && npx vitest run && npm run build)
(cd cloudmart-app && npm ci && npx tsc --noEmit && npx expo export --platform web)
(cd cloudmart-mobile && npm ci && npx tsc --noEmit && npm run build:h5 && npm run build:weapp)
```

CI（`.github/workflows/ci.yml`）执行同一命令集：push/PR 到 master 自动触发，
另含 migration-gate 门禁（见下）。

## 生产启动自检（ENG-01）

`mall-common` 的 `ProdStartupGuard` 在 `prod` profile 下强制校验：

- `cloudmart.security.service-token-secret` 必须配置（由 Nacos `shared-runtime.yml` 中的 `CLOUDMART_SERVICE_TOKEN_SECRET` 提供），
  长度 ≥32 字符，且不得包含已知弱口令片段（123456/changeit/secret/password/changeme/test）。
- 不满足时**拒绝启动**（fail-fast）——绝不允许内部端点在无密钥状态下对外服务。

部署前在对应环境的 Nacos `DEFAULT_GROUP/shared-runtime.yml` 配置：

```
CLOUDMART_SERVICE_TOKEN_SECRET: <≥32 字节随机串>
CLOUDMART_SECURITY_JWKS_URI: <mall-auth JWKS 地址>
```

变量位置、Windows 删除清单和启动入口见 [NACOS_CONFIGURATION.md](NACOS_CONFIGURATION.md)。

## Flyway 基线与迁移门禁（C26）

20 个业务库各有独立的迁移目录（mall-software 从 V1 起步，其余为 `V1__baseline.sql` + V2 增量）。切换、重建、来源证据和保护说明见 [DATABASE_BASELINE.md](DATABASE_BASELINE.md)。

```bash
python3 scripts/db-baseline/run.py verify
```

该命令在独立的新 MySQL 中执行全部迁移，核对表/索引/约束/种子摘要、菜单和权限种子，并验证第二次启动无迁移且 validate 通过。它还验证 checksum 漂移、文件缺失、自动 clean 与非空无历史库均被拒绝。CI 执行相同命令；出现 V2+ 后同时执行存量续跑路径和代表数据断言。

已启用基线的数据库禁止改写历史 V1；修复须增加迁移。不能在旧库中替换 V1 后 repair，也不能关闭 validate 或忽略 missing/future 以启动。

## 约定

- 后端单测（`mvn test`）不依赖外部资源；集成测试（隔离 MySQL/Redis/MQ）单独 profile
  执行，不进入默认构建门禁。
- MapStruct 转换器对交易核心（订单）启用严格映射；新增字段必须显式补充映射，
  未映射字段在编译期报错。
- 三端 `package-lock.json` 提交且 CI 用 `npm ci` 安装——锁文件漂移会在 CI 暴露。
