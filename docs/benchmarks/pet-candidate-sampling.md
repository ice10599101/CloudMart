# P04 基准记录：宠物候选采样（主键随机起点 vs ORDER BY RAND()）

## 脚本

`scripts/bench/pet-candidate-sampling.sql`（对本库只读执行）。
生产实现：`mall-pet/.../PetCandidateSampler.java`（B22/P04：MAX(id) → 随机起点
→ 主键范围 `ORDER BY id LIMIT n` 两段扫描，段间去重回卷表头）。

## 2026-10-03 首次记录（远程业务库 129.204.152.168:8306/mall_pet）

- 数据量：`pet` 表 `is_public = 1` 共 **3 行**（maxId≈2.1e18，雪花 ID 分布）
- 执行环境：MySQL 9.7（远程容器），采集端经公网 RTT ≈ 40ms
- 结果：pk-range 81.11 ms/op（2 次往返）vs ORDER BY RAND() 39.42 ms/op（1 次往返）

## 结论

**当前数据量下结果不具结论性**：耗时由网络往返次数主导（pk-range 2 次
RTT vs RAND 1 次 RTT），而非扫描成本；3 行数据无扫描差异可言。
不据此宣布达标，也不据此推翻采样方案——采样方案的收益预期在大表
（RAND 全表排序 O(n log n) 随行数恶化，主键采样 O(log n + n) 恒定）。

## 重跑条件（结论有效性的前提）

1. `is_public = 1` 的宠物 ≥ 10 万行；
2. 采集端与应用同机房（RTT < 1ms）或本机直连数据库执行；
3. 每档数据量（1k / 1w / 10w）各采样 50 次，报告中位数而非均值。

达标前不得引用本文件任何数字作为性能证据（E03：不能空报性能达标）。
