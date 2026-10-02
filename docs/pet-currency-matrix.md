# 宠物币域矩阵（P03 / docs）

> P03「币域和奖励事实统一」交付物：玩法 → 币种 → 钱包方法 → 奖励来源的权威矩阵。
> 唯一正账本：**PET_COIN**（`pet_wallet_account`，PetWalletService，DB 唯一键 `(user,bizType,bizKey)` 幂等）。
> 心愿 STARLIGHT（mall-wish）、社区经验/积分与 PET_COIN **独立**，不做隐式兑换。

## 钱包模式

| 配置 `pet.wallet.mode` | 行为 |
|---|---|
| null / blank / 非法值 / `PET` | PET_COIN 独立钱包（P03 新基线，唯一正账本） |
| `LEGACY`（仅显式配置） | 存量回退通道：社区星光 + pet_operation 幂等/恢复；**待全量迁移后物理删除** |
| `PAUSED` | 维护拒绝（新收支一律 PET_WALLET_MAINTENANCE，进行中请求不受影响） |

## 玩法矩阵（统一门面 PetEconomyService.earn/spend）

| 玩法入口 | 方向 | bizType | 奖励事实键（bizKey parts） | 奖励来源 |
|---|---|---|---|---|
| 战斗结算 | EARN | BATTLE_REWARD | battleId:petId:结果段 | 服务端结算快照（客户端上报无效） |
| 打工/学习领取 | EARN | CLAIM_WORK / CLAIM_STUDY | activityId | 活动服务端金额（封顶截断记快照） |
| 职业领取 | EARN / SPEND | CAREER_CLAIM | activityId | 配置货币奖励 |
| 每日任务 | EARN | QUEST_CLAIM | questId:date | 任务配置 |
| 事件奖励 | EARN | EVENT_* | eventId:分支 | 事件配置 |
| 商城购买 | SPEND | SHOP_BUY | itemId:意图键 | 客户端意图键收敛重试 |
| 进化 | SPEND | EVOLVE | petId:目标阶段 | 进化配置 costStarlight（PET 模式即宠物币价） |
| 瓶子结算 | EARN | BOTTLE_* | bottleId | 结算服务 |
| 陪伴/家园/季节 | EARN | 各自 bizType | 各自实体 ID | 配置/结算服务 |

## 类型基线（P03 去窄化）

- `PetWalletResult.amount/balanceAfter`：**long**（DB 列 BIGINT）
- `WalletSettlement.credited/balanceAfter`：**long/Long**（禁止 (int) 强转）
- `PetEconomyService.balanceOf`：**Long**（null=Fail-Open 隐藏）
- 前端 VO：`PetShopVO.balance` **Long**；JSON 数字无形态差异，前端无感
- LEGACY（社区星光）线上契约仍为 int（mall-wish 侧），经 `longValue()` 显式拓宽，无损

## LEGACY 物理删除清单（后续独立提交）

`PetEconomyServiceImpl.settleLegacy/starlightBalanceQuietly` → `PetOperationService`
（executeEarn/executeSpend/operationKey/pet_operation 存储与恢复）→ `WishFeignClient`
（starlightBalance/earn/spendStarlightIdempotent）→ `PetOperationRecoveryService` → `pet_operation`
表与迁移 → AdminPetOperationController → 各 LEGACY 测试。前置：线上确认 `mode=PET` 稳定运行。
