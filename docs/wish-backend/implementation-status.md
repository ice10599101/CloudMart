# 心愿宇宙后端整改实施状态（docs/wish-backend）

> 依据：`.ice/心愿宇宙后端审计与改造开发任务书.md` v1.0（2026-09-26）
> 状态口径：任务书 §1.1 证据等级 + §25 输出协议。未执行的验证一律标记 NOT RUN。

## 1. 本轮已交付（对应任务书 W0 安全止血 + 阶段 0）

### B01 隔离用户/管理员/服务身份，封堵内部接口绕行 — 已完成（服务端+网关）

| 项 | 实现 | 证据 |
| --- | --- | --- |
| 网关阻断 | 新增 `WishInternalRouteBlockFilter`（order=HIGHEST+500，先于身份注入与路由）：外部 `/api/wish/admin/**`、`/api/wish/internal/**` 一律 404；路径规范化含百分号解码、矩阵参数剥离、点段拒绝、重复/尾部斜杠折叠、大小写归一 | `mall-gateway/.../filter/WishInternalRouteBlockFilter.java`；`WishInternalRouteBlockFilterTest` 30 用例 |
| 网关提权回退移除 | `AdminAuthGlobalFilter` 删除未验签 `extractScopeFromJwt` 回退：管理员角色只来自验签成功后注入的 `X-Admin-Role` | `AdminAuthGlobalFilter.java` |
| 用户身份直验 | 新增 `WishJwtAuthenticationFilter`：Bearer JWT（RS256，mall-auth JWKS，kid 匹配+exp）建立 `ROLE_USER`；`X-User-Id` 不再作为身份源 | `mall-wish/.../config/WishJwtAuthenticationFilter.java`；测试 5 用例 |
| 服务身份 | 新增 `ServiceTokenAuthenticationFilter`：`X-Service-Token`（HS256 短效令牌）按路径强校验 `iss/aud/scope`（/admin→mall-admin+wish:admin；/internal/jobs、/internal/tree-env→mall-job+wish:jobs；/internal/pet-support→mall-pet+wish:pet）建立 `ROLE_INTERNAL` | `ServiceTokenAuthenticationFilter.java`；测试 6 用例 |
| 删除头即身份 | 删除 `InternalCallAuthenticationFilter`；用户令牌绝不可能获得 INTERNAL 角色 | SecurityConfig 重写 |
| 令牌编解码 | `ServiceTokenCodec`（mall-common，纯 JDK HMAC-SHA256，常量时间比较，密钥≥32 字节，TTL≤300s）+ `ServiceTokenSigner` | `ServiceTokenCodecTest` 8 用例 |
| 调用方接入 | mall-admin 三个 wish FeignClient、mall-pet WishFeignClient 挂 `WishServiceTokenConfig` 签发拦截器；mall-job `BusinessJobHandler` 全部 20 处 mall-wish 调用加 `X-Service-Token` | 各服务 config/feign 变更 |
| 密钥注入 | `WISH_SERVICE_TOKEN_SECRET` 环境变量，四个服务启动 fail-fast 校验（≥32 字节），不落仓库；IT 用隔离测试密钥 | `WishSecurityProperties`；`application-it.yml` |

**部署要求（上线前必须执行）**：为 mall-wish / mall-admin / mall-job / mall-pet 注入同一 `WISH_SERVICE_TOKEN_SECRET`（≥32 字节随机值，部署密钥管理下发）。未注入时服务拒绝启动——这是预期行为（fail closed）。

### B02 统一心愿及其子资源访问控制 — 核心完成（capabilities/管理端最小元数据 → W1）

- 新增 `com.cloudmart.wish.policy.WishAccessPolicy`（任务书 4.1 谓词唯一实现）：`isOwner/isPublicReadable/isReadableBy/requireReadable/requireOwner/requireInteractable/canReadDiary`；PRIVATE/TREE_HOLE/软删/下架对非作者统一 404 语义；DIARY 永远仅作者。
- 接入点：`getWishProgress`、`listGrowthTimeline`（新增 viewer 参数，匿名 null）、`getWishDetail`（DIARY 解密前过滤）、`WishCollectionServiceImpl.collect/listCollections`（私密不可收藏；已隐藏心愿列表不回显内容）、`GiftServiceImpl.sendGift/listTargetRecords`（目标必须公开可读；场景记录防反查）、`CollectionServiceImpl.collectSpark`（作者专用）、`InteractionServiceImpl`、`FulfillmentServiceImpl` 私有可见性副本全部收敛到策略。
- 测试：`WishAccessPolicyTest`（8 用例覆盖 4.1 矩阵）、`WishServiceImplTest` 新增子资源越权/DIARY 过滤用例、`FulfillmentServiceImplTest` 新增 B03 用例、`GiftServiceImplTest` 夹具升级至新基线。

### B03 还愿向社区传播显式授权，阻断日记外发 — 核心完成（社区侧去重/持久化隐藏任务 → W1/B13）

- `SubmitFulfillmentRequest` 增加 `shareToCommunity`（默认 false；旧 3 参构造兼容）。
- PRIVATE/TREE_HOLE 携带 true → 422（`WISH_VALIDATION_ERROR`）。
- `wish_fulfillment` 增加 `share_to_community/share_consent_at/content_version/share_revoked_at`（迁移 `V41__wish_fulfillment_share_consent.sql`，可空/有默认，Expand 安全）。
- 默认零传播：未授权还愿不再注册 afterCommit 流转。
- 投递执行前复核：`isFlowStillAuthorized` 校验当前 PUBLIC 可读 + 授权未撤销，不满足置 HIDDEN 不发布（防"入队后转私密"）。
- `buildLegacyPostPayload` 移除成长记录时间轴与进度拼接——不再抓取、不再解密 DIARY，社区投影仅含标题/还愿故事/选定媒体。

### B07 公开缓存可见性失效闭环 — 核心完成（世界树/直播挂件核查通过；事件化失效 → W1）

- `HomeServiceImpl`：热门缓存 ZSet 只存候选 ID+分值；命中后批量回查并重新执行公共可见谓词，不可见剔除；DB 回查失败按空处理（隐私 Fail-Closed）。
- `NearbyWishServiceImpl`：nearby/cluster 缓存改存候选 ID；命中后回查重组 VO（距离/模糊坐标重算），转私密/下架/删除的心愿不再从旧缓存泄露标题与坐标。
- 世界树聚合缓存仅含聚合计数；直播挂件查询即带 PUBLIC+APPROVED+isVisible 谓词（TTL 10s）——核查无需改造。

## 2. 验证证据（Validation Evidence）

```text
编译：mall-common/mall-gateway/mall-wish/mall-admin/mall-job/mall-pet 全部 PASS（mvn -o compile，JDK 26.0.1）
单元/切片测试（-Djacoco.skip=true，与审计基线同口径）：
  - 审计基线 9 类回归：163 tests, 0 failures, 0 errors → PASS
  - 本轮含新增用例合计：188 tests（wish）+ 8（common codec）+ 30（gateway block）→ PASS
  - gateway：JwtAuthenticationFilterTest 等既有用例与新增阻断用例 → PASS
lint / typecheck：NOT RUN（Java 后端以编译+测试为准）
集成测试（真实网关+HTTP+MySQL+Redis+MQ）：NOT RUN —— 按任务书 B23 须先完成 UT/IT 分组与隔离库保护
安全矩阵联调（T01–T05 端到端）：NOT RUN —— 需隔离环境
```

## 3. 已知遗留与风险（按任务书编号）

1. **部署依赖**：`WISH_SERVICE_TOKEN_SECRET` 未注入则四服务无法启动（有意设计）。滚动发布期间，旧版调用方未带服务令牌访问新版 mall-wish 的内部端点会 401——四个服务需同批次发布。
2. **IT 套件适配（W1）**：`it/` 集成测试中直连 `/admin/**`、`/internal/**` 的用例需改为携带服务令牌（测试密钥已在 `application-it.yml` 预置）。
3. **B02 余量（W1）**：详情 VO 的 `capabilities` 字段、管理端列表私密内容最小元数据、敏感正文受控查看（`business:wishPrivacy:read`）未实现。
4. **B03 余量（W1/B13）**：社区侧 `sourceType+sourceId+contentVersion` 去重、持久化隐藏任务（outbox）未建；`hideFlow` 下游失败仍只记日志。当前默认关闭传播 + 投递前复核已阻断泄露面。
5. **B01 余量（W1）**：mall-auth 统一签发/密钥轮换（当前为各调用方持共享密钥自签，安全边界等价，轮换需多服务同步换钥）；服务间调用传递 actorId/requestId 的签名通道未加。
6. **发现的新问题（High，与 B 编号无关）**：`mall-pet/feign/WishFeignClient#batchGetUsers` 调用 `mall-wish /users/batch`，mall-wish 无该端点且 mall-user 才是正确归属——宠物对战主人昵称解析必然失败进 fallback。建议 W2 修复（迁移到 mall-user 客户端）。
7. **发现的新问题（Medium）**：成长时间轴 DIARY 过滤发生在 limit 之后，非作者分页可能出现短页/漏后页（安全优先）；W1 重构分页时一并修。


## 4. 第二轮交付（W1 交易基础主体 + 缺陷修复，2026-09-27）

### 顺手修复的开发中发现问题

| 级别 | 问题 | 修复 | 验证 |
| --- | --- | --- | --- |
| High | mall-pet 6 处主人昵称解析经 `wishFeignClient.batchGetUsers` 调 mall-wish `/users/batch`（wish 无此端点，一直走降级占位） | 新建 `mall-pet/feign/UserFeignClient`（name=mall-user, GET /users/batch）+ fallback；7 个服务实现与测试装配迁移；wish 客户端删除该方法 | PetVisitServiceImplTest 5/5 PASS；mall-pet 编译 PASS |
| Medium | 成长时间轴 DIARY 后过滤破坏分页口径（limit 后过滤 → 短页/漏后页） | 过滤下推 SQL：非作者查询 `.ne(type, DIARY)`，hasMore/limit 口径恢复正确；单测改为断言查询条件含 DIARY 排除 | WishServiceImplTest 37/37 PASS |

### B04 写操作持久幂等 — 基础设施 + 首个接入点完成

- 迁移 `V42__wish_operation_and_asset_stock.sql`：`wish_operation`（§6.2 目标结构：actor 作用域 + ascii request_key + SHA-256 request_hash + COMPLETED 结果 JSON，仅成功落库）；`wish_pet_operation` 兼容保留。
- `WishOperationExecutor`：事务内插唯一键 → 冲突读已提交视图：同键同摘要重放、同键异摘要 409 `IDEMPOTENCY_KEY_REUSED`、无已提交行 409 `WISH_OPERATION_IN_PROGRESS`（可按原键重试）；业务失败整体回滚不留"已成功"凭证；无调用方事务时自建事务。
- 已接入：**资产兑换**（`ASSET_EXCHANGE`，键=X-Idempotency-Key，摘要=assetId+paymentMethod）。
- 测试：`WishOperationExecutorTest` 7 用例（重放/冲突/进行中/失败不落凭证/actor 作用域隔离）。

### B05 兑换余额与库存模型 — 完成

- 删除 `credited < cost` 错误二次判断（spendStarlight 返回扣款后余额）；余额不足由钱包统一 402。
- `wish_virtual_asset` 增加 `stock_mode/stock_remaining/version`；LIMITED 在 DB 执行 `stock_remaining>0` 条件减一，与钱包、归属、操作凭证同事务；Redis 库存降级为可删除展示缓存（不再预扣）。
- 校验补齐：validFrom（未开售）、validTo、上架状态、支付白名单（仅 STARLIGHT；RMB 通道关闭）、已拥有拒绝。
- 响应改为 `ExchangeResultVO(id, assetId, balanceAfter, spentAmount)`（API 形态变更，前端 W5 适配）。
- 测试：`CollectionExchangeTest` 9 用例 —— 100/80→余额20、80/80→余额0、79/80→402 无半成功、库存 0 售罄不扣款（先库存后钱包）、同键重试不二次扣款/扣库存（T09/T08 语义）。

### B06 衰减与对账 — 完成

- 衰减：逐用户短事务 + 条件 UPDATE 复核（不活跃+余额>10），affected=1 才写 DECAY 流水（余额快照为扣减后值）；业务唯一键 `DECAY:{userId}:{platformDate}`（Asia/Shanghai 平台日界）经 wish_operation 兜底——同日重复调度/多实例重放原结果。
- 对账：CHECK_ONLY——只比对余额与流水求和、输出差异工单（日志承载 ID+差额），**不再直接改余额**；修复须走 N02 专属权限工单。
- 测试：`MaintenanceStarlightTest` 5 用例（affected=0 无假流水、同日重复只扣一次、差异只读、一致无写，T10/T11 语义）。

### 验证证据（第二轮）

```text
编译：六模块全量 PASS
单元测试：mall-wish 227 + mall-gateway 30 + mall-pet 5 全部 PASS（含 W0 全部存量用例）
集成/并发（真实库）：NOT RUN（同前，受 B23 前置约束）
```

### B04 余量（下一增量）

1. 送礼（GIFT_SEND）、还愿（WISH_FULFILL）、打卡（WISH_CHECKIN）、活动领取接入 executor（兑换已作为参照实现；这些路径已有部分 DB 唯一键幂等，但尚无统一操作凭证）。
2. `CFG/IdempotencyFilter` 旧 Redis 过滤器降级重写（作用域键=方法+路径+query+actor、409 进行中、Redis 故障仅跳过加速层）——当前旧过滤器仍在运行，其"跨端点结果串用/processing 解析"缺陷待修。
3. operation 结果 VO 的 `duplicate` 标记与查询接口 `GET /v2/operations/{operationId}`（§5.3）。

## 6. 第三轮交付（B04 收口 + B13 基础，2026-09-27）

### B04 写操作持久幂等 — 主体收口
- 旧 `IdempotencyFilter` 重写为纯加速层：作用域键与摘要覆盖 method+path+规范化 query+body（修复跨端点结果串用与仅 body 摘要）；processing 状态改统一 JSON 存储（修复"处理中请求被误判 409 并把哈希当响应体重放"）；处理中返回 `WISH_OPERATION_IN_PROGRESS`（与键复用区分）；过滤器排在 Security 链之后（先认证、再查幂等）；仅 2xx 缓存 24h，失败删键可原键重试；Redis 故障仅跳过加速层。
- executor 接入铺开：兑换 `ASSET_EXCHANGE`、送礼 `GIFT_SEND`、还愿 `WISH_FULFILL`、打卡 `WISH_CHECKIN`；四个写入口控制器读取 `X-Idempotency-Key`（可空=单次请求保护）。

### B13 事件可靠投递 — 基础设施
- 迁移 `V43__wish_outbox_inbox.sql`：`wish_outbox`（eventId PK、聚合版本、租约、退避、DEAD）+ `wish_event_inbox`（consumer_name+event_id 主键去重）。
- `WishOutboxService`：publish 于调用方事务内落 PENDING（业务事实与事件原子提交）；`@Scheduled` 中继按租约条件 UPDATE 抢占（多实例互斥），退避 1s/5s/30s/2min/10min，超 10 次置 DEAD 告警，投递成功置 PUBLISHED。
- 首个接入：还愿成功与 `WishFulfilled` 事件同事务（payload 仅 ID/状态，无正文）。
- 余量（下一增量）：消费端 `WishStatSyncConsumer` 接入 inbox 去重（totalHelped 重复累计修复）；`WishVisibilityChanged` 等其余首批事件接线；签到/活动跨服务经验发送迁移 outbox。

### 验证证据（第三轮）
```text
编译 + test-compile：PASS
单元测试：mall-wish 227 + mall-gateway 30 全部 PASS
集成/并发（真实库）：NOT RUN（B23 前置约束不变）
```

## 7. 第四轮交付（W2 核心正确性第一批，2026-09-27）

### B13 收口：消费端去重
- `HelpedEventMessage` 增加 eventId（兼容旧在途消息）；`publishHelpedEvent` 改走 outbox（事实与事件同事务，不再"发送失败仅记日志"）；中继按事件类型映射既有 tag（HelpedRecorded→wish-stat-sync）。
- `WishEventInboxService.tryConsume`：去重行与 `incrementTotalHelped` 同事务——重复投递只累加一次；副作用回滚时去重行一并回滚，消息可安全重投。

### B14：收藏馆
- 指定 type=SKIN 等不再无条件查徽章（修复 NPE）；非法类型返回 422。
- 余量：星火收藏迁移至 wish_collection 统一模型（需数据迁移，W2 后续）。

### B08：心愿编辑字段级 CAS
- V44：`wish.version`。UpdateWishRequest 必填 version；`update` 条件更新 `WHERE id AND version` 命中才 SET，`version+1`；未命中 409 `WISH_VERSION_CONFLICT`。
- 只 SET 可编辑字段——互动计数/审核状态/果实类型不在更新列（修复覆盖并发点亮/审核）；缺失字段不更新。
- 可见性切换联动：PUBLIC→PRIVATE/TREE_HOLE 显式清除 geohash；进入/离开 TREE_HOLE 同步 enableAiReply/auditStrategy/triggerEnvEmo；重新公开必须携带新坐标（否则 422）；PRIVATE→PUBLIC 首次固化树坐标；可见性变更与 `WishVisibilityChanged` 事件同事务。

### B09：打卡连续天数与 mood
- V44：`wish_progress.last_checkin_date`。连续天数按"last_checkin_date==昨天 → +1，否则 → 1"判定（修复盲 +1）；maxStreak 取最大；mood 此前接收未保存——已落库并校验打卡正文 ≤200。
- 余量：用户级去重日事实表（totalCheckinDays 去重口径）与时区口径统一（W2 后续）。

### B10：删除统计一次
- deleteWish 改条件软删：仅删除时心愿在活跃集合（ACTIVE/OVERDUE/FULFILLING）才 `decrementOnWishDeleted`；并发流转（恰好还愿）下条件删除未命中则按非活跃口径重删、不扣统计（已还愿心愿再删不再误减）；删除事实与 `WishDeleted` 事件同事务。
- 余量：后台删除共用同一命令入口（WishCommandService 归一，W2 后续）；到期扫描按实际成功对象计数。

### 验证证据（第四轮）
```text
编译：六模块 PASS
单元测试：mall-wish 228 + mall-gateway 30 全部 PASS
新增用例：B10 删除已还愿心愿不扣统计；B08 可见性切换/坐标固化/不触碰计数控（wrapper SQL Set 断言）
集成/并发（真实库）：NOT RUN
```

## 8. 第五轮交付（W2 第二批：B15/B17/B24，2026-09-27）

### B15 公开列表分页漏项
- V1 `listWishes` 暂停"置顶混合排序"：首页不再 `isTop DESC` 混排、翻页不再排除置顶项——消除"置顶数≥pageSize 或旧置顶导致普通心愿被跳过"的漏项；V1 保持 `(created_at,id)` 全序稳定分页。V2 签名游标（sortVersion/feedVersion/snapshotAt）随 B22/新接口交付。

### B24 限流与 AI 目标归属
- **废除 SAME_WISH 永久 Redis 排他门闩**：唯一性以 `wish_interaction.uk_interaction_unique`（DB）为事实——`tryAcquireSameWishUnique` 改为事务内 DB 计数判定（0 可同求）；`releaseSameWishUnique` 收敛为 no-op（撤同求删 DB 行即释放）。修复"业务失败/异常残留 Key 把用户永久挡住"与"Redis 故障 Fail-Open 与闭键语义不一致"。
- **AI 目标归属校验**：`createGoals` 校验 wishId 属于本人（否则 404）、sessionId 前缀归属本人（否则 422）——不再直接保存请求传入的 wishId/sessionId。
- 余量：BLESS 按日唯一需 V45（wish_interaction.business_date 列）迁移，随下一批落地。

### B17 漂流瓶隐藏
- `requireBottleViewable` 增加 `isHidden` 判定：隐藏瓶对参与者本人也统一 404——评论读取/添加、互动等所有经此入口的读写全部封堵，不泄露存在性。
- 余量：投/捞配额 DB 条件预占、关联心愿隐私复核（W2 后续）。

### 验证证据（第五轮）
```text
编译：六模块 PASS
单元测试：mall-wish 244 + mall-gateway 30 全部 PASS（限流器测试重写至 B24 新契约）
集成/并发（真实库）：NOT RUN
```

## 9. 第六轮交付（B16 地图完整性，2026-09-27）

- **自适应网格枚举**替代固定 9 邻格：按半径选精度（≤6km→geohash5、≤22km→4、其余→3），枚举包围盒 ±2 环（≤25 格）——修复 20/50km 半径下跨格心愿漏查；候选集上界稳定。
- **坐标校验收紧**（B16/T26）：非有限数（NaN/Infinity）422；lat/lng 只传一个 422；越界 422；0,0 不再被当作"缺失"回退默认城市（合法坐标正常查询）；仅两者都未提供才回退默认城市。
- 距离按本次中心重算与模糊坐标重组已在第三轮 B07 改造中完成（缓存只存 ID）。
- 余量：truncated 标志需要响应包装 VO（API 形态变更，随 V2 契约批次）；独立 locationSharingEnabled 开关与精度降级展示随 N05。

```text
验证：编译 PASS；NearbyWishServiceImplTest+WishServiceImplTest 38 用例 PASS；全量选定回归 PASS
```

## 10. 第七轮交付（B18 活动进度与奖励，2026-09-27）

- **审批状态 CAS**：reviewApplication 由"读 PENDING 再 updateById"改为条件 UPDATE `WHERE status=PENDING`——并发双审批只有一个成功、进度只计一次（T20）。
- **进度事实化**：参与/审批通过时 `activity.progress_counter` DB 事实列 +1（与领域写同事务）；`getProgress` 直读 DB，Redis 不再参与读判定——清 Redis 不丢进度。
- **奖励类型独立**：issueRewards 中星光已发不再 `continue` 跳过同用户徽章——每奖励类型独立判定、独立入账（修复"星光重复时跳过同用户其他奖励"）。
- 余量：批奖励拆 job/recipient item 短事务 + 后台 jobId 查询属 N02 任务工作台范围；当前为单人短事务逐人发放（tryIssue 唯一键幂等已有）。

```text
验证：编译 PASS；活动相关选定回归 PASS
```

## 11. 第八轮交付（W3：B11 + B12，2026-09-27）

### B11 审核、下架、恢复与删除治理记录
- V45 迁移：`wish.reject_reason`。**驳回原因必填（422）并随决定落库**；恢复上架（APPROVED）时清空。
- **审核 CAS**：条件更新 `WHERE audit_status=旧值`——两名管理员并发审核只有一个成功，未命中 409 提示刷新（不再整实体 updateById 覆盖）。
- **恢复语义**：`updateVisibility(visible=true)` 对 REJECTED/AUTO_HIDDEN 内容拒绝（422）——恢复必须走审核接口改审核状态，不能只翻 isVisible；上下架本身改 CAS（并发单成功）。
- **后台删除与用户侧同口径**：活跃集合才扣 activeWishes（修复后台删除误减其他心愿统计）；治理动作（WishModerated/WishVisibilityChanged/WishDeleted）与领域写同事务落 outbox，通知作者经消费端（原 TODO 注释消除）。
- 操作者 ID 经管理代理透传头记录进日志与事件（不上传敏感正文）。

### B12 富文本与附件服务端信任边界
- 新增 `WishContentSanitizer.sanitizeRichText`（白名单净化，无第三方依赖）：script/style/iframe/object/embed/svg/math 整块移除；白名单外标签剥壳保留内文；on* 事件属性剥离；javascript:/vbscript:/data: URL 拦截。接入心愿描述（create/update）与还愿故事（此前仅路径穿越检查）。
- `isAllowedMediaUrl` 附件白名单：仅 http/https/oss 内部存储域、单元素 ≤500 字符；还愿提交逐项校验。
- 与前端 DOMPurify 构成纵深——服务端不再信任"前端已消毒"。
- 余量：私密附件短时签名下载（依赖 mall-file 鉴权下载接口，W4 联动）；成长记录 DTO @Valid 补齐随 N01 批次。

```text
验证：编译 PASS；单元测试 64（净化器 7/审核治理/还愿故事净化）+ 全量选定回归 PASS
```

## 12. 第九轮交付（W3 收口：N01 治理工单，2026-09-27）

### N01 举报、申诉与统一治理工作台
- **V46 迁移四表**：`wish_moderation_case`（活动 case 生成列唯一键：同一内容仅一个活动工单）、`wish_moderation_decision`（追加写审计：before/after 状态快照、操作者、requestId）、`wish_report`（dedup_key 活动举报键唯一，结案置空释放）、`wish_appeal`（同决定同申诉人唯一，7 日窗口）。
- **举报（用户）**：POST /v2/reports——仅可举报有权看到的内容（WISH 强校验，防私密探测）；OTHER 必填说明；每日 10 次有效配额（429）；同内容同理由未结举报合并返回原记录；并发同键唯一约束兜底。
- **我的举报/我的申诉**：GET /v2/my/reports、/v2/my/appeals（cursor 分页；举报人看不到运营内部备注）。
- **申诉（用户）**：POST /v2/moderation-decisions/{id}/appeals——仅被处理作者（WISH 校验心愿归属）；7 日窗口（422）；同决定同作者一条（唯一键）；证据 ≤3。
- **治理决定（管理）**：POST /admin/moderation/cases/{id}/decisions——NO_ACTION/HIDE/RESTORE；HIDE/RESTORE 必填原因（422）；**version CAS 防双审核员覆盖**；决定追加写；HIDE/RESTORE 落到心愿状态（is_visible/audit_status）并与 WishVisibilityChanged 事件同事务；关联举报结案释放 dedup 唯一。
- **申诉复核（管理）**：POST /admin/moderation/appeals/{id}/decisions——复核人不得为原决定处理人（403）；**通过恢复前检查其他生效下架原因**（存在其他活动 case 不自动恢复）。
- **mall-admin 代理**：ModerationFeignClient（服务令牌）+ AdminModerationController（/wish/moderation/**，权限码 business:wishModeration:list/audit、business:wishAppeal:review）；降级抛 503 不静默失败。
- 余量：权限菜单/角色种子 SQL（mall-admin 管理页配置）；管理前端页面（W5 契约批次）；高危词临时隐藏规则引擎。

```text
验证：编译 PASS；ModerationServiceTest 9 用例（合并/配额/OTHER 必填/CAS/复核回避/窗口/不越权恢复）PASS；
     全量选定回归 263 用例 PASS
```

## 13. 第十轮交付（W4：B21 + B19，2026-09-27）

### B21 加密 envelope 与 fail-closed
- **v2 envelope**：`enc:v2:<keyId>:<base64(iv||ct)>`，GCM AAD 绑定记录上下文（DIARY 绑定 `GROWTH:wishId:userId`，树洞/AI 会话走 LEGACY 域）——密文挪用被 AAD 校验拦截（测试锁定）。
- **轮换**：当前 keyId 写、当前+previous 双钥读（回填验收后移除 previous）；v1 历史密文按当前密钥兼容读取。
- **解密失败 → `WISH_CONTENT_UNAVAILABLE`**：不再把密文当正文返回（旧行为原样返回密文的缺陷修复）。
- **fail-closed**：`wish.crypto.require-key=true`（生产）时密钥缺失拒绝启动、加密入口抛异常回滚写操作——绝不降级明文；开发/测试显式 false 与 prod 隔离。
- 调用点全迁移（DIARY 加密/解密 4 处携带 AAD）。

### B19 导出任务
- **恢复 PROCESSING**：启动恢复不再只扫 PENDING——宕机残留的 PROCESSING 一并重新入队。
- **配额**：每用户同时仅一个进行中任务（409）；24 小时内最多 2 次（429）。
- **真实清理**：过期内容改显式 `SET content=NULL`（修复 `updateById` 空字段不落库导致 DB 内容残留的缺陷）；状态视图 `@JsonIgnore` 脱敏 content——状态查询不再暴露导出正文。
- 余量：持久租约与多实例接管（单实例内恢复已闭环）；流式分页导出与私有对象存储（W5 与 mall-file 联动）。

```text
验证：编译 PASS；ContentCipherTest 8 用例（往返/AAD 挪用拦截/轮换双钥/v1 兼容/篡改拒绝/fail-closed）PASS；
     选定回归 PASS
```

## 14. 第十一轮交付（W4：B20 + N05，2026-09-27）

### B20 注销闭环（wish 侧）
- **发码真实化**：`sendDeletionCode` 返回 `SendCodeResult(sent, echoCode, message)`——验证码下发通道（短信/邮件）未接入时 **sent=false + 明确提示**，控制器不再固定 sent=true 假成功；echo-code 回显仅限开发/测试（生产必须关闭）。真实短信通道接入属 mall-notification 能力扩展（当前仅站内信），接口点已预留。
- **取消 CAS**：仅 `status=PENDING 且未过截止` 可取消——与到期执行并发时只有一个成功（409 提示刷新）。
- **执行认领与回退**：到期执行先 CAS `PENDING→EXECUTING` 认领（多实例/重复调度单执行者），清理后 `EXECUTING→EXECUTED`；异常回退 PENDING 下轮重试（软删幂等）。
- 余量：全账号注销统一编排（mall-user 主导 + 各服务进度可查）为跨服务工作包；终态账号令牌失效在 mall-auth 侧（B20 联动项）。

### N05 统一隐私中心
- **GET /v2/my/privacy**：聚合 AI 数据处理授权（复用 consent，不新造开关）、最近导出任务进度、注销阶段、默认关闭项（位置共享/还愿自动分享/RMB 支付均 false）——各端隐私开关的单一数据源。
- 余量：位置共享独立开关 `locationSharingEnabled` 持久化与附近模式退出清轨迹（随 B16 收尾批次）。

```text
验证：编译 PASS；选定回归 146 用例 PASS
```

## 15. 第十二轮交付（收尾：B23 测试基线 + B22 契约，2026-09-27）

### B23 迁移与测试基线
- **取消启动自动 repair**：删除 `FlywayRepairConfig`（不再以 repair 掩盖历史 checksum 差异）；`validate-on-migrate: true`——正式环境先 validate 再 migrate，差异须以新迁移修正。
- **UT/IT 分组**：surefire 排除 `**/it/**`（修复"IT 类名匹配默认 includes、全量 mvn test 会连远程库"的危险配置）；failsafe 绑定 `**/it/*IntegrationTest.java` 显式执行。
- **IT 环境守卫**：`WishIntegrationTestBase` 增加前置校验——必须 `-Dwish.it.enabled=true` 且显式提供 `WISH_IT_MYSQL_HOST`（缺任一拒绝执行）；`application-it.yml` 移除远程实例默认地址（129.204.152.168），杜绝误连共享库。
- 余量：覆盖率 agent 启动根因修复（JaCoCo 0.8.14 + JDK26，当前 CI 以 -Djacoco.skip 规避）；真实网关+容器化 IT 套件编写（W5）。

### B22 契约文档
- `openapi.yaml`：对外核心路径骨架（wishes CRUD/fulfillment/exchange/reports/appeals/privacy）+ 统一信封 + ID 字符串约定 + 错误语义；SDK 生成与三端薄适配层随 W5 前端批次。
- `api-examples.http`：占位令牌与隔离测试 ID 的成功/错误示例（版本冲突/兑换/举报/申诉/隐私中心）。

```text
验证：编译 PASS；全量选定回归 271 用例 PASS（12 轮累计新增约 100 用例）
IT：按 B23 分组后不在默认 mvn test 中执行；执行需显式启用 + 本地隔离库
```

## 16. 交付收尾总览（截至第十二轮）

| 工作包 | 任务 | 状态 |
| --- | --- | --- |
| W0 安全止血 | B01（完整）/B02/B03/B07 | ✅ |
| W1 交易基础 | B04（executor+四入口+过滤器重写）/B05/B06/B13（outbox+inbox 消费去重） | ✅ |
| W2 核心正确性 | B08/B09/B10/B14/B15/B16/B17/B18/B24 | ✅ |
| W3 治理闭环 | B11/B12/N01（V46 四表+端点+代理） | ✅ |
| W4 异步与数据控制 | B19/B21/B20（wish 侧）/N05 | ✅ |
| W5 契约与发布 | B22（openapi/examples/错误码）/B23（迁移+UT/IT 分组+IT 守卫） | ✅ 后端部分；SDK 生成+三端前端适配待前端批次 |
| P2 增强 | N03/N04 | ⬜ 未开始（独立发布，不阻塞安全修复） |
| 独立缺陷 | pet /users/batch 错误归属、DIARY 分页口径 | ✅ 已修 |

**部署门槛（不变）**：四服务同批注入 `WISH_SERVICE_TOKEN_SECRET`；`WISH_CRYPTO_REQUIRE_KEY=true` + `WISH_CRYPTO_KEY`；Flyway V41–V46 先于代码执行；禁止回滚到含 B01 漏洞的版本。
## 17. 第十三轮交付（P2：N03 + N04，2026-09-27）

### N03 心愿草稿与发布
- V47 迁移：`wish_draft`（client_draft_id 幂等唯一键、published_wish_id 发布关联、version 乐观锁、软删）。
- `WishDraftService`：保存（clientDraftId 幂等 + 乐观锁自动保存 + 每人 20 份上限）、列表（仅本人）、删除（CAS）、**发布复用 createWish 领域命令**并同事务关联 publishedWishId——同草稿仅首次发布、重复调用返回既有心愿（不重复发奖/计统计）。
- 端点：POST/GET/PATCH/DELETE `/v2/drafts`、POST `/v2/drafts/{id}/publish`。
- 归档/延期（archivedFromStatus/reschedule）依赖 B10 状态机扩展，随 V2 端点批次交付（草稿-发布主链路已闭环）。

### N04 AI 目标计划化
- V47：`wish_ai_goal` 增加 `sort_order/version`。
- `GoalPlanService`：清单（作者专用）、创建（每心愿 ≤20 步；用户可直接建，AI 失败不阻塞）、编辑/勾选（version CAS；勾选完成**不触发还愿奖励**）、删除（软删+CAS）、批量排序（集合必须完整且同心愿）。
- 端点：GET/POST `/v2/wishes/{id}/goals`、PATCH/DELETE `/v2/goals/{id}`、PUT `/v2/wishes/{id}/goal-order`。

```text
验证：编译 PASS；全量选定回归 271 用例 PASS
```

## 5. 建议下一步

按任务书 §12.2 顺序：`wish-privacy-policy`（本轮已完成主体）→ `wish-operation-wallet`（B04–B06，operation/outbox 迁移 V42+）→ `wish-events-tasks`（B13）。
