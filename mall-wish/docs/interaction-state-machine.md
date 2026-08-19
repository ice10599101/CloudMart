# 心愿互动状态机与星光结算规则（Sprint 1.2）

> 实现类：`com.cloudmart.wish.service.impl.InteractionServiceImpl`
> 设计依据：需求文档 2.2 / 4.1 / 6.1 / 6.4 / 6.5 节

## 1. 互动类型与星光规则

| 类型 | 行为约束 | 互动者星光 | 作者星光 |
|---|---|---|---|
| LIGHT 点亮 | 可重复，每次独立扣费 | **-2**/次（SPEND，source=LIGHT_OTHER） | **+1**/次（EARN，source=LIGHTED），日上限 20 |
| SAME_WISH 同求 | 每愿望唯一（未删除时），可取消后重新同求 | 0 | **+2**/次（EARN，source=SAME_WISHED），日上限 50 |
| BLESS 祝福 | 每愿望 1 次/日，内容必填（≤200 字） | 0 | 0 |
| ANON_STAR 匿名星光 | 未启用（Sprint 2.6），接口直接 400 | — | — |

取消互动（软删）：**已扣/已发星光一律不退还**（文档 6.1 取消规则）。

## 2. 状态机

### 2.1 互动记录（wish_interaction）

```
           createInteraction                  revokeInteraction
不存在 ────────────────► ACTIVE ──────────────────────► REVOKED(软删)
                          │  ▲                              │
                          │  └──────── 重新同求 ◄────────────┘
                          │     （仅 SAME_WISH：软删后可再次创建新记录，
                          │       唯一约束只约束未删除记录）
                          ▼
                    星光结算同事务完成
```

- ACTIVE：`deleted_at IS NULL`，参与各端计数展示；
- REVOKED：`deleted_at` 非空，用户端列表不展示，管理端审计可见（轨迹保留）；
- 点亮无"取消"语义上限制——多记录叠加，取消即软删单条记录。

### 2.2 心愿计数联动（updateWishCounter）

```
创建互动:  light_count / same_wish_count / bless_count  +1（原子 SQL，GREATEST 防负数）
取消互动:  对应计数 -1
support_count 为生成列（light + same_wish + bless），自动联动
```

## 3. 作者星光日上限的防刷口径

**按"含软删记录"的当日总数判定**（`countIncludingDeletedSince`，平台时区当日 0 点起）。

原因：取消互动不退星光，若按未删除计数，会出现「取消→重新互动」绕过日上限重复发薪的漏洞。
判定时机在发放**之前**：`todayCount > cap` 则本次不再发放（互动本身仍成功）。

## 4. 事务边界与一致性

```
TransactionTemplate.execute:
  ├─ 同求 DB 存在性校验（第二道防线）
  ├─ 互动记录 insert
  ├─ 星光结算（互动者 SPEND / 作者 EARN，均与流水同事务，balance_after 快照）
  └─ 心愿计数原子更新
提交后(afterCommit):
  └─ RocketMQ 发送 total_helped +1 事件（异步累加，发送失败由对账兜底）
```

关键决策：
- 编程式事务（`TransactionTemplate`）而非 `@Transactional`：`createInteraction` 内部自调用事务体，
  注解事务不生效；
- MQ 事件在**事务提交后**发送：避免回滚后统计多加；
- total_helped 异步化：避免互动接口被统计写阻塞。

## 5. 可互动性校验（requireInteractableWish）

不可互动/不可见统一返回 `WISH_NOT_FOUND`(404)，不暴露存在性：

- 心愿不存在或已软删；
- visibility = PRIVATE / TREE_HOLE 且非作者（TREE_HOLE 互动 Sprint 2.x 开放）；
- audit_status = REJECTED / AUTO_HIDDEN；
- is_visible = false（先发后审场景被下架）。

## 6. 错误码一览

| code | HTTP | 场景 |
|---|---|---|
| `WISH_INTERACTION_TYPE_INVALID` | 400 | ANON_STAR 等未开放类型 |
| `WISH_VALIDATION_ERROR` | 400 | 祝福内容为空/超长/含非法字符；游标格式错误 |
| `WISH_FORBIDDEN` | 403 | 取消他人的互动 |
| `WISH_NOT_FOUND` | 404 | 心愿不可见/不存在；互动记录不存在 |
| `WISH_ALREADY_INTERACTED` | 409 | 已同求过该心愿 |
| `WISH_STARLIGHT_INSUFFICIENT` | 402 | 点亮时星光余额不足 |
| `WISH_RATE_LIMITED` | 429 | 限频触发（详见限频策略文档） |

## 7. "今日已祝福"判定口径（interactions/my）

- DB 时间按平台时区（Asia/Shanghai）存储，`createdToday` 按**用户时区**折算判定；
- 与限频口径一致：用户时区的"当日"内已 BLESS → 前端禁用祝福按钮。
