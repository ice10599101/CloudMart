# 构建与验证指南（ENG-01）

## 环境要求

| 工具 | 版本 | 说明 |
|---|---|---|
| JDK | 26+ | 后端编译目标；`java -version` 确认，不一致时设置 `JAVA_HOME` |
| Maven | 3.9+ | 或直接使用仓库自带 `./mvnw`（推荐，版本锁定 3.9.9） |
| Node.js | 22+ | 三端前端 |

## 一键构建（新机器）

```bash
# 后端：22 个范围内模块编译 + 单测（mvnw 首次运行自动下载 Maven 3.9.9）
./mvnw -B -pl 'mall-common,mall-gateway,mall-auth,mall-user,mall-product,mall-order,mall-payment,mall-inventory,mall-coupon,mall-risk,mall-cart,mall-seckill,mall-notification,mall-ai,mall-marketing,mall-live,mall-wms,mall-admin,mall-file,mall-job,mall-gen,mall-community' -am test

# 三端前端（锁文件严格安装 → 类型检查 → Web 单测）
(cd CloudMart-ui && npm ci && npx tsc --noEmit && npx vitest run)
(cd cloudmart-app && npm ci && npx tsc --noEmit)
(cd cloudmart-mobile && npm ci && npx tsc --noEmit)
```

CI（`.github/workflows/ci.yml`）执行同一命令集：push/PR 到 master 自动触发。

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

## 约定

- 后端单测（`mvn test`）不依赖外部资源；集成测试（隔离 MySQL/Redis/MQ）单独 profile
  执行，不进入默认构建门禁。
- MapStruct 转换器对交易核心（订单）启用严格映射；新增字段必须显式补充映射，
  未映射字段在编译期报错。
- 三端 `package-lock.json` 提交且 CI 用 `npm ci` 安装——锁文件漂移会在 CI 暴露。
