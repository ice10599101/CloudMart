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

- `cloudmart.security.service-token-secret` 必须配置（环境变量 `CLOUDMART_SERVICE_TOKEN_SECRET`），
  长度 ≥32 字符，且不得包含已知弱口令片段（123456/changeit/secret/password/changeme/test）。
- 不满足时**拒绝启动**（fail-fast）——绝不允许内部端点在无密钥状态下对外服务。

部署生产前注入：

```
CLOUDMART_SERVICE_TOKEN_SECRET=<≥32 字节随机串>
CLOUDMART_SECURITY_JWKS_URI=<mall-auth JWKS 地址>
```

## Flyway 迁移门禁（T26 第 4 点）

CI `migration-gate` job 对全部 19 个服务的迁移目录按**服务独立库**执行两条路径：

1. **空库全量**：V1 → head 一次 migrate 通过（validate-on-migrate 开启）。
2. **存量续跑**：先 migrate 到次末版本模拟存量库，再续跑到 head——
   等价真实发布的滚动升级路径。

本地模拟同一验证（需 Docker）：

```bash
docker run --rm --network host -v "$PWD/mall-wish/src/main/resources/db/migration":/flyway/sql:ro   flyway/flyway:11-alpine   -url="jdbc:mysql://127.0.0.1:3306/gate_mall_wish?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true"   -user=root -password=<pwd> migrate
```

规则：迁移提交后禁止修改历史文件（checksum 会破坏存量库校验），修复必须新增
迁移；门禁失败禁止用 `flyway repair` 掩盖。

## 约定

- 后端单测（`mvn test`）不依赖外部资源；集成测试（隔离 MySQL/Redis/MQ）单独 profile
  执行，不进入默认构建门禁。
- MapStruct 转换器对交易核心（订单）启用严格映射；新增字段必须显式补充映射，
  未映射字段在编译期报错。
- 三端 `package-lock.json` 提交且 CI 用 `npm ci` 安装——锁文件漂移会在 CI 暴露。
