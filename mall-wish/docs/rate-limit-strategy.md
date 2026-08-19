# 心愿互动限频策略（Sprint 1.2）

> 实现类：`com.cloudmart.wish.service.impl.InteractionRateLimiter`
> 设计依据：需求文档第 32 章（限频矩阵 / 32.3 同求唯一 / 32.4 降级原则）

## 1. 限频矩阵

| 维度 | 互动类型 | 上限 | 计数周期 | 时区口径 |
|---|---|---|---|---|
| 用户 | LIGHT 点亮 | 50 次/日 | 当日 | 用户时区（wish_user_stat.timezone） |
| 用户 | SAME_WISH 同求 | 10 次/日 | 当日 | 用户时区 |
| 用户 | BLESS 祝福 | 20 次/日（总量） | 当日 | 用户时区 |
| 心愿 | LIGHT 被点亮 | 200 次/日 | 当日 | 平台运营时区 Asia/Shanghai |
| 用户×心愿 | BLESS | 1 次/日 | 当日 | 用户时区 |
| 用户×心愿 | SAME_WISH | 永久唯一 | — | SETNX 无 TTL |
| 用户 | ANON_STAR 匿名星光 | 3 次/日 | — | Sprint 2.6 预留，当前接口拒绝 |

## 2. Redis Key 设计

格式：`wish:rate:{维度}:{标识}:{type}`

| Key | 示例 |
|---|---|
| 用户维度 | `wish:rate:user:1001:light` |
| 心愿维度 | `wish:rate:wish:88:light` |
| 用户×心愿 祝福 | `wish:rate:user_wish:1001:88:bless` |
| 用户×心愿 同求占位 | `wish:rate:user_wish:1001:88:same_wish`（值为占位时间戳） |

- 计数 Key 在**首次自增**时设置 TTL 至「指定时区当日 23:59:59」；
  心愿维度按平台时区，用户维度按用户时区。
- 时钟回拨保护：若计算的过期时刻异常（≤ 当前时刻），退化为固定 24h TTL，防止永不过期。

## 3. 同求唯一的三道防线

1. **Redis SETNX 占位**（快速拒绝，无 TTL）：冲突时直接返回 `WISH_ALREADY_INTERACTED`(409)；
2. **DB 存在性校验**（事务内）：Redis 降级时的第二道拦截；
3. **`uk_interaction_unique` 函数唯一索引**（最终正确性保障）：
   `type != 'LIGHT' AND deleted_at IS NULL` 时 (wish_id, user_id, type) 唯一。

占位补偿：占位成功但事务内落库失败（唯一索引冲突等 `WISH_ALREADY_INTERACTED` 场景）
会**释放占位**，允许客户端重试；取消同求时也释放占位（软删记录不参与唯一约束，可重新同求）。

## 4. 降级策略（Fail-Open）

Redis 不可用（`RedisConnectionFailureException`）时：**放行请求 + WARN 日志**。

依据 32.4 节原则：Redis 仅用于减少重复请求与快速拒绝，
**数据库唯一约束才是最终正确性保障**——限频是防刷优化层，不是正确性层。
后果：Redis 宕机期间可能出现短时超限（如用户×心愿祝福超 1 次/日），
由 DB 唯一索引兜底的数据（同求唯一）不会破坏，纯计数限频允许少量超额。

## 5. 执行顺序

限频检查位于方法入口、**DB 写事务之外**（避免事务内做 Redis 网络调用拖长锁持有时间）：

```
入口校验（类型/心愿可见性/用户时区）
  → Redis 限频（用户维度 → 心愿维度 → 用户×心愿维度 → 同求占位）
  → 祝福内容净化（非空/≤200/路径穿越/XSS 转义）
  → TransactionTemplate 事务体（落库 + 星光结算 + 计数更新）
```

## 6. 相关错误码

| code | HTTP | 场景 |
|---|---|---|
| `WISH_RATE_LIMITED` | 429 | 任一限频维度达到上限 |
| `WISH_ALREADY_INTERACTED` | 409 | 已同求过该心愿（占位冲突 / DB 校验 / 唯一索引） |

## 7. 观测与运维

- 限频触发与降级均有 WARN 日志（含 key），可通过 Loki 检索 `wish:rate:` 关键字观测防刷命中；
- 同求占位 Key 无 TTL：若 Redis 异常导致「占位残留 + DB 记录已软删」需人工删除
  `wish:rate:user_wish:{userId}:{wishId}:same_wish`；
- 限额调整：修改 `InteractionRateLimiter` 常量（当前为编译期常量，调整需重启）。
