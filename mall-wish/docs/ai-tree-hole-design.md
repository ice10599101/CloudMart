# AI 树洞治愈回复设计（Sprint 1.3）
> 实现范围：`com.cloudmart.wish`（TreeHoleService / ConsentService / WishAiController / ConsentController）
> 设计依据：需求文档 2.11（API）、1.2 节 ⑳/㊲c（表结构）、28.1.3 与 30 章（DashScope 方案）、34.2/39.8（合规）

## 1. 调用链路

```
POST /wish/ai/tree-hole
  ├─ 心愿校验：存在 + visibility=TREE_HOLE + enableAiReply=true + 仅作者本人
  ├─ 合规前置：wish_consent 存在有效 AI_DATA_PROCESSING GRANT → 否则 403 WISH_CONSENT_REQUIRED
  ├─ 限频：wish:rate:user:{userId}:ai_tree_hole（10 次/日，用户时区当日 23:59 过期）
  ├─ 危机词检测（wish.ai.crisis-keywords）
  │    └─ 命中 → 本地兜底回复 + 心理援助热线 resources，绝不外发 DashScope
  ├─ PII 脱敏（AiPrivacySanitizer：手机号/邮箱/身份证 → [已隐藏]）
  ├─ DashScope qwen-turbo（同步调用，失败重试 2 次间隔 1s → 仍失败 503）
  ├─ 回复解析（JSON 契约：reply/sentimentScore/resources；非 JSON 纯文本降级）
  └─ 对话持久化（USER + ASSISTANT 同事务写 wish_ai_conversation）
```

## 2. 关键设计决策

| 决策 | 理由 |
|---|---|
| 同步调用（非流式 SSE） | API 契约返回完整 JSON（reply/sentimentScore/resources）；打字机效果由前端对完整回复逐字渲染实现；真 SSE 属体验优化项后续迭代 |
| 树洞 AI 直接集成在 mall-wish（非 Feign 调 mall-ai） | 文档 30.2/30.3 将 Prompt、限频、对话历史均划归 wish 模块（wish-prompt-templates / wish:rate 命名空间 / wish_ai_conversation 表）；mall-ai 定位是电商导购 RAG |
| sentiment_score DB 存 -100~100 整数 | ㊲c 表定义 TINYINT；API 层返回 -1.0~1.0（30.1），Service 层换算 |
| session_id 用普通索引（非 uk） | 文档 uk_ai_session 与"每条记录一个角色"的存储模型矛盾（同会话 USER/ASSISTANT 共享 session_id）；会话逻辑唯一性由业务生成规则 `tree-hole-{wishId}-{userId}` 保证 |
| 危机词本地拦截 | 文档 30.4：高危内容不外发第三方 AI；返回专业话术 + 12356 等热线，sentiment=-1.0 且仍写对话记录 |
| Redis 限频 Fail-Open | 文档 32.4 原则：限频是成本控制优化层；Redis 故障放行仅产生额外 AI 成本，不破坏数据一致性 |
| consentTextHash 占位生成 | 协议文本管理模块未建；客户端可提交 64 位哈希，未提交时服务端按 `type:version` 生成确定性 SHA-256，表结构契约完整保留 |
| JSON 解析失败降级为纯文本 | qwen-turbo 输出不稳定时整段原文作为 reply（空回复比格式错误更伤害情感场景体验） |

## 3. 错误码

| code | HTTP | 触发场景 |
|---|---|---|
| WISH_CONSENT_REQUIRED | 403 | 未同意 AI 数据处理协议 |
| WISH_NOT_AUTHOR | 403 | 非树洞作者本人 |
| WISH_VALIDATION_ERROR | 400 | 心愿非 TREE_HOLE / enableAiReply=false / 非法游标 |
| WISH_NOT_FOUND | 404 | 心愿不存在 |
| WISH_AI_RATE_LIMITED | 429 | 树洞 10 次/日超限 |
| WISH_AI_UNAVAILABLE | 503 | DashScope 重试耗尽 |

## 4. 配置项（application.yml，支持 Nacos 热更新）

```yaml
spring.ai.dashscope:
  api-key: ${DASHSCOPE_API_KEY:sk-placeholder}
  chat.options: { model: qwen-turbo, temperature: 0.8 }
wish.ai:
  tree-hole-daily-limit: 10     # 30.3 限频
  max-retries: 2                # 30.3 重试
  retry-interval-ms: 1000
  crisis-keywords: [自杀, 自残, 轻生, ...]
  hotline-resources: [{type: HOTLINE, title: 全国心理援助热线, url: tel:12356}, ...]
  tree-hole-system-prompt: ...  # JSON 输出契约 Prompt
  crisis-fallback-reply: ...    # 危机兜底话术
```

## 5. API 一览

| Method | Path | 说明 |
|---|---|---|
| POST | /wish/ai/tree-hole | 树洞治愈回复（@Idempotent 10s + Sentinel） |
| GET | /wish/ai/conversations | AI 对话历史（cursor 分页，默认 scene=TREE_HOLE） |
| POST | /wish/my/consents | 提交同意/撤回（uk 幂等） |
| GET | /wish/my/consents?consentType= | 查询同意状态（最新记录判定） |

## 6. 安全与合规清单（文档 30.4 / 39.8）

- [x] 首次 AI 使用前强制 AI_DATA_PROCESSING consent（403 拦截）
- [x] 外发前 PII 脱敏（手机号/邮箱/身份证）
- [x] 危机内容本地拦截不外发第三方
- [x] AI 调用日志仅记录 userId/wishId/耗时/异常摘要，不含完整倾诉内容
- [x] 同意记录含 IP/UA/协议版本/文本哈希（防篡改留痕）
- [ ] 隐私协议中说明 DashScope 数据政策（阿里云承诺不用于训练）→ 前端协议文案待接入

## 7. 遗留与后续

- 流式 SSE 打字机（体验优化，后续 Sprint）
- Prompt 模板独立 Nacos dataId（wish-prompt-templates.json, WISH_GROUP）——当前以 wish.ai.* 配置项热更新等价实现
- 年度报告 / AI 助手目标拆解（scene 枚举与表结构已预留）
- 情感分数联动生命树环境（trigger_env_emo，Sprint 2.x）
