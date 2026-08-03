-- CloudMart 库存原子预扣减 Lua 脚本
-- KEYS[1]: 库存 Key (inventory:product:{skuId}) — String 类型，存储可用库存数量
-- ARGV[1]: 扣减数量
--
-- 返回值:
--   0 = 库存不足或 Key 不存在
--   1 = 预扣减成功

local key = KEYS[1]
local quantity = tonumber(ARGV[1])

if quantity == nil or quantity <= 0 then
    return 0
end

local stock = tonumber(redis.call('GET', key))
if stock == nil then
    return 0
end

if stock < quantity then
    return 0
end

redis.call('DECRBY', key, quantity)
return 1
