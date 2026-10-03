-- P04 基准脚本：宠物候选采样（主键随机起点两段扫描）对照 ORDER BY RAND()
-- 只读执行。记录格式见 docs/benchmarks/pet-candidate-sampling.md。
-- 生产实现：mall-pet PetCandidateSampler（MAX(id) → 随机起点 → 主键范围两段）。

-- 0) 数据量确认（< 10 万行时结果不具结论性，仅作冒烟）
SELECT COUNT(*) AS public_pets, MAX(id) AS max_id FROM pet WHERE is_public = 1;

-- 1) 生产实现的查询形态（应用侧循环 20+ 次，随机起点 = RAND() * max_id）
--    每轮 2 次往返：MAX(id) + 范围查询
SELECT MAX(id) FROM pet;
SET @start := FLOOR(RAND() * (SELECT MAX(id) FROM pet));
SELECT id, user_id, level FROM pet
WHERE is_public = 1 AND user_id <> 0 AND id >= @start
ORDER BY id LIMIT 10;

-- 2) 对照：ORDER BY RAND() 全表排序（P04 改造前的写法），每轮 1 次往返
SELECT id, user_id, level FROM pet
WHERE is_public = 1 AND user_id <> 0
ORDER BY RAND() LIMIT 10;
