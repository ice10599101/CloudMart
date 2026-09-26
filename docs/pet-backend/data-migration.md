# 数据迁移报告（§7 / M5）

## 1. 迁移清单

| 模块 | 版本 | 内容 | 远程真实 MySQL 9.7 验证 |
| --- | --- | --- | --- |
| mall-pet | V7 | pet_operation / pet_outbox_event | PASS |
| mall-pet | V8 | 迁移冲突报告表；到期 IN_PROGRESS 先结算；多活动冲突保留最早（其余 EXPIRED+审计）；活动唯一索引改"每用户一条进行中" | PASS |
| mall-pet | V9 | pet 四属性小数余量列 | PASS |
| mall-pet | V10 | pet_companion_session / pet_companion_daily | PASS |
| mall-pet | V11 | pet_daily_quota（用户日额度，原子条件更新） | PASS |
| mall-pet | V12 | pet_battle.seed 改有符号 BIGINT（预检极值入报告表） | PASS |
| mall-pet | V13 | pet_activity.snapshot 规则快照 | PASS |
| mall-pet | V14 | pet_career_stint + 存量回填（INSERT IGNORE） | PASS |
| mall-pet | V15 | pet_room_like / pet_user_block / pet_report；pet_relation 规范化无向列 + 去重 ACTIVE + 唯一键；daily_quest 枚举加 CANCELLED/EXPIRED；event_mode 列；pet_room_item.archived_at + 重复摆放归档 | PASS（修复两处 SQL 缺陷后） |
| mall-pet | V16 | pet.base_appearance；pet_inventory 同槽/皮肤穿戴函数唯一索引 | PASS |
| mall-pet | V17 | N01–N07 全部新表（9 张）+ pet 记忆开关两列 | PASS |
| mall-wish | V38 | wish_pet_operation 幂等表 | PASS |
| mall-wish | V39 | fish_log.request_id + 唯一键 | PASS |
| mall-notification | V5 | notifications.event_id + 唯一键 | PASS |

**验证方式**：在远程 MySQL 9.7（129.204.152.168:8306）建一次性验证库，按数字版本序完整执行三条迁移链（mall-pet V1–V17 建表 50 张、mall-wish V1–V39 建表 61 张、mall-notification V1–V5），全部通过后验证库已删除。未使用 H2 替代。

## 2. 过程中发现并修复的 SQL 缺陷

1. **V15 反引号缺失**：`AFTER pet_a_id';` 丢失闭合反引号 → 未闭合标识符吞掉语句边界 → MySQL 1064（用户环境实测触发）。已修复，并把"一条 ALTER 内第二列 AFTER 引用同语句新增列"拆为两条独立 ALTER。
2. **V15 room_id 引用错误**：`pet_room_item` 实际按 `pet_id` 建模（无 room_id 列），重复摆放冲突检测改为 `GROUP BY pet_id, furniture_code`。
3. V17 记忆开关两列同语句跨列 AFTER —— 同步拆分为两条。

## 3. 历史情形处理（§7.2 落点）

| 历史情形 | 处理 |
| --- | --- |
| 同用户多条不同类型 IN_PROGRESS | V8：到期先结算；未到期保留最早一条、其余 EXPIRED（result 写迁移标记），用户清单落 `pet_migration_conflict`（resolved=0 待人工核对），索引建立前强制清零 |
| 已开始活动无奖励快照 | V13 加 snapshot 列；存量无快照活动结算时回退当前配置并在日志 WARN（不伪称还原历史配置） |
| 两只宠物共享旧会话 | 聊天会话表 pet_id 已在 V4 模型存在；本轮 B03 领取/结算均按 activity.petId 归属 |
| 一件家具多处摆放 | V15：保留最早合法实例，其余 archived_at 归档 + 冲突报告；舒适度按现存唯一实例重算（refreshComfort 封顶 100） |
| 重复/超额宠物关系 | V15：规范化 (pet_a_id,pet_b_id) 后，重复 ACTIVE 保留最早，其余 CANCELLED + 冲突报告（不物理删除） |
| 皮肤标记与背包不一致 | 手动改外观同步卸皮肤标记（B12 代码）；历史不一致以当前外观为准，缺原始自定义外观时按物种默认（base_appearance NULL 语义） |
| battle.seed 范围 | V12：预检极值入报告表；MODIFY 为有符号 BIGINT |
| 任务停用卡宝箱 | CANCELLED 显式状态 + 宝箱门禁排除 CANCELLED（B15）；存量由 ensureToday 触发取消 |
| 星光重复/遗漏嫌疑 | wish_pet_operation 与 pet_operation 双侧流水可对账；修复必须走补偿操作（原单:refund），禁止直接改余额 |

## 4. 业务日切换（§7.3）

本轮采用任务书允许的**暂缓方案**：数据存储与计算保持统一 UTC（全部列已是 UTC 口径），业务日归属通过 `PetClock.businessZone=Asia/Shanghai` 计算（pet_companion_daily / pet_daily_quest.quest_date / pet_daily_quota.business_date 均按业务日落行）。**旧 UTC 日行不重解释**；切换日重叠区额度处理与全量切库为未完成项，已记录在 implementation-status.md。

## 5. 回滚（§7.4）

- 功能开关关闭新玩法（N04–N07 控制器可独立下线）；已提交的操作记录保留只读查询与补偿能力。
- 失败迁移重启自愈：mall-pet 已加入 `FlywayRepairConfig`（repair→migrate，与 mall-wish 同构）。
- 禁止自动删除新表/抹除流水的破坏性回滚；备份恢复走既有发布流程。
