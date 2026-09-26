# 宠物模块 API 契约（B07/§6 交付）

通用约定：
- 外层信封沿用现有 `ApiResponse{success,data,error,meta}`；认证走网关 `X-User-Id`（登录态），管理端 `/admin/**` 需 INTERNAL/管理员链路。
- **ID**：全部业务 ID JSON 输出为字符串（含消息体）；数量/等级/秒数为 number。客户端禁止 `Number(id)`。
- **时间**：时间点统一 RFC 3339 UTC（`2026-09-26T01:00:00Z`）；业务日为 `2026-09-26`（businessZone=Asia/Shanghai）。
- **幂等**：写接口支持 `Idempotency-Key` 请求头（服务端持久化校验；同键不同内容 409 `PET_OPERATION_CONFLICT`）。
- **分页**：默认 page/size（默认 20、最大 50）；游标接口返回 `nextCursor/hasMore`。
- 建议路径前缀经网关映射，服务内部不加 `/pet`（除新增显式路径）。

## 错误码（新增登记，其余见 PetErrorCodes）

| code | HTTP | 语义 |
| --- | --- | --- |
| PET_STATE_CONFLICT | 409 | 宠物状态版本冲突，可重试 |
| PET_OPERATION_CONFLICT | 409 | 操作键已存在但内容不同 |
| PET_SETTLEMENT_PENDING | 503 | 星光结算结果未知（按原请求重试幂等） |
| PET_QUOTA_EXHAUSTED | 429 | 每日收益额度耗尽（可执行无收益互动） |
| PET_USER_BUSY | 409 | 交互互斥位被占用（长期活动/游戏局进行中） |
| PET_BLOCKED | 403 | 屏蔽名单生效 |
| PET_FURNITURE_ALREADY_PLACED | 409 | 家具已摆放（每宠物每家具一个实例） |
| WISH_OPERATION_CONFLICT | 409 | 钱包操作键冲突（同键不同用户/金额/类型） |

## 核心端点（本轮新增/语义升级）

| 任务 | 方法与路径 | 说明 |
| --- | --- | --- |
| B01 | `GET /pet/operations/{operationId}`* | 业务操作结果查询（*管理端经 B21） |
| B02/B09 | `GET /pet/activities?status=&petId=&page=&size=` | 统一活动列表（含 petId/petName/claimExpiresAt） |
| B03/B09 | `POST /pet/activities/{activityId}/claim` | 按活动 ID 领取（奖励归 activity.petId） |
| B06 | `GET /pet/pets/{petId}/actions` | 动作可执行性（allowed/reasonCode/rewardRemainingToday） |
| B05 | `POST /companion/heartbeat`、`POST /companion/stop` | 服务端会话计时（seq 幂等；响应 PetCompanionSessionVO） |
| B08 | `GET /battle/pending?page=&size=` | 待应战独立分页；PVE 挑战请求带 `templateId` |
| B12 | `GET /inventory/equip-preview?itemCode=` | 装备替换预览（base/current/after/delta） |
| B13 | `POST /home/{petId}/like`、`DELETE` 同路径语义→`/home/{petId}/unlike` | 持久化点赞/显式取消（取消再点不再发经验） |
| B14 | `POST /blocks/{userId}`、`DELETE /blocks/{userId}`、`GET /blocks` | 屏蔽名单 |
| B14 | `POST /reports` | 举报（targetType: WALL_MESSAGE/BOTTLE_CONTENT/NICKNAME） |
| B15 | 每日任务 VO | 停用任务显式 `CANCELLED`（不阻挡宝箱，宝箱门禁排除 CANCELLED） |
| B19 | `PUT /notifications/read-all?type=PET`、`PUT /pet/reminders/read-all` | PET 类型全部已读（返回剩余未读数） |
| N01 | `GET /pet/onboarding`、`POST /pet/onboarding/skip` | 引导进度（领域事件驱动）/跳过 |
| N02 | `GET /pet/pets/{petId}/diary?cursor=&size=`、`POST/DELETE .../album` | 日记时间线、相册上传/删除（仅主人） |
| N03 | `GET/PUT/DELETE /pet/pets/{petId}/memories...`、`PUT .../memory-settings` | 记忆列表/编辑(USER 优先)/删除/批量清空/双开关 |
| N04 | `POST /pet/pets/{petId}/minigames`、`POST /pet/minigames/{id}/ops`、`POST .../settle`、`GET /pet/minigames` | 开局（训练局 rewardEligible=false）/提交操作/幂等结算/历史 |
| N05 | `POST /pet/custody/start`、`GET /pet/custody`、`POST /pet/custody/end` | 托管启动/状态（惰性照顾）/提前结束 |
| N06 | `POST /pet/cooperation`、`POST .../{id}/accept`、`GET /pet/cooperation`、`POST .../{id}/leave` | 合作邀请/接受/查询/退出 |
| N07 | `GET /pet/collection?category=&page=`、`GET /pet/collection/stats` | 图鉴列表/统计（未解锁返回线索） |
| B21 | `GET /admin/pet/reports`、`PUT /admin/pet/reports/{id}/handle`、`POST /admin/pet/achievements/recalculate?petId=` | 管理端举报处理/成就补算（INTERNAL） |

## 操作响应示例（B01 结构）

```json
{
  "success": true,
  "data": {
    "activityId": "9007199254740997",
    "petId": "9007199254740995",
    "status": "CLAIMED",
    "serverNow": "2026-09-26T01:00:00Z",
    "result": {"exp": 12, "currency": 20, "actualCurrency": 20}
  }
}
```

星光结算中时 `actualCurrency=0` 且整体不失败；客户端按 `GET /pet/operations/{id}` 查询终态。
完整请求/响应样例见 `api-examples.http`。
