# 测试报告（T01–T46 状态）

## 1. 已执行（自动化单元测试，本轮实际运行）

运行命令：
```
mvn -pl mall-pet test            # 102 tests, 0 failures, 0 errors   PASS
mvn -pl mall-wish test           # 783 tests, 0 failures, 0 errors  PASS
mvn -pl mall-notification test   # 47 tests, 0 failures, 0 errors   PASS
mvn -pl mall-pet,mall-admin -am package -DskipTests   # 打包       PASS
```
环境：JDK 26.0.1 / Maven 3.9.15 / Windows；数据库层为真实 MySQL 9.7 迁移链验证（见 data-migration.md），单测为 Mockito 层。

新增/覆盖的关键用例与本轮任务对应（节选）：
- B05：会话基准/伪造秒数不加速/seq 幂等/失效不补计/停止结算窗口/概览今日值（PetIntimacyServiceImplTest 重写 7 用例）
- B04：衰减等价与余量（PetStateServiceTest，含 CAS 未命中重读重算路径）
- B01：扣款/发薪走统一操作记录的打桩断言（Activity/Shop/Evolution/DailyQuest/Event/Bottle 六处 verify operationService.execute*）
- B11：快照冻结成功率+种子→结算确定；FAILED 重试不重抽（PetBottleFishingServiceImplTest）
- B15：停用任务 CANCELLED 展示、宝箱门禁排除（编译层验证 + 存量用例回归）
- B16：WINDOW 模式结束超 24h 领取拒绝（claimEndedEventRejected 更新）
- B06：喂食 DB 额度、休息定时语义、settleRest 恢复+成就（PetInteractionServiceImplTest 更新/新增）
- T16 字符串 ID：JacksonConfig Long→String 与 RFC3339 UTC 序列化（Java 契约层）

## 2. NOT RUN（需多服务/并发/故障注入环境，未执行）

| 编号 | 场景 | 状态 |
| --- | --- | --- |
| T01–T04 | 同请求并发 20 次购买/钱包超时重启/补偿重复/同键不同内容 | NOT RUN（需真实并发 + 双服务 Testcontainers） |
| T05–T08 | 并发打工读书互斥/影响行数 0/切宠归属/领养并发 | NOT RUN（数据库并发） |
| T09–T14 | 衰减等价高频读/陪伴跨日多端/互动额度绕过 | NOT RUN（仅单测等价路径） |
| T15/T17 | Redis 故障发奖/时区午夜边界 | NOT RUN |
| T18–T24 | 对战双方收益/负 seed/过期挑战/捞瓶远程幂等 | NOT RUN（远程联调） |
| T25–T38 | B12–B22 数据库并发与权限矩阵 | NOT RUN |
| T39–T45 | N01–N07 端到端 | NOT RUN（客户端未接入，后端能力已具备） |
| T46 | 旧 API 兼容全量回归 | NOT RUN |

## 3. 结论

- 单元层：PASS（932 用例全绿）。
- 数据库层：迁移链 PASS（真实 MySQL 9.7）。
- 并发/故障注入/跨服务契约/端到端：**NOT RUN**——T01–T46 不能标记为已通过；后续需按任务书 §8.1 搭建 Testcontainers(MySQL/Redis) 与网关联调环境执行。
