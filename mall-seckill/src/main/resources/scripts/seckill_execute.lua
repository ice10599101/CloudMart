-- CloudMart 秒杀库存原子扣减 Lua 脚本
-- KEYS[1]: 库存 Key (seckill:stock:{activityId}:{productId}) — String 类型，存储剩余库存
-- KEYS[2]: 用户去重集合 (seckill:users:{activityId}:{productId}) — Set 类型，存储已购买用户 ID
-- ARGV[1]: 用户 ID
-- ARGV[2]: 购买数量
--
-- 返回值:
--   0 = 库存不足（售罄）
--   1 = 扣减成功
--   2 = 用户已购买过（防重复）

-- 校验用户是否已购买
if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
    return 2
end

-- 获取当前库存
local stock = tonumber(redis.call('GET', KEYS[1]))
if stock == nil then
    -- 库存 Key 不存在，说明未初始化或已过期
    return 0
end

-- 库存不足
if stock < tonumber(ARGV[2]) then
    return 0
end

-- 原子扣减库存
redis.call('DECRBY', KEYS[1], tonumber(ARGV[2]))

-- 记录已购买用户
redis.call('SADD', KEYS[2], ARGV[1])

return 1
