# 运营手册（operations.md）

## 1. 配额参数（PetProperties，Nacos `mall-pet.yml` 可热更；数据库 pet_daily_quota 权威）

| 参数 | 默认 | 说明 |
| --- | --- | --- |
| interaction.feedDailyLimit | 5 | 每日有效喂食（用户共享；失败/已饱不消耗） |
| interaction.playRewardDailyLimit | 10 | 每日有收益玩耍；超限转无收益动画 |
| interaction.restDurationSeconds | 600 | 定时休息时长 |
| interaction.restIntimacyDailyLimit | 3 | 休息亲密度收益次数 |
| battle.rewardDailyLimit / pvpPerOpponentDailyLimit | 10 / 1 | 有收益对战；对同一对手 |
| home.dailyVisitLimit / friend.dailyVisitLimit | 10 / 5 | 拜访共享额度/好友子额度 |
| home.dailyLikeLimit | 20 | 点赞收益次数 |
| wall.dailyPostLimit | 10 | 留言+回复共享 |
| intimacy.companionSecondsPerPoint / DailyPointCap / DailyCapSeconds / companionSessionTimeoutSeconds | 600 / 8 / 7200 / 90 | 陪伴积分公式与会话失效 |
| chat.dailyLimit / aiDailyLimit | 20 / 20 | 消息频控（Fail-Open）/ AI 成本额度（Fail-Closed） |
| career.dailyWorkLimit | 6 | 职业工作次数（DB 计数） |
| home.comfortCap | 100 | 舒适度封顶 |
| skillSlots.enabled | false | 技能槽模式（关闭=已学全生效；开启需分配迁移） |

## 2. 功能开关与回退

- 新玩法 N04–N07：控制器独立，可按端点下线；关闭不影响已达成数据查询与领取。
- 交易新链路：`pet_operation` 恢复任务（30s 扫描）持续收敛 PENDING/UNKNOWN；不可关闭查询/补偿。
- 业务日切换：保持 UTC 存储 + businessZone 归属（见 data-migration.md §4），未做全量切库。
- Flyway：mall-pet/mall-wish 均为 repair→migrate，失败迁移修复 SQL 后重启自愈。

## 3. 指标与告警建议（B22）

结构化日志关键字：`requestId / operationId / eventId / userId / petId / activityId`。
建议采集：pet_operation 状态计数（PENDING/UNKNOWN 积压与最长等待）、outbox FAILED 积压、
配额拒绝数（PET_QUOTA_EXHAUSTED）、状态冲突数（PET_STATE_CONFLICT）、结算中响应数（PET_SETTLEMENT_PENDING）、
AI 降级次数。异常阈值示例：UNKNOWN 积压 > 100 或最长等待 > 30 分钟告警。

## 4. 异常操作处理

- **查询**：`GET /admin/pet/operations?status=UNKNOWN|FAILED`（支持 userId/petId 过滤）。
- **人工重试**：`POST /admin/pet/operations/{operationId}/retry` —— 沿用同一 operationId，
  钱包侧幂等，绝不产生第二次资金变动；COMPLETED/COMPENSATED 终态幂等拒绝。
- **补偿退款**：恢复任务自动执行（原单:refund 操作键）；失败保持 COMPENSATING 可查询可重试。
- **成就补算**：`POST /admin/pet/achievements/recalculate?petId=`（幂等，不重复发奖）。
- **举报处理**：`PUT /admin/pet/reports/{id}/handle?action=HANDLED|REJECTED`（记录处理人/时间）。

## 5. 扫描与定时任务

- PetActivityScheduler：分钟级活动结算（WORK/STUDY/CAREER_WORK→COMPLETED、BOTTLE settle、REST 自动应用）；小时级清扫（72h 领取过期、48h 待应战过期）。
- PetOutboxService：5s 批量投递，指数退避（30s→30min 封顶）。
- PetOperationRecoveryService：30s 扫描，自动重试 3 次后退避，等待人工介入（可查询、可重试）。

## 6. AI 故障处理

- AI 调用已移出数据库事务（短事务→外部生成→短事务），慢 AI 不占连接。
- 失败/超时/空回复 → 模板降级（isAiReply=false），危机词本地拦截始终可用。
- AI 成本额度 Fail-Closed：Redis 故障时拒绝 AI 路径，固定/危机回复不受影响。
