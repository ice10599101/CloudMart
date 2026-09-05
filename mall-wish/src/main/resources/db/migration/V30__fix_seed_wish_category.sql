-- 修复 QA 走查发现的种子数据悬空分类：wish_category 实际主键为 1001-1006，
-- 早期批量种子脚本写入的 category_id=1 不存在，导致广场卡片分类名为空。
-- 统一归入 1001（学业事业），与种子标题语义相符。

UPDATE wish
SET category_id = 1001
WHERE category_id = 1
  AND category_id NOT IN (SELECT id FROM wish_category);
