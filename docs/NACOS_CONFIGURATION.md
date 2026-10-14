# CloudMart 配置集中管理

2026-10-15，本地业务配置已迁移至正在运行的 Nacos 3.2.2。
控制台：[本地 Nacos](http://127.0.0.1:9090/)，命名空间 `public`，分组 `DEFAULT_GROUP`。

## 配置位置

| Data ID | 用途 |
| --- | --- |
| `shared-runtime.yml` | 60 个运行变量：SMTP、服务令牌、文件签名、数据库、Redis、MQ、AI 默认项、文件存储、天气、通知、调度；全部业务服务导入 |
| `mall-<service>.yml` | 原有服务专用设置，包括已配置的 AI、天气、高德地图、微信通知；优先于共享默认项 |
| `local-launch-archive.yml` | 17 个基础设施、构建及停用变量的备份，包括 OSS 密钥；业务服务不导入 |
| `shared-logging.yml` | 原有公共日志配置 |

真实值只保存在 Nacos 和 Git 忽略的本地备份中。
[完整变量清单](ENVIRONMENT_VARIABLES.csv) 只包含变量名、引用文件、类别、配置位置，不包含值。
清单涵盖显式后端变量、测试变量、前端构建变量、脚本参数和本地 `.env` 项。
`not-configured` 表示本次没有本机配置值且不属于业务默认项，不代表需要填写。
Spring 的任意属性理论上都可通过环境变量覆盖；清单列出项目实际引用或本机实际设置的项目变量。

## 名称统一

| 原环境变量 | 新名称 |
| --- | --- |
| `WISH_MAIL_HOST` | `CLOUDMART_MAIL_HOST` |
| `WISH_MAIL_PORT` | `CLOUDMART_MAIL_PORT` |
| `WISH_MAIL_USERNAME` | `CLOUDMART_MAIL_USERNAME` |
| `WISH_MAIL_PASSWORD` | `CLOUDMART_MAIL_PASSWORD` |
| `WISH_MAIL_FROM` | `CLOUDMART_MAIL_FROM` |
| `WISH_SERVICE_TOKEN_SECRET` | `CLOUDMART_SERVICE_TOKEN_SECRET`（与已有同名变量合并，迁移前已验证值相同） |
| `WISH_JWKS_URI` | `CLOUDMART_JWKS_URI` |
| `WISH_IT_ENABLED` | `CLOUDMART_IT_ENABLED` |
| `WISH_IT_MYSQL_HOST` / `WISH_IT_MYSQL_PORT` | `CLOUDMART_IT_MYSQL_HOST` / `CLOUDMART_IT_MYSQL_PORT` |
| `WISH_IT_REDIS_HOST` / `WISH_IT_REDIS_PORT` | `CLOUDMART_IT_REDIS_HOST` / `CLOUDMART_IT_REDIS_PORT` |

测试开关保留 Java 系统属性 `-Dwish.it.enabled=true`；它不是环境变量。
`wish.*` 业务属性、服务名 `mall-wish`、业务错误码和事件类型不属于此次环境变量前缀更名。

邮件属性 `cloudmart.mail.*`、文件签名属性 `file.download-signing-secret` 和各服务令牌属性，
通过占位符解析 Nacos 中的大写变量名。共享令牌使用迁移前 Windows 系统变量的值；
原来各服务 Nacos 文档中的不同重复令牌已移除。
OSS 当前停用，凭据已保存到归档配置；迁移不会启用 OSS。

## 可以手动删除的 Windows 变量

以下项目已逐值核对迁移。只删除这些变量，不需要新建对应的 `CLOUDMART_MAIL_*` 系统变量。

| Windows 位置 | 变量名 |
| --- | --- |
| 用户变量 | `OSS_ACCESS_KEY`、`OSS_SECRET_KEY`、`WISH_SERVICE_TOKEN_SECRET` |
| 系统变量 | `CLOUDMART_FILE_SIGNING_SECRET`、`CLOUDMART_SERVICE_TOKEN_SECRET`、`OSS_ACCESS_KEY`、`OSS_SECRET_KEY` |
| 系统变量 | `WISH_MAIL_FROM`、`WISH_MAIL_HOST`、`WISH_MAIL_PASSWORD`、`WISH_MAIL_PORT`、`WISH_MAIL_USERNAME` |

共 10 个不同名称、13 条用户/系统记录。此次操作没有修改 Windows 持久环境变量。
启动脚本会屏蔽继承的旧业务变量，旧终端在删除前后都可通过脚本使用 Nacos 配置。
从 IDEA 等直接启动时，删除变量后重开 IDE，避免进程继承旧值。

## 必须先于 Nacos 提供的参数

`NACOS_SERVER_ADDR`、`NACOS_USERNAME`、`NACOS_PASSWORD`、`NACOS_NAMESPACE` 是客户端连接入口，
无法仅放在其尚未连接的 Nacos 中。当前保留在忽略的本地 `.env`，并在归档 Data ID 中备份；
代码保留当前本机的连接默认值。

Docker 启动 Nacos/MySQL/Seata 的密码和启动参数、SkyWalking Agent 参数、Expo/Taro/Umi
构建参数也不会自动消费 Spring 的配置。当前已设置项已备份到归档 Data ID，
实际启动仍使用 `.env` 或工具参数。`JAVA_HOME` 等电脑通用变量不要删除。
MySQL 的 Docker/数据库维护参数与 Nacos 中的业务连接参数需保持一致。

## 启动与验证

所有 23 个业务模块均导入必需的 `shared-runtime.yml`，防止缺少远端配置时静默使用空密钥。
入口：`scripts/start-services.ps1`，或 `python scripts/dev-services.py start`（需 JDK 26）。
两个入口会清理子进程继承的业务变量，保留 Nacos 连接参数；
`dev-services.py` 不再生成或注入临时服务签名密钥。

PowerShell 入口默认使用本机 `.jdks/openjdk-26.0.1`（若存在），也可用 `-JavaHome` 指定。
例如 `./scripts/start-services.ps1 -Services mall-user,mall-wish`；首次构建可加 `-Build`。
`start-all.ps1` 是迁移前已有的本地未提交文件，本次提交不包含该文件；它不会屏蔽业务环境变量。

迁移工具使用 Python 和 PyYAML，调用 [Nacos 3 Admin API](https://nacos.io/en/docs/latest/manual/admin/admin-api/)：

```powershell
python scripts/nacos_config.py verify
```

`migrate` 子命令用于首次迁移，读取当前系统变量、`.env` 和代码默认值并写入 Nacos。
日常修改直接在 Nacos 维护，不要重新执行首次迁移覆盖已经调整的共享配置。
私密备份在 `.tmp-nacos-migration/`，包含原 Nacos 文档、原 `.env` 和原注册表项目变量；
该目录被 `.gitignore` 的 `/.tmp-*` 规则忽略，不应提交或分享。

密钥、SMTP 客户端及 Feign 签名器等已创建的对象未必支持热更新。
修改密钥后应重启相关服务，保持调用方和接收方使用一致的值。

## 本次验证结果

- 全部 Maven 模块编译及安装通过；`mall-user` 原有重复 YAML 键已去重修复。
- 23 个业务服务分别在屏蔽业务环境变量后加载 Nacos，60 个共享变量的摘要逐项一致。
- 8 个相关服务实际启动成功，健康检查均为 `UP`：auth、user、wish、file、admin、job、pet、gateway。
- 24 项现有服务令牌测试、4 项迁移与启动环境回归检查全部通过。
- 22 份原有服务专用配置逐项对比，除移除重复令牌外均保持原值。
- 验证启动的 8 个服务已停止，恢复验证前状态；Nacos 及原有中间件保持运行。
