# 徽章系统设计文档（待办 ①：condition JSON 声明式触发判定）

> 模块：mall-wish · 交付日期：2026-08-20
> 需求依据：心愿宇宙文档 6.5 成就框架 / 2.6 个人中心 / 2.9 成就与排行
> 范围：后端触发判定引擎 + 用户端 API；管理端 CRUD 属后续管理后台 Sprint，
> `BadgeConditionParser.validate` 已为其预留 condition 编辑校验能力。

---

## 1. 触发链路

```mermaid
flowchart LR
    subgraph 触发点（统计变更事务内）
        A["WishServiceImpl.createWish"] -->|"同事务 incrementOnWishCreated"| S["UserStatServiceImpl"]
        M["WishStatSyncConsumer<br/>(MQ total_helped +1)"] -->|"独立事务 incrementTotalHelped"| S
    end
    S -->|"尾部调用"| E["BadgeServiceImpl.evaluateAndAward"]
    E --> P["BadgeConditionParser.parse<br/>(纯函数, Fail-Open)"]
    P --> C{"metric >= threshold<br/>且未持有?"}
    C -- 是 --> I["INSERT wish_user_badge<br/>(uk_user_badge 幂等兜底)"]
    C -- 否 --> SKIP["跳过"]
```

## 2. condition JSON Schema（管理端编辑校验复用 validate）

```json
{
  "type": "WISH_CREATED | WISH_FULFILLED | TOTAL_HELPED | TOTAL_CHECKIN_DAYS",
  "threshold": 1,
  "description": "发布第一个心愿"
}
```

- `type` → `wish_user_stat` 指标映射见 `BadgeConditionType`（每种类型实现 `extractMetric`）
- `threshold`：正整数；判定语义 `metric >= threshold`（恰好等于阈值即达标）
- `description`：必填，前端"如何获取"展示文案（表无独立 description 列，存于 JSON 内）
- 解析失败（非法 JSON/未知 type/缺字段/阈值非正）→ 跳过该徽章不阻断事务（Fail-Open）+ WARN 日志
- **声明式扩展**：新增徽章仅 INSERT `wish_badge` 一行，引擎零改动；
  打卡/还愿统计上线后对应条件类型（TOTAL_CHECKIN_DAYS/WISH_FULFILLED）自然生效

## 3. 幂等与并发

| 层 | 机制 |
|---|---|
| 常规 | 授予前 SELECT 已持有集合，已持有跳过 |
| 并发 | 双实例同时授予同一徽章 → `uk_user_badge(user_id, badge_id)` 唯一索引冲突，捕获 `DuplicateKeyException` 忽略 |
| MQ 重复消费 | total_helped 至少一次投递重复 +1（既有容忍口径），徽章授予由上述双层幂等保证不重复 |
| 事务 | 心愿创建链路：授予与心愿 INSERT 同事务，回滚一致撤销；MQ 链路：与统计 +1 同独立事务 |

## 4. 接口契约

| 接口 | 方法 | 权限 | 说明 |
|---|---|---|---|
| `/my/badges` | GET | 登录 | 徽章墙聚合：全部定义 + earned/earnedAt（已获得）/condition+progress（未获得）；已获得在前（获得时间倒序），未获得按 badgeId 升序 |
| `/badges/definitions` | GET | 公开（SecurityConfig permitAll） | 徽章图鉴：全部定义含 rarity；未登录可浏览 |

进度语义：`progress.percentage = min(100, ceil(current*100/threshold))`；已获得条目 current=threshold（100%）。

## 5. 数据变更（Flyway V5）

- `wish_badge` 新增 `rarity VARCHAR(16) NOT NULL DEFAULT 'COMMON'`（文档 2.9 契约字段）
- V1 种子补齐：FIRST_WISH/FIRST_FULFILL=COMMON、HELP_100=EPIC、PERSIST_365=LEGENDARY

## 6. 性能与缓存

徽章定义为个位数行的低频变更配置数据，`evaluateAndAward` 直查 DB
（每次统计变更 +2 次小查询 + 仅新达标 INSERT），无缓存必要；
管理端 CRUD 上线后若定义规模增长再评估缓存。

## 7. 已知边界与后续

- **BADGE_EARNED 通知事件**（文档 27.1 Tag）：通知中心对接前不发送，
  `evaluateAndAward` 返回新授予列表已为通知链路预留
- **漏发补偿扫描**（mall-job 定时全量判定）：属待办 ② XXL-Job 定时任务补全范围，
  当前同步挂载覆盖全部统计变更点，MQ 消费失败重试耗尽进 DLQ 时由补偿扫描兜底
- **WISH_FULFILLED / TOTAL_CHECKIN_DAYS**：还愿闭环与打卡功能上线后，
  对应统计 increment 方法尾部按同模式挂载 `evaluateAndAward`
