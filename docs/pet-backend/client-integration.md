# 客户端接入说明（client-integration.md）

供后续外部设计与三端（Web/App/Taro）接入使用；不含 UI 设计要求。

## 1. 硬性契约

1. **ID 全部是字符串**（雪花 Long 经 JSON 序列化为字符串，含 MQ 消息体）。客户端禁止 `Number(id)`；旧端已发生的精度丢失无法在服务端恢复。
2. **时间**：时间点一律 RFC 3339 UTC（`2026-09-26T01:00:00Z`），客户端展示时自行转本地时区；业务日为纯日期字符串（北京时间口径）。
3. **幂等键**：所有写接口携带 `Idempotency-Key`（一次按钮操作一个键，网络重试复用原键）。同键不同内容 409 `PET_OPERATION_CONFLICT`。
4. **结算中**：`PET_SETTLEMENT_PENDING(503)` / 返回体 `actualCurrency=0` 表示星光结算中——按原请求重试或轮询 `GET /pet/operations/{operationId}`，**禁止**提示失败后重新生成一笔独立操作。
5. **serverNow / remainingSeconds / nextResetAt / claimExpiresAt**：倒计时只作展示，完成与否以服务端判定。

## 2. 目标动作映射（通知/任务/成就跳转）

targetType 语义（notifications.biz_type）：`PET_WORK_COMPLETED`→活动领取页、`PET_CAREER_*`→职业页、`PET_BOTTLE_CAUGHT`→漂流瓶、`PET_BATTLE_*`→对战、`PET_LEVEL_UP/EVOLVED`→宠物档案、`PET_INTIMACY_LEVEL_UP`→亲密页、`PET_HOME_VISIT/VISIT`→家园、`PET_WALL_MESSAGE`→留言墙、`PET_FRIEND_*`→社交页。均携带字符串 bizId。

## 3. 轮询与推送建议

- 活动完成：服务端 MQ→通知（eventId 去重）；客户端可按 `GET /pet/activities?status=COMPLETED` 补拉。
- 陪伴：60s 心跳（携带递增 seq），>90s 中断后重连不补计；Web 依赖 visibilitychange、App 前后台、Taro onHide/onShow 触发 stop/restart——服务端不依赖客户端声明防刷。
- 对战：待应战用 `GET /battle/pending`，不要从最近战绩筛选。

## 4. 能力版本（capability）

- `GET /pet/pets/{id}/actions` 的 reasonCode 是按钮禁用/文案唯一依据（`PET_QUOTA_EXHAUSTED`=可无收益互动、`PET_STATE_FULL`、`PET_USER_BUSY`）。
- 定时休息无领取步骤（到期自动应用）；技能槽模式默认关闭（`pet.skill-slots.enabled=false`），旧端"已学技能全部生效"行为保持。
- 旧接口兼容：`/work/claim`、`/study/claim`、`/bottle/claim` 保留，稳定取本人最新一条可领取任务；推荐切换 `POST /pet/activities/{activityId}/claim`。

## 5. 旧端注意（升级提示）

- 商城购买失败若为 503 结算中，UI 应显示"处理中"并保留原请求参数重试。
- 喂食/玩耍失败先查 actions 端点，不要依赖本地计数。
- 通知全部已读分入口：宠物页用 `PUT /reminders/read-all`（只清 PET）；站内通用页用 `PUT /notifications/read-all`（全站）。
