# 宠物模块后端整改与新增功能 — 实施状态报告

> 依据：`.ice/宠物模块后端整改与新增功能开发任务书.md`
> 更新时间：2026-09-26（M1 阶段交付）
> 环境：JDK 26.0.1 / Maven 3.9.15 / MySQL 迁移 V1–V6 → 本轮新增 V7–V14（mall-pet）、V5（mall-notification）、V38–V39（mall-wish）

## 0. 诚实声明（未完成项优先）

**本轮交付为 M0 基线 + M1 底层修复的主体部分。以下任务未开始或未完成：**

- **未完成**：B12–B22（商城拥有判断收尾、家园库存、拜访/好友/关系/留言统一规则、每日任务快照、限时活动窗口、成就补算、聊天 AI 事务边界、通知可靠性全量、排行榜隐私、管理端配置校验、性能/可观测性）。
- **未开始**：N01–N07 全部七项新增功能。
- **未执行**：T01–T46 验收矩阵中的并发/故障注入/跨服务契约/端到端测试（本轮以单元测试为主，未搭建 Testcontainers/真实 MySQL 并发环境）；`api-examples.http`、`data-migration.md` 演练、灰度指标等 M5 材料。
- **风险提示**：迁移 V7–V14 仅做静态审查，未在真实 MySQL 上执行过演练（任务书 §7.1 要求真实 MySQL 验证）；Flyway 迁移在联调环境首次启动时需人工确认 V8 的冲突回填行为。

## 1. 已交付任务状态

| 任务 | 状态 | 关键产出 |
| --- | --- | --- |
| M0 | 完成 | 三大模块代码地图（服务/实体/迁移/Feign/MQ 清单）；环境清单；git 差异记录（工作区改动集中在社区前端与 pet-game，与宠物后端无关） |
| B01 mall-wish 侧 | 完成 | `wish_pet_operation` 幂等表（V38）；`earn/spendStarlightIdempotent`（去重记录与余额更新同事务、重复同键返回原结果、同键不同内容 409 WISH_OPERATION_CONFLICT）；内部结果查询端点 `/internal/pet-support/starlight/operations/{operationId}` |
| B01 mall-pet 侧 | 完成 | `pet_operation` 操作记录（V7，REQUIRES_NEW 先提交 PENDING 再远程调用）；`PetOperationService`（确定性操作键 + 客户端 Idempotency-Key 双保险）；`PetOperationStore`；恢复任务 `PetOperationRecoveryService`（按原单查询钱包/幂等重试/补偿退款 COMPENSATED）；outbox `pet_outbox_event` + `PetOutboxService` 提交后可靠投递 |
| B01 调用方改造 | 完成 | 商城购买/进化/晋升/家具购买改为「幂等扣款 → 本地入包」；工作/读书/职业/任务/宝箱/活动/对战（双方各一单）/稀有瓶发薪改为「本地奖励 → 幂等发薪」，结果未知返回"结算中"不回滚 |
| B02 | 核心完成 | V8 迁移：活动唯一索引改为**每用户一条进行中**（冲突预检报告表 + 到期先结算 + 窗口函数保留最早一条）；`grantExp` 改限定列 + 显式版本 CAS（冲突重算一次后抛 PET_STATE_CONFLICT）；喂食/玩耍/清洁改 SQL 原子增量；活动/进化/晋升/家具等写入检查影响行数 |
| B03 | 部分 | 领取统一入口 `POST /pet/activities/{activityId}/claim`（WORK/STUDY/CAREER_WORK/BOTTLE 分派，归属 activity.petId 而非当前主宠）；统一活动列表 `GET /pet/activities`；领养 `FOR UPDATE` 用户级锁；旧按类型领取保留兼容（稳定序取本人最新一条） |
| B04 | 完成 | V9 余量列（四属性独立 DECIMAL 余量）；`PetStateService.applyIdleDecay` 重写：余量累计不吞步长、饱食跨阈值分段速率、触界归零不储备、48h 截断只结算一次；统一 `PetClock` |
| B05 | 完成 | V10 会话表+业务日表；服务端会话计时权威（首次心跳建基准、90s 失效不补计、seq 去重、多端一份时间、正常停止只结算有效窗口）；积分公式 entitled-grant 同事务落库；`gain` 改原子 SQL 自增（经验为 0 也落库亲密度）；升级事件经 outbox 提交后发送 |
| B06 | 部分 | V11 `pet_daily_quota` 数据库权威日额度（原子条件更新+释放）；喂食 5/日按用户共享（失败不耗次数）、玩耍收益 10/日超限转无收益动画、休息亲密度 3/日；休息改 10 分钟定时活动（全满 409 不耗次数、到期自动恢复、扫描器+惰性结算、无取消入口）；`GET /pet/pets/{petId}/actions` 统一动作 DTO；忙碌期间喂食/清洁放行、玩耍无收益 |
| B08 | 部分 | V12 seed 转有符号 BIGINT（预检写入冲突报告表）；野生模板稳定 templateId（选哪只打哪只，等级/属性确定性）；`GET /battle/pending` 待应战独立分页；PvP 结算双方各一次结果通知；回合流水 Round 已含 actorPetId/targetPetId 快照 |
| B10 | 完成 | V14 任职 stint 表（入职/离职时间、结束原因、段内累计；重新入职不清历史）；晋升条件统一（开放段次数+目标等级/智力+配置有效性）；入职仅第一阶（直接调接口同样拒绝）；职业工作进行中禁止转职/晋升；晋升幂等扣款（CAREER_PROMOTE:petId:toCode）+ 恢复补投递 |
| B11 | 完成 | 冷却独立于领取状态（最近完成时间+600s）；开始冻结成功率+随机种子到活动快照，结算按种子重放（重启/重试不重抽）；`PetBottleSettlementService` 独立事务 Bean（修自调用事务失效）；远程打捞带显式主人身份+稳定 requestId（wish 侧 V39 fish_log 唯一键幂等重放）；FAILED 按原种子语义重试 |
| B19 | 部分 | mall-notification V5 `event_id` 唯一键 + `sendPetEventNotification` 幂等落库；消费者消息体契约升级（ID 字符串）；`PUT /notifications/read-all?type=PET` 按类型已读（原全站语义保留）+ 内部端点 + 宠物侧 `PUT /reminders/read-all` 代理 |
| B07 | 完成 | JacksonConfig 重写：业务 ID（包装 Long）全量字符串输出、数量/等级保持 number；LocalDateTime 统一 RFC 3339 UTC（Z 后缀），反序列化兼容 Z/偏移/无时区；MQ 消息体（PetEventMessage）、Feign VO、管理代理同步字符串 ID |
| B02/B22 基础 | 部分 | `PetClock`（可注入 Clock，businessZone=Asia/Shanghai，业务日换算/重置点）；`PetRequestContext`（Idempotency-Key 请求上下文）；错误码 PET_STATE_CONFLICT/PET_SETTLEMENT_PENDING/PET_OPERATION_CONFLICT/PET_QUOTA_EXHAUSTED/PET_USER_BUSY + HTTP 映射 |

## 2. 数据迁移清单（未演练）

| 版本 | 模块 | 内容 |
| --- | --- | --- |
| V7 | mall-pet | pet_operation、pet_outbox_event |
| V8 | mall-pet | 迁移冲突报告表；到期先结算；多活动冲突保留最早（其余 EXPIRED+标记）；活动唯一索引改为每用户一条进行中 |
| V9 | mall-pet | pet 四属性小数余量列 |
| V10 | mall-pet | pet_companion_session / pet_companion_daily |
| V11 | mall-pet | pet_daily_quota |
| V12 | mall-pet | pet_battle.seed 改有符号（预检极值入报告表） |
| V13 | mall-pet | pet_activity.snapshot 规则快照列 |
| V14 | mall-pet | pet_career_stint 任职记录 + 存量回填 |
| V5 | mall-notification | notifications.event_id + 唯一键 |
| V38 | mall-wish | wish_pet_operation 幂等表 |
| V39 | mall-wish | fish_log.request_id + 唯一键 |

## 3. 测试结果（本轮实际执行）

- `mvn -pl mall-pet test`：**PASS（102 tests, 0 failures, 0 errors）**——含新增 B05 会话/积分/失效/停止 7 用例、B04 衰减（含余量与 CAS）、B01 扣款/发薪操作记录打桩断言、B11 快照冻结结算。
- `mvn -pl mall-notification test`：**PASS（47 tests）**。
- `mvn -pl mall-wish test`：**PASS（783 tests）**（修复 UserStatServiceImplTest 构造后）。
- **NOT RUN**：T01–T46 并发/故障注入场景（T01/T02/T05/T07 等需真实 MySQL + 多实例，本轮未搭建）、`mvn -pl mall-wish,mall-notification,mall-admin -am test` 联动回归、打包验证。

## 4. 下一步（按优先级）

1. 真实 MySQL 迁移演练（V7–V14 + 冲突清单人工核对）。
2. Testcontainers 并发/幂等测试落地 T01–T10、T11–T13、T23–T24。
3. B12–B22 剩余整改（背包拥有判断收尾/家园库存/每日任务快照/限时活动窗口/成就补算/聊天事务边界/排行隐私/管理端校验/性能基线）。
4. N01–N07 新增功能后端。
5. M5 交付材料：api-contract.md、api-examples.http、data-migration.md 演练记录、operations.md、client-integration.md。
