# 测试报告（T01–T46 执行结果）

## 1. 自动化单元测试（PASS）

```
mvn -pl mall-pet test            # 103 tests, 0 failures, 0 errors   PASS
mvn -pl mall-wish test           # 783 tests, 0 failures, 0 errors   PASS
mvn -pl mall-notification test   # 47 tests, 0 failures, 0 errors    PASS
mvn -pl mall-pet,mall-admin,mall-wish,mall-notification -am package  # PASS
```

## 2. 远程环境验收测试（2026-09-26，真实服务 + 真实 MySQL 9.7）

环境：`http://129.204.152.168:9023`（mall-pet，直连，`X-Internal-Call/X-User-Id` 认证链），
数据库 `mall_pet` / `mall_wish` 远程只读核对 + 专用测试用户（990001/990002，钱包 5000 封顶值）。

| 场景 | 结果 | 证据 |
| --- | --- | --- |
| T05 并发打工+读书互斥 | **PASS** | 1×200 + 1×409 PET_ACTIVITY_CONFLICT；DB 仅一条 IN_PROGRESS |
| T07 A 开工→切主宠 B→按 ID 领取 | **PASS** | 响应 petId=A；A 升 2 级 exp16、B 不变；重复领取 409；`CLAIM_WORK:activityId` 操作落库（含客户端幂等键） |
| T12 陪伴伪造秒数/seq 幂等 | **PASS** | 首跳建基准 credited=0；上报 600s 实计 64s（服务端时钟差）；重复 seq 幂等 accepted=false |
| T14 互动额度（喂食 5/玩耍 10） | **PASS** | FEED used=5 后 429 PET_INTERACTION_RATE_LIMITED；已饱不耗次数；PLAY 10 次 exp8 + 第 11 次起 exp:0 无收益 |
| T19 负 seed / 稳定野生模板 | **PASS** | PVE templateId=3 开打；seed=-426144292720364275 负数成功入库（V12 有符号列） |
| T18 PVP 应战/双方结算 | **PASS\*** | 应战→FINISHED、winner 正确；**发现缺陷**：PVP 防守方胜时 currency_reward 恒 0（已修，待部署） |
| T23 捞瓶结算后冷却 | **PASS** | 结算 CAUGHT/NORMAL（远程真实捞瓶 + requestId）；立即再开局 409 PET_BOTTLE_COOLDOWN |
| T42 小游戏伪造分数/乱序/重复结算 | **PASS** | 错窗/错目标/超期操作 accepted=0；合法窗内操作计入；successCount=2<3 无奖励；重复结算幂等同结果 |
| T43 托管周额度/互斥/提前退出 | **PASS** | 启动→重复启动 409（周额度+ACTIVE 唯一）→状态含照顾计数→提前结束 200→weekUsed=true 不退次数 |
| N01 引导进度 | **PASS** | 自动建档；FEED/PLAY 步骤由真实事件驱动（OPEN→DONE 由互动接线推进） |
| N02 成长日记自动生成 | **PASS** | T07 升级自动生成 `LEVEL_UP:petId:2` 日记条目（不可变快照，outbox 单点生成） |
| N03 记忆归属/编辑/删除 | **PASS** | 聊天自动抽取 owner_nickname；用非归属宠物编辑被拒（404，归属校验生效） |
| N06 合作贡献去重 | **PASS\*** | 接受→贡献计数 {"inviter":1}；**发现缺陷**：贡献表缺 updated_at 列导致 500（已修 V19 + 远程补列恢复） |
| B14 屏蔽生效 | **FAIL→已修待部署** | 屏蔽行已入库但留言仍成功——部署构件为旧版（本地代码已有拦截，见 §4） |
| B19 PET 全部已读 | **PASS** | 返回剩余未读 0；type=PET 范围接口可用 |
| B21 管理端操作查询/重试 | **PASS** | UNKNOWN 操作可查；原单重试 200（retry_count 递增，沿用同一 operationId） |
| B21 举报处理审计 | **PASS** | handle=200，status=HANDLED |
| T01/T04 同键并发购买 | **FAIL→已修待部署** | 钱包返回 400 MISSING_PARAMETER: refId（购买场景 refId 必填冲突）——幂等防线正确兜底（余额未动、op 转交恢复任务），代码已修 |
| T16 超安全整数 ID/时间契约 | **FAIL→待重部署** | 远程响应 `remainingSeconds:0`（数字）、时间无 Z 后缀——部署构件为旧 JacksonConfig；本地单测 `JacksonConfigContractTest` 证明新配置输出 `"id":"…"","time":"…Z"` 正确 |

## 3. 远程测试发现并已修复的缺陷（代码在本地仓库，需重新部署）

1. **B01 购买 refId 必填冲突**（阻断 T01）：wish 内部端点 `refId` 改为可选；pet 购买传 petId 作审计关联。
2. **B08 PVP 胜者星光恒 0**：`currency_reward` 不再绑定 attackerWon，PVP 胜者（含防守方）得奖。
3. **N06 贡献表缺 updated_at**：V19 迁移 + 远程已补列，贡献链路实测恢复。
4. **claimedAt 响应回显 null**：CAS 后回写内存对象（4 处）。
5. **N01/N06 钩子防护**：辅助钩子异常不再拖垮喂食/摆放主事务。
6. **部署构件过期**（T16/B14）：远程运行的 mall-pet 为部分旧构建（旧 JacksonConfig、缺留言屏蔽拦截）——需要 **clean rebuild 后重新部署**，然后重验 T16/B07 契约与 B14 屏蔽。

## 4. 仍 NOT RUN

- T02/T03（进程重启/补偿退款全流程——需部署 refId 修复后由恢复任务演示）、T06（强制版本冲突）、T08（领养并发上限）、T15/T17（Redis 故障/时区边界注入）、T25–T38 的数据库并发子项、T46 旧端全量回归。
- 上述场景依赖故障注入与多实例并发工具，已在单测层覆盖等价逻辑；建议在联调环境按本报告同口径补跑。
