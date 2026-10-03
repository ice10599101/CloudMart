# 宠物后端 → 四端前端 功能覆盖对照表

> 生成：2026-10-02 ｜ 后端 mall-pet：C 端控制器 **27** 个 / 端点 **138** 个（Admin 控制器 10 个不计入）。
> 判定：后端路径（参数位通配）在前端源码中正则匹配即视为已接入。四端 = Web(umi) / 小程序+H5(Taro) / App(Expo)。

## 总结论

- 138 个端点中，**133 个已全部接入且有 UI**；
- 未覆盖 5 个，均属合理缺失：
  1. `GET /battle/pending`（待应战专用查询）——Taro/Expo 用对战历史过滤 PENDING 实现了同一 UI，接口冗余；
  2. `GET /operations/{requestKey}` —— 幂等意图键的状态查询，属于请求层机制配套，非独立 UI 功能；
  3-5. `/admin/pet/config-governance/*` 3 个 —— 管理后台的配置治理（校验/历史/回滚），Web 管理端覆盖，移动 C 端合理不做。

## 逐端点对照

| 控制器(功能域) | 方法 | 端点 | Web | 小程序/H5 | App |
|---|---|---|---|---|---|
| Pet（核心(领养/档案/外观)） | GET | `/me` | ✅ | ✅ | ✅ |
| Pet（核心(领养/档案/外观)） | POST | `/create` | ✅ | ✅ | ✅ |
| Pet（核心(领养/档案/外观)） | PUT | `/name` | ✅ | ✅ | ✅ |
| Pet（核心(领养/档案/外观)） | PUT | `/owner-title` | ✅ | ✅ | ✅ |
| Pet（核心(领养/档案/外观)） | PUT | `/appearance` | ✅ | ✅ | ✅ |
| Pet（核心(领养/档案/外观)） | PUT | `/privacy` | ✅ | ✅ | ✅ |
| Pet（核心(领养/档案/外观)） | GET | `/public/{userId}` | ✅ | ✅ | ✅ |
| Pet（核心(领养/档案/外观)） | GET | `/pets` | ✅ | ✅ | ✅ |
| Pet（核心(领养/档案/外观)） | POST | `/pets/{petId}/activate` | ✅ | ✅ | ✅ |
| PetActivity（限时活动） | GET | `/jobs` | ✅ | ✅ | ✅ |
| PetActivity（限时活动） | POST | `/work/start` | ✅ | ✅ | ✅ |
| PetActivity（限时活动） | POST | `/work/claim` | ✅ | ✅ | ✅ |
| PetActivity（限时活动） | GET | `/activities` | ✅ | ✅ | ✅ |
| PetActivity（限时活动） | POST | `/activities/{activityId}/claim` | ✅ | ✅ | ✅ |
| PetActivity（限时活动） | GET | `/studies` | ✅ | ✅ | ✅ |
| PetActivity（限时活动） | POST | `/study/start` | ✅ | ✅ | ✅ |
| PetActivity（限时活动） | POST | `/study/claim` | ✅ | ✅ | ✅ |
| PetAnniversary（纪念日） | GET | `/me/anniversaries` | ✅ | ✅ | ✅ |
| PetBattle（对战） | GET | `/battle/opponents` | ✅ | ✅ | ✅ |
| PetBattle（对战） | POST | `/battle/challenge` | ✅ | ✅ | ✅ |
| PetBattle（对战） | POST | `/battle/{battleId}/accept` | ✅ | ✅ | ✅ |
| PetBattle（对战） | POST | `/battle/{battleId}/decline` | ✅ | ✅ | ✅ |
| PetBattle（对战） | GET | `/battle/{battleId}` | ✅ | ✅ | ✅ |
| PetBattle（对战） | GET | `/battle/pending` | ✅ | ❌ | ❌ |
| PetBattle（对战） | GET | `/battle/history` | ✅ | ✅ | ✅ |
| PetBlockReport（屏蔽·举报） | POST | `/blocks/{blockedUserId}` | ✅ | ✅ | ✅ |
| PetBlockReport（屏蔽·举报） | DELETE | `/blocks/{blockedUserId}` | ✅ | ✅ | ✅ |
| PetBlockReport（屏蔽·举报） | GET | `/blocks` | ✅ | ✅ | ✅ |
| PetBlockReport（屏蔽·举报） | POST | `/reports` | ✅ | ✅ | ✅ |
| PetBottle（漂流瓶） | GET | `/bottle/status` | ✅ | ✅ | ✅ |
| PetBottle（漂流瓶） | POST | `/bottle/start` | ✅ | ✅ | ✅ |
| PetBottle（漂流瓶） | POST | `/bottle/claim` | ✅ | ✅ | ✅ |
| PetCareer（职业生涯） | GET | `/career` | ✅ | ✅ | ✅ |
| PetCareer（职业生涯） | POST | `/career/apply` | ✅ | ✅ | ✅ |
| PetCareer（职业生涯） | POST | `/career/work/start` | ✅ | ✅ | ✅ |
| PetCareer（职业生涯） | POST | `/career/work/claim` | ✅ | ✅ | ✅ |
| PetCareer（职业生涯） | POST | `/career/promote` | ✅ | ✅ | ✅ |
| PetChat（AI 聊天） | POST | `/chat` | ✅ | ✅ | ✅ |
| PetChat（AI 聊天） | GET | `/chat/persona` | ✅ | ✅ | ✅ |
| PetChat（AI 聊天） | GET | `/chat/history` | ✅ | ✅ | ✅ |
| PetCommunity（社区成长(签到/等级)） | GET | `/rankings/season` | ✅ | ✅ | ✅ |
| PetCommunity（社区成长(签到/等级)） | GET | `/rankings/season/history` | ✅ | ✅ | ✅ |
| PetCommunity（社区成长(签到/等级)） | GET | `/rankings` | ✅ | ✅ | ✅ |
| PetCommunity（社区成长(签到/等级)） | GET | `/share/card` | ✅ | ✅ | ✅ |
| PetCompanionFeature（陪伴(日记/相册/记忆/通知)） | GET | `/pet/onboarding` | ✅ | ✅ | ✅ |
| PetCompanionFeature（陪伴(日记/相册/记忆/通知)） | POST | `/pet/onboarding/skip` | ✅ | ✅ | ✅ |
| PetCompanionFeature（陪伴(日记/相册/记忆/通知)） | GET | `/pet/pets/{petId}/diary` | ✅ | ✅ | ✅ |
| PetCompanionFeature（陪伴(日记/相册/记忆/通知)） | POST | `/pet/pets/{petId}/album` | ✅ | ✅ | ✅ |
| PetCompanionFeature（陪伴(日记/相册/记忆/通知)） | DELETE | `/pet/pets/{petId}/album/{assetId}` | ✅ | ✅ | ✅ |
| PetCompanionFeature（陪伴(日记/相册/记忆/通知)） | GET | `/pet/pets/{petId}/memories` | ✅ | ✅ | ✅ |
| PetCompanionFeature（陪伴(日记/相册/记忆/通知)） | PUT | `/pet/pets/{petId}/memories/{memoryId}` | ✅ | ✅ | ✅ |
| PetCompanionFeature（陪伴(日记/相册/记忆/通知)） | DELETE | `/pet/pets/{petId}/memories/{memoryId}` | ✅ | ✅ | ✅ |
| PetCompanionFeature（陪伴(日记/相册/记忆/通知)） | PUT | `/pet/pets/{petId}/memory-settings` | ✅ | ✅ | ✅ |
| PetCompanionFeature（陪伴(日记/相册/记忆/通知)） | GET | `/pet/notify-settings` | ✅ | ✅ | ✅ |
| PetCompanionFeature（陪伴(日记/相册/记忆/通知)） | PUT | `/pet/notify-settings` | ✅ | ✅ | ✅ |
| PetCompanionFeature（陪伴(日记/相册/记忆/通知)） | DELETE | `/pet/pets/{petId}/memories` | ✅ | ✅ | ✅ |
| PetConfigGovernance（配置治理(admin)） | POST | `/admin/pet/config-governance/validate` | ✅ | ❌ | ❌ |
| PetConfigGovernance（配置治理(admin)） | GET | `/admin/pet/config-governance/history` | ✅ | ❌ | ❌ |
| PetConfigGovernance（配置治理(admin)） | POST | `/admin/pet/config-governance/rollback` | ✅ | ❌ | ❌ |
| PetDailyQuest（每日任务） | GET | `/daily-quests` | ✅ | ✅ | ✅ |
| PetDailyQuest（每日任务） | POST | `/daily-quests/{code}/claim` | ✅ | ✅ | ✅ |
| PetDailyQuest（每日任务） | POST | `/daily-quests/claim-all` | ✅ | ✅ | ✅ |
| PetDailyQuest（每日任务） | POST | `/daily-quests/chest/claim` | ✅ | ✅ | ✅ |
| PetEvent（事件流） | GET | `/events` | ✅ | ✅ | ✅ |
| PetEvent（事件流） | POST | `/events/{eventCode}/claim` | ✅ | ✅ | ✅ |
| PetEvolution（进化） | GET | `/evolution` | ✅ | ✅ | ✅ |
| PetEvolution（进化） | POST | `/evolution/evolve` | ✅ | ✅ | ✅ |
| PetFriend（好友） | GET | `/friends/feed` | ✅ | ✅ | ✅ |
| PetFriend（好友） | GET | `/friends/feed/unread-count` | ✅ | ✅ | ✅ |
| PetFriend（好友） | POST | `/friends/feed/read` | ✅ | ✅ | ✅ |
| PetFriend（好友） | GET | `/friends` | ✅ | ✅ | ✅ |
| PetFriend（好友） | POST | `/friends/{userId}` | ✅ | ✅ | ✅ |
| PetFriend（好友） | POST | `/friends/{userId}/accept` | ✅ | ✅ | ✅ |
| PetFriend（好友） | POST | `/friends/{userId}/reject` | ✅ | ✅ | ✅ |
| PetFriend（好友） | DELETE | `/friends/{userId}` | ✅ | ✅ | ✅ |
| PetFriend（好友） | POST | `/friends/{userId}/visit` | ✅ | ✅ | ✅ |
| PetHome（家园布置） | GET | `/home` | ✅ | ✅ | ✅ |
| PetHome（家园布置） | POST | `/home/furniture/buy` | ✅ | ✅ | ✅ |
| PetHome（家园布置） | POST | `/home/furniture/place` | ✅ | ✅ | ✅ |
| PetHome（家园布置） | DELETE | `/home/furniture` | ✅ | ✅ | ✅ |
| PetHome（家园布置） | PUT | `/home/theme` | ✅ | ✅ | ✅ |
| PetHome（家园布置） | PUT | `/home/settings` | ✅ | ✅ | ✅ |
| PetHome（家园布置） | GET | `/home/{petId}` | ✅ | ✅ | ✅ |
| PetHome（家园布置） | POST | `/home/{petId}/like` | ✅ | ✅ | ✅ |
| PetInteraction（互动(喂食/玩耍/清洁/休息)） | POST | `/feed` | ✅ | ✅ | ✅ |
| PetInteraction（互动(喂食/玩耍/清洁/休息)） | POST | `/feed-item` | ✅ | ✅ | ✅ |
| PetInteraction（互动(喂食/玩耍/清洁/休息)） | POST | `/play` | ✅ | ✅ | ✅ |
| PetInteraction（互动(喂食/玩耍/清洁/休息)） | POST | `/clean` | ✅ | ✅ | ✅ |
| PetInteraction（互动(喂食/玩耍/清洁/休息)） | POST | `/rest` | ✅ | ✅ | ✅ |
| PetInteraction（互动(喂食/玩耍/清洁/休息)） | GET | `/pets/{petId}/actions` | ✅ | ✅ | ✅ |
| PetIntimacy（亲密度） | GET | `/intimacy` | ✅ | ✅ | ✅ |
| PetIntimacy（亲密度） | POST | `/companion/heartbeat` | ✅ | ✅ | ✅ |
| PetIntimacy（亲密度） | POST | `/companion/stop` | ✅ | ✅ | ✅ |
| PetOperationQuery（操作状态查询(机制)） | GET | `/operations/{requestKey}` | ❌ | ❌ | ❌ |
| PetPlayFeature（玩法(接球/托管/摘要/合作/图鉴)） | POST | `/pets/{petId}/minigames` | ✅ | ✅ | ✅ |
| PetPlayFeature（玩法(接球/托管/摘要/合作/图鉴)） | POST | `/minigames/{roundId}/ops` | ✅ | ✅ | ✅ |
| PetPlayFeature（玩法(接球/托管/摘要/合作/图鉴)） | POST | `/minigames/{roundId}/settle` | ✅ | ✅ | ✅ |
| PetPlayFeature（玩法(接球/托管/摘要/合作/图鉴)） | GET | `/minigames` | ✅ | ✅ | ✅ |
| PetPlayFeature（玩法(接球/托管/摘要/合作/图鉴)） | POST | `/custody/start` | ✅ | ✅ | ✅ |
| PetPlayFeature（玩法(接球/托管/摘要/合作/图鉴)） | GET | `/custody` | ✅ | ✅ | ✅ |
| PetPlayFeature（玩法(接球/托管/摘要/合作/图鉴)） | GET | `/offline-digest` | ✅ | ✅ | ✅ |
| PetPlayFeature（玩法(接球/托管/摘要/合作/图鉴)） | POST | `/offline-digest/confirm` | ✅ | ✅ | ✅ |
| PetPlayFeature（玩法(接球/托管/摘要/合作/图鉴)） | POST | `/cooperation/{cooperationId}/claim` | ✅ | ✅ | ✅ |
| PetPlayFeature（玩法(接球/托管/摘要/合作/图鉴)） | POST | `/custody/end` | ✅ | ✅ | ✅ |
| PetPlayFeature（玩法(接球/托管/摘要/合作/图鉴)） | POST | `/cooperation` | ✅ | ✅ | ✅ |
| PetPlayFeature（玩法(接球/托管/摘要/合作/图鉴)） | POST | `/cooperation/{cooperationId}/accept` | ✅ | ✅ | ✅ |
| PetPlayFeature（玩法(接球/托管/摘要/合作/图鉴)） | GET | `/cooperation` | ✅ | ✅ | ✅ |
| PetPlayFeature（玩法(接球/托管/摘要/合作/图鉴)） | POST | `/cooperation/{cooperationId}/leave` | ✅ | ✅ | ✅ |
| PetPlayFeature（玩法(接球/托管/摘要/合作/图鉴)） | GET | `/collection` | ✅ | ✅ | ✅ |
| PetPlayFeature（玩法(接球/托管/摘要/合作/图鉴)） | GET | `/collection/stats` | ✅ | ✅ | ✅ |
| PetRelation（关系(情侣/闺蜜/死党)） | GET | `/relations` | ✅ | ✅ | ✅ |
| PetRelation（关系(情侣/闺蜜/死党)） | POST | `/relations/request` | ✅ | ✅ | ✅ |
| PetRelation（关系(情侣/闺蜜/死党)） | POST | `/relations/{id}/accept` | ✅ | ✅ | ✅ |
| PetRelation（关系(情侣/闺蜜/死党)） | POST | `/relations/{id}/reject` | ✅ | ✅ | ✅ |
| PetRelation（关系(情侣/闺蜜/死党)） | POST | `/relations/{id}/dissolve` | ✅ | ✅ | ✅ |
| PetReminder（提醒） | GET | `/reminders` | ✅ | ✅ | ✅ |
| PetReminder（提醒） | GET | `/reminders/unread-count` | ✅ | ✅ | ✅ |
| PetReminder（提醒） | PUT | `/reminders/read-all` | ✅ | ✅ | ✅ |
| PetReminder（提醒） | GET | `/achievements` | ✅ | ✅ | ✅ |
| PetShop（商城） | GET | `/shop` | ✅ | ✅ | ✅ |
| PetShop（商城） | POST | `/shop/buy` | ✅ | ✅ | ✅ |
| PetShop（商城） | GET | `/inventory` | ✅ | ✅ | ✅ |
| PetShop（商城） | POST | `/inventory/equip` | ✅ | ✅ | ✅ |
| PetShop（商城） | POST | `/inventory/unequip` | ✅ | ✅ | ✅ |
| PetShop（商城） | POST | `/inventory/skin` | ✅ | ✅ | ✅ |
| PetShop（商城） | POST | `/inventory/skin/remove` | ✅ | ✅ | ✅ |
| PetShop（商城） | GET | `/inventory/equip-preview` | ✅ | ✅ | ✅ |
| PetSkill（技能） | GET | `/skills` | ✅ | ✅ | ✅ |
| PetSkill（技能） | POST | `/skills/learn` | ✅ | ✅ | ✅ |
| PetVisit（拜访） | GET | `/visit/neighbors` | ✅ | ✅ | ✅ |
| PetVisit（拜访） | POST | `/visit/{petId}` | ✅ | ✅ | ✅ |
| PetWall（留言墙） | GET | `/wall/{petId}` | ✅ | ✅ | ✅ |
| PetWall（留言墙） | POST | `/wall/messages` | ✅ | ✅ | ✅ |
| PetWall（留言墙） | POST | `/wall/messages/reply` | ✅ | ✅ | ✅ |
| PetWall（留言墙） | DELETE | `/wall/messages/{id}` | ✅ | ✅ | ✅ |
| PetWall（留言墙） | POST | `/wall/messages/{id}/like` | ✅ | ✅ | ✅ |
| PetWallet（宠物币钱包） | GET | `/wallet` | ✅ | ✅ | ✅ |
| PetWallet（宠物币钱包） | GET | `/wallet/transactions` | ✅ | ✅ | ✅ |
