# mall-pet（社区宠物模块）

社区宠物后端服务：领养/互动/打工读书/捞瓶/对战/商城/家园/社交/聊天/钱包/管理端。

## 生产配置治理（P2-2）

本模块 `application.yml` 面向**本地开发**。生产环境以 Nacos `mall-pet.yml` 为准，上线前必须确认以下覆盖项生效（Nacos 优先级高于本地文件）：

| 项 | 本地默认 | 生产要求（Nacos mall-pet.yml） |
| --- | --- | --- |
| `logging.level.com.cloudmart.pet` | `DEBUG` | `INFO`（避免日志量失控） |
| `knife4j.enable` | `true` | `false` |
| `springdoc.api-docs.enabled` | 未显式关闭 | `false` |
| `springdoc.swagger-ui.enabled` | 未显式关闭 | `false` |

文档端点已在**网关层屏蔽**（mall-gateway `block-mall-pet-docs` 路由：`/api/pet/v3/api-docs/**`、`/api/pet/swagger-ui/**`、`/api/pet/doc.html` 等一律 404），Nacos 关闭是纵深防御的第二层。

## 数据库会话时区与业务时间口径（重要，排查时容易踩坑）

连接串 `serverTimezone=Asia/Shanghai` 保持现状即可：**DB 会话时区与业务 UTC 的换算统一由 `PetClock` 承担**（业务时间一律 `LocalDateTime.now(ZoneId.of("UTC"))` / `petClock.nowUtc()`）。

⚠️ 因此**禁止在业务 SQL 中直接使用 `NOW()` / `CURDATE()` 做业务计算**——`NOW()` 返回的是 DB 会话时区（Asia/Shanghai）时间，与业务写入的 UTC 值相差 8 小时，会造成边界判定错乱（如"当日额度""到期判定"）。需要当前时间时，由应用层经 `PetClock` 传入参数。
