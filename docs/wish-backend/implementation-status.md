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

## 4. 建议下一步

按任务书 §12.2 顺序：`wish-privacy-policy`（本轮已完成主体）→ `wish-operation-wallet`（B04–B06，operation/outbox 迁移 V42+）→ `wish-events-tasks`（B13）。
