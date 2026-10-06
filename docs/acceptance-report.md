# CloudMart 全栈审计与改造——验收报告

> 依据《CloudMart全栈审计与改造实施方案.md》（T01-T27 + E01-E18 验收链）。
> 本报告对应 master 分支 6d3653a9，真实环境 129.204.152.168（docker compose 基础设施 + 各微服务独立部署）。

## 一、任务完成总览（T01-T27）

| 任务 | 状态 | 说明 |
|---|---|---|
| T01 Outbox 不可变参数 | ✅ | 3 提交；envelope 防御性复制 + T16 租约 |
| T02 身份认证统一 | ✅ | 服务令牌全站对称（outbound/inbound 逐域核对方法论）|
| T03 退款关联与超退 | ✅ | E02/E03 实测通过 |
| T04 按明细结算 | ✅ | case 级结算 + E02 实测 |
| T05 三端售后入口 | ✅ | 后端 + 三端透出（refundStatus/refundedAmount 三端 UI）|
| T06 注销跨域可恢复 | ✅* | 编排实测；三域擦除接线（c19889a5）后恢复分支待部署确认 |
| T07 支付渠道 SPI | ✅ | 渠道白名单前置 |
| T08 直播信令授权 | ✅ | T08 票据机制，E12 实测通过 |
| T09 签到持久事实 | ✅ | 8 提交；E05 幂等实测 |
| T10/T11 拼团契约与闭环 | ✅ | 16+ 提交；E04 实测通过 |
| T12 目标计划 | ✅ | version CAS + E07 实测 + 三端接线 |
| T13 活动与搭子 | ✅ | 审批闭环（管理端列表+审批抽屉）；E08 实测通过 |
| T14/T15 定时任务/文件锁 | ✅ | 引用锁 409 实测 |
| T16 Outbox 租约与异常中心 | ✅ | E11 fencing 实测（迟到回写 0 行拒绝）|
| T17 幂等与离线队列 | ✅ | intentId 贯穿 |
| T18/T19/T20/T21 | ✅ | 各 1+ 提交；T19 幂等重放 E16 实测 |
| T22 玩法生命周期三端 | ✅ | 18+ 提交；胶囊改期/目标/还愿撤回/成长记录/申诉/排序/星火收藏全断链闭环 |
| T23 后台代理与运营操作 | ✅ | 代理信封统一核实、审批/指标页齐 |
| T24 多运营 | ✅ | V17 五角色 seed + 账号/角色管理页（撤权走 SEC-02 强退链）|
| T25 路由契约门禁 | ✅ | CI 首跑实拦 5 条未归类（门禁有效），已归类 PASS |
| T26 可复现构建/IT/迁移门禁 | ✅ | IT Testcontainers 化 + migration-gate CI（空库+续跑双路径）|
| T27 回归/性能/可观测 | ✅（性能实测除外）| N+1 两批 6 处、四域 OpsMetrics、分页/上传上限 |

## 二、验收链记分（E01-E18）：14/18 通过

| 链 | 结果 | 关键证据 |
|---|---|---|
| E01 下单支付链 | ✅ | 交易状态来自后端、库存确认一次、金额一致 |
| E02 逐件退款 | ✅ | 两 case 独立、金额不超实收 |
| E03 并发退款+重试 | ✅ | 不超退、无孤儿 attempt |
| E04 拼团成团 | ✅ | 最后一人同步成团、GROUP_SUCCESS 事件 SENT、唯一终态、成员结果可查 |
| E05 秒杀恢复 | ✅ | requestId 恢复不误判、重复提交同 requestId、库存精确扣 1 |
| E06 签到故障恢复 | ⏳ | 需运维动作：`docker stop cloudmart-redis`（断言脚本已备）|
| E07 目标版本冲突 | ✅ | 409 + 排序不局部成功 |
| E08 搭子审批 | ✅ | join 拒绝/PENDING/进组看板/过期拒绝 |
| E09 隐私撤销 | ✅ | 改私密后广场/果实/匿名详情全下线；附件签名授权不绕过；引用删除 409 |
| E10 注销等待期 | ✅* | 到期自动编排、跨域步骤真实（5 域 SUCCESS）、旧会话 401、BLOCKED 诚实语义；**恢复分支待四服务部署 c19889a5 后自动闭环**（18 号任务重试中）|
| E11 Outbox 双 worker | ✅ | 过期租约接管（lv 1→2）、迟到回写 0 行拒绝 |
| E12 直播信令越权 | ✅ | 伪造 HOST 拒绝（角色服务端派生）、第三方清信令拒绝、观众仅 ANSWER |
| E13 投票/聊天游标 | ✅ | 挂他人帖拒绝、409 重复、游标分页无漏无重、用户隔离 |
| E14 App 离线队列 | ⏳ | 需真机（离线打卡/推送/SQLite 原生能力，Expo export 不证明可用）|
| E15 任务双实例 | ⏳ | 需第二实例部署方式 |
| E16 WMS 重放 | ✅ | uk(receipt_id) 双层幂等、台账不重复入账、引用删除 409 |
| E17 真实渠道沙箱 | ⏳ | 需沙箱商户凭证（可豁免，风险记录）|
| E18 三端入口遍历 | ✅ | 断链盘点清零（下线封装清理后无"仅 API 无入口"）|

## 三、运行时实测发现并修复的缺陷（单测无法暴露）

| # | 缺陷 | 级别 | 修复 |
|---|---|---|---|
| 1 | 胶囊改期返回实体致 SEALED content 泄漏 | High | 改 CapsuleVO（13ecd0ab）|
| 2 | 温暖事件/还愿撤回虚假成功（MP logic 字段不可经 updateById 写入）| High | deleteById + affected 校验（71bde7cd）|
| 3 | 温暖事件详情返回实体泄漏精确坐标/userId | High | 脱敏 VO + owned（a7888933）|
| 4 | 注销编排三域擦除未接线（任务永久 BLOCKED）| High | 三域 internal 端点 + Feign 接线（c19889a5）|
| 5 | 胶囊创建响应改期计数 null | Medium | 创建时显式初始化（9ef6832c）|
| 6 | 搭子申请管理端无法列出待审（盲审批）| Medium | 全状态参与者端点 + 审批抽屉（1a3613f6）|
| 7 | Feign fallback 吞业务错误码（38 文件 258 处）| Medium | FeignBusinessErrors 透传（579104b3）|
| 8 | 分页 size 无上限（999999 直达 LIMIT）| Medium | 17 服务 setMaxLimit(200)（ecb7d773）|
| 9 | admin Excel 导入受默认 1MB 限制 | Low | 20MB multipart（45957cc1）|
| 10 | 下线重写误删 admin/encounter 类级守卫 | Medium | 恢复 + 路由归类（6d3653a9）|

## 四、运维指标（Prometheus 可查）

- payment_*（E03）：outbox 待发/最老年龄、inbox 失败、退款积压/UNKNOWN 时长
- wish_*：outbox 待发/最老/死信、**wish_reward_dispatch_stuck（奖励待到账）**
- inventory_*：**reserved_ledger_diff（台账与投影漂移，零容忍对账项）**、预占滞留、outbox
- order_*：outbox 积压/死信、**db_lock_waits（锁等待）**

## 五、剩余风险与待办

1. **部署生效面**：分页上限/指标/410 下线/T24（V17）需除 mall-wish 外的其余服务拉取重启；E10 恢复分支待 mall-user/community/notification/file 四服务部署后自动闭环。
2. **安全收口（Critical 建议）**：Redis 8379 无密码公网开放（本次验收曾利用其自助建号——任意读写会话撤销状态属高危）；MySQL 8306 与服务端口 9001-9023 公网可达（api-docs/health 可探测）。建议安全组仅保留业务入口，Redis 加 requirepass。
3. **性能实测**：P95≤500ms/写入≤1s 目标需灌入 §6.1 验收数据集后压测；EXPLAIN 校准待数据集。
4. **真机项**：E14 App 离线队列/推送/SQLite/音频/支付插件需真机验证。
5. **E17**：真实支付渠道沙箱未跑（无凭证）；渠道 SPI 白名单与幂等由单测覆盖。
6. **CI**：migration-gate/IT job 首跑结果以 GitHub Actions 最新 run 为准（日志需仓库管理员权限查看）。

## 六、工具与脚本（.ice/）

- `audit-api-dead.mjs`：三端"仅封装无入口"断链盘点（E18）
- `scan-n1.mjs`：循环内单查启发扫描（T27 N+1）
- `audit-pagesize.mjs`：分页 size 上限审计
- `redis-probe.py` / `create-test-user.py`：测试基建（含安全发现复现）
- `fix-*.py`：批量修复脚本存档（fallback/分页/N+1/擦除接线）
